#!/usr/bin/env python3
"""
Extract a StarCraft GRP sprite archive into one PNG per frame, coloured with a PCX palette.

Used to import `import/cmdicons.grp` - StarCraft's 390 command-card icons - into
`import/command_icons/`, so the mod's command cards (`building/ProductionKind`) have real
reference icons to work from instead of the raw archive.

Nothing about either format is self-describing, so the details worth keeping are:

GRP
  6-byte header: uint16 frame count, uint16 max width, uint16 max height. Then one 8-byte
  record per frame: uint8 x, uint8 y, uint8 w, uint8 h, uint32 absolute data offset. Frames are
  tight-cropped and carry an x/y offset placing them on the shared max-width x max-height canvas
  (mostly zero, but non-zero on 35 of cmdicons' frames, so it cannot be ignored without
  misaligning them). Each frame's data begins with `h` uint16 row offsets *relative to that
  frame's data offset*, each pointing at an RLE row: a byte with 0x80 set skips (0x7F & b)
  transparent pixels, 0x40 set repeats the next byte (0x3F & b) times, and anything else is a
  literal run of b bytes.

  The sanity check that the header is being read correctly: the frame table ends exactly where
  frame 0's pixel data starts (6 + 8*count == frames[0].offset).

PCX palette (`ticon.pcx`)
  Not a plain palette - it is a 96x1 8-bit *image* whose pixels are themselves indices into the
  file's own trailing 256-colour VGA palette (last 769 bytes, marker byte 0x0C then 256 RGB
  triples). Resolving that indirection yields 96 colours arranged as six 16-entry tint ramps
  (gold, warm grey, white, dark gold, blue, silver); within each ramp slot 0 is black and the
  rest run brightest -> darkest.

  cmdicons indexes directly into all 96 entries, NOT into a single 16-colour ramp. The giveaway
  is index 16 - 2974 pixels across the archive, far too many to be junk, and entry 16 is pure
  black, the icons' outline colour sitting in ramp 1's slot 0. So the art is ramp 0 (gold) plus
  that black, which is why every index lands in 1..18. A single pixel in the whole archive
  carries index 255, past the end of a 96-entry palette; it is dropped as transparent rather
  than clamped to a colour, and reported.

  `import/tunit.pcx` is the sibling table: 128 entries as eight 16-ramps in team colours. That is
  the *unit* tint palette - pass it with --palette to recolour these icons per faction.

Frame index == StarCraft's internal unit ID for the whole unit/building range, which is how
ICON_NAMES below is derived. Confirmed structurally rather than assumed: the frames at SC's
unused IDs are exactly the placeholder art (#153, #158 and #161 read "BLANK"; #145, the unused
slot between Spore Colony and Sunken Colony, is the radiation symbol). Every name in the table
was then checked by eye against the rendered icon.

Usage:
    python tools/grp_extract.py
    python tools/grp_extract.py --grp import/cmdicons.grp --palette import/ticon.pcx \
                                --out import/command_icons
    python tools/grp_extract.py --sheet contact.png     # also write a contact sheet
"""

import argparse
import os
import struct
import sys

from PIL import Image

# Frame index -> name, for the icons this mod actually uses. Frame index is the StarCraft unit
# ID, so extend this by looking an ID up in any SC unit table; every entry here was verified
# against the rendered art.
ICON_NAMES = {
    # Protoss
    64: "probe",            65: "zealot",           66: "dragoon",
    61: "dark_templar",     70: "scout",            68: "archon",
    63: "dark_archon",      154: "nexus",           156: "pylon",
    160: "gateway",         162: "photon_cannon",
    # Zerg units
    35: "larva",            37: "zergling",         38: "hydralisk",
    39: "ultralisk",        41: "drone",            42: "overlord",
    43: "mutalisk",         44: "guardian",         47: "scourge",
    50: "infested_terran",  103: "lurker",
    # Zerg structures
    131: "hatchery",        132: "lair",            133: "hive",
    137: "greater_spire",   141: "spire",           142: "spawning_pool",
    144: "spore_colony",    146: "sunken_colony",   149: "extractor",
    # Terran
    106: "command_center",   111: "barracks",        113: "factory",
    114: "starport",         124: "missile_turret",
    # Protoss structures beyond the four above
    167: "stargate",
}


def read_pcx_palette(path):
    """Resolve a PCX palette strip to a flat list of (r, g, b), one per pixel of its single row."""
    d = open(path, "rb").read()
    if d[0] != 0x0A:
        raise ValueError("%s: not a PCX file" % path)
    if d[3] != 8 or d[65] != 1:
        raise ValueError("%s: expected 8bpp single-plane, got %dbpp x %d planes"
                         % (path, d[3], d[65]))

    xmin, ymin, xmax, ymax = struct.unpack_from("<HHHH", d, 4)
    w, h = xmax - xmin + 1, ymax - ymin + 1
    bpl = struct.unpack_from("<H", d, 66)[0]

    if d[-769] != 0x0C:
        raise ValueError("%s: no trailing VGA palette marker" % path)
    base = len(d) - 768
    vga = [tuple(d[base + i * 3: base + i * 3 + 3]) for i in range(256)]

    p, end = 128, len(d) - 769
    row = []
    while len(row) < bpl * h:
        b = d[p]
        p += 1
        if b & 0xC0 == 0xC0:
            row.extend([d[p]] * (b & 0x3F))
            p += 1
        else:
            row.append(b)
    if p != end:
        raise ValueError("%s: %d bytes of unconsumed image data" % (path, end - p))

    return [vga[v] for v in row[:w]]


def read_grp(path):
    """Return (data, frame_count, canvas_w, canvas_h, [(x, y, w, h, offset)]) for a GRP."""
    d = open(path, "rb").read()
    count, cw, ch = struct.unpack_from("<HHH", d, 0)
    frames = [struct.unpack_from("<BBBBI", d, 6 + i * 8) for i in range(count)]
    if frames and frames[0][4] != 6 + 8 * count:
        raise ValueError("%s: frame table ends at %d but frame 0's data starts at %d"
                         % (path, 6 + 8 * count, frames[0][4]))
    return d, count, cw, ch, frames


def decode_frame(d, frame):
    """RLE-decode one frame into rows of palette index, or None for transparent."""
    _, _, w, h, off = frame
    out = [[None] * w for _ in range(h)]
    row_offsets = struct.unpack_from("<%dH" % h, d, off)
    for r in range(h):
        p, c = off + row_offsets[r], 0
        while c < w:
            b = d[p]
            p += 1
            if b & 0x80:
                c += b & 0x7F
            elif b & 0x40:
                n, v = b & 0x3F, d[p]
                p += 1
                for _ in range(n):
                    if c < w:
                        out[r][c] = v
                        c += 1
            else:
                for _ in range(b):
                    if c < w:
                        out[r][c] = d[p]
                        c += 1
                    p += 1
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__.strip().split("\n")[0])
    ap.add_argument("--grp", default="import/cmdicons.grp")
    ap.add_argument("--palette", default="import/ticon.pcx")
    ap.add_argument("--out", default="import/command_icons")
    ap.add_argument("--sheet", help="also write a contact sheet to this path")
    args = ap.parse_args()

    palette = read_pcx_palette(args.palette)
    d, count, cw, ch, frames = read_grp(args.grp)
    os.makedirs(args.out, exist_ok=True)

    dropped = 0
    for i, f in enumerate(frames):
        x, y = f[0], f[1]
        img = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
        px = img.load()
        for ry, row in enumerate(decode_frame(d, f)):
            for rx, v in enumerate(row):
                if v is None:
                    continue
                if v >= len(palette):
                    dropped += 1
                    continue
                r, g, b = palette[v]
                px[x + rx, y + ry] = (r, g, b, 255)
        img.save(os.path.join(args.out, "icon_%03d.png" % i))
        if i in ICON_NAMES:
            img.save(os.path.join(args.out, "%s.png" % ICON_NAMES[i]))

    with open(os.path.join(args.out, "manifest.tsv"), "w") as fh:
        fh.write("# %s + %s\n# frame\tname\n" % (args.grp, args.palette))
        for i in range(count):
            fh.write("%d\t%s\n" % (i, ICON_NAMES.get(i, "")))

    print("%d frames -> %s (%dx%d canvas, %d-colour palette, %d named)"
          % (count, args.out, cw, ch, len(palette), len(ICON_NAMES)))
    if dropped:
        print("  %d pixel(s) dropped as out-of-palette" % dropped)

    if args.sheet:
        cols = 26
        rows = (count + cols - 1) // cols
        sheet = Image.new("RGB", (cols * cw, rows * ch), (40, 40, 48))
        for i in range(count):
            im = Image.open(os.path.join(args.out, "icon_%03d.png" % i))
            sheet.paste(im, ((i % cols) * cw, (i // cols) * ch), im)
        sheet.resize((cols * cw * 2, rows * ch * 2), Image.NEAREST).save(args.sheet)
        print("  contact sheet -> %s (%d per row)" % (args.sheet, cols))

    return 0


if __name__ == "__main__":
    sys.exit(main())
