#!/usr/bin/env python3
"""
Generate the Dark Templar's two-dimensional art: the Gateway production icon and the pair of spawn
eggs. Separate from `gen_dark_templar_texture.py`, which paints the 3D model's skin.

Both outputs are derived from the Zealot's, because both are part of a *set* and inventing new
geometry for either would make the odd one out:

  * The GUI icons are unit portraits inside a blue bevelled frame. The frame is lifted from
    `gui/icons/zealot.png` pixel for pixel and only the interior is repainted, so the Gateway's four
    buttons keep an identical border. The portrait itself is a bold silhouette rather than a detailed
    render, which is the right call regardless of what could be drawn here: `ProductionScreen` scales
    it into a 20px button, where detail is invisible and shape is everything.

  * The spawn eggs share one egg shape and one speckle mask across the whole mod. What varies is the
    base colour (per unit) and the spot colour (per faction: blue for an ally, dark red for an
    enemy). So this recolours the Zealot's egg rather than drawing a new one, remapping only the
    base and leaving every faction spot exactly where it was.

Usage:
    python tools/gen_dark_templar_items.py
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
TEX = ROOT / "src/main/resources/assets/asteriskcraft/textures"

# The frame inset, measured off the Zealot icon: the bevel runs ~14px deep on every side.
FRAME = 14

# Faction speckle colours, shared by every spawn egg in the mod. Left untouched by the recolour.
SPOTS = {
    (30, 75, 186, 255), (26, 63, 157, 255),      # ally  (Protoss blue)
    (124, 14, 43, 255), (95, 11, 33, 255),       # enemy (Zerg red)
}

# The Dark Templar's egg base: a cold violet-black, so it sits apart from the Zealot's gold on the
# creative tab while still reading as the same egg.
EGG_DARK = (24, 20, 34)
EGG_LIGHT = (104, 92, 132)


def luminance(px):
    return (px[0] * 299 + px[1] * 587 + px[2] * 114) / 255000.0


def recolour_egg(source: Path, out: Path):
    """Remap the egg's base ramp onto the Templar's, preserving shading, outline and speckles."""
    im = Image.open(source).convert("RGBA")
    px = im.load()
    w, h = im.size
    for y in range(h):
        for x in range(w):
            c = px[x, y]
            if c[3] == 0 or c in SPOTS:
                continue  # transparent, or a faction spot that must survive verbatim
            # Map the original's brightness onto the new ramp, so highlights stay highlights and the
            # dark outline stays an outline — the egg keeps its shape, only its hue changes.
            t = luminance(c)
            px[x, y] = (
                int(EGG_DARK[0] + (EGG_LIGHT[0] - EGG_DARK[0]) * t),
                int(EGG_DARK[1] + (EGG_LIGHT[1] - EGG_DARK[1]) * t),
                int(EGG_DARK[2] + (EGG_LIGHT[2] - EGG_DARK[2]) * t),
                c[3],
            )
    im.save(out)
    print(f"wrote {out}")


def build_icon(frame_source: Path, out: Path):
    """Keep the Zealot frame, repaint the interior as a hooded bust with a lit blade."""
    im = Image.open(frame_source).convert("RGBA")
    w, h = im.size
    x0, y0, x1, y1 = FRAME, FRAME, w - FRAME, h - FRAME
    iw, ih = x1 - x0, y1 - y0

    # Interior painted on its own layer, then composited, so the blur below can't bleed onto the frame.
    inner = Image.new("RGBA", (iw, ih), (0, 0, 0, 0))
    d = ImageDraw.Draw(inner)

    # Backdrop: a cold vertical gradient, darkest at the bottom, so the silhouette has something to
    # sit against without competing with it.
    for y in range(ih):
        t = y / max(1, ih - 1)
        d.line([(0, y), (iw, y)], fill=(int(58 - 34 * t), int(64 - 38 * t), int(92 - 54 * t), 255))

    cx = iw // 2
    # Shoulders: a broad cloaked mass rising from the bottom edge.
    d.polygon([(cx - iw * 0.44, ih), (cx - iw * 0.30, ih * 0.60),
               (cx + iw * 0.30, ih * 0.60), (cx + iw * 0.44, ih)],
              fill=(16, 14, 20, 255))
    # Hood: a tall cowl, wider at the jaw than the crown.
    d.polygon([(cx - iw * 0.20, ih * 0.66), (cx - iw * 0.23, ih * 0.34),
               (cx - iw * 0.10, ih * 0.14), (cx + iw * 0.10, ih * 0.14),
               (cx + iw * 0.23, ih * 0.34), (cx + iw * 0.20, ih * 0.66)],
              fill=(24, 21, 30, 255))
    # The mask, set back inside the hood's mouth — the one warm note, exactly as on the model.
    d.polygon([(cx - iw * 0.11, ih * 0.52), (cx - iw * 0.12, ih * 0.33),
               (cx + iw * 0.12, ih * 0.33), (cx + iw * 0.11, ih * 0.52)],
              fill=(120, 96, 40, 255))
    # Nerve cords trailing back off the hood, the model's strongest silhouette cue after the blade.
    for sign in (-1, 1):
        d.line([(cx + sign * iw * 0.19, ih * 0.30), (cx + sign * iw * 0.46, ih * 0.52)],
               fill=(38, 34, 38, 255), width=max(2, iw // 22))
        d.line([(cx + sign * iw * 0.20, ih * 0.42), (cx + sign * iw * 0.44, ih * 0.68)],
               fill=(32, 29, 34, 255), width=max(2, iw // 26))

    # Glowing parts go on their own layer so they can be bloomed before compositing.
    lit = Image.new("RGBA", (iw, ih), (0, 0, 0, 0))
    ld = ImageDraw.Draw(lit)
    eye = max(2, iw // 24)
    for sign in (-1, 1):
        ld.ellipse([cx + sign * iw * 0.06 - eye, ih * 0.40 - eye * 0.6,
                    cx + sign * iw * 0.06 + eye, ih * 0.40 + eye * 0.6],
                   fill=(255, 246, 200, 255))
    # The warp blade, swept across the portrait — the single read that says "not a Zealot".
    ld.line([(cx + iw * 0.10, ih * 0.94), (cx + iw * 0.60, ih * 0.16)],
            fill=(188, 232, 255, 255), width=max(3, iw // 16))

    bloom = lit.filter(ImageFilter.GaussianBlur(radius=max(2, iw // 20)))
    inner.alpha_composite(bloom)
    inner.alpha_composite(lit)

    im.paste(inner, (x0, y0))
    im.save(out)
    print(f"wrote {out} ({w}x{h})")


def main():
    icons = TEX / "gui/icons"
    items = TEX / "item"
    build_icon(icons / "zealot.png", icons / "dark_templar.png")
    recolour_egg(items / "zealot_spawn_egg_ally.png", items / "dark_templar_spawn_egg_ally.png")
    recolour_egg(items / "zealot_spawn_egg_enemy.png", items / "dark_templar_spawn_egg_enemy.png")


if __name__ == "__main__":
    main()
