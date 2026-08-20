#!/usr/bin/env python3
"""
Generate the Dark Templar's entity textures.

This is the successor to the retired `gen_<unit>_texture.py` scripts, and differs from them in the
one way that matters: those predate per-cube UV islands and painted flat colour *zones*, with every
cube of a material pointing at the same rectangle. Since `blockbench_export.py` started shelf-packing
each cube into its own island, that approach can't work — so this reads the packed layout back out of
`build/model-export/dark_templar.json` and paints each cube's six faces individually, shaded by face
direction.

Output is a starting point, not a finished skin: the point is that `tools/blockbench/dark_templar.bbmodel`
opens onto a correct layout with real artwork on it, so painting is a paint job rather than a setup
job. See docs/texturing.md.

Two files are written:
    dark_templar.png       the skin — near-black hooded cloth, dark bronze armour, a gold mask
    dark_templar_glow.png  the emissive pass: ONLY the warp blade and the eyes, everything else
                           transparent, since UnitGlowLayer re-submits the whole model

The palette follows the reference art: the unit is almost entirely shadow, with exactly two bright
notes — the gold face plate and the pale blue blade. That restraint matters more here than on any
other unit, because a Dark Templar is permanently cloaked and therefore never rendered at more than
15% alpha; the emissive pass is most of what a player actually sees.

Usage:
    python tools/gen_dark_templar_texture.py
Run it AFTER `./gradlew test` has refreshed the geometry dump, or the islands it paints will be the
ones from the previous layout.
"""
import json
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
DUMP = ROOT / "build" / "model-export" / "dark_templar.json"
TEX_DIR = ROOT / "src/main/resources/assets/asteriskcraft/textures/entity"

CLEAR = (0, 0, 0, 0)

# --- palette ------------------------------------------------------------------------------------
# Keyed by leaf part name. Everything is desaturated and dark on purpose; the two accents carry the
# whole read.
CLOTH_HOOD = (26, 24, 30)        # hood and cowl: the darkest cloth on the model
CLOTH_CAPE = (20, 18, 24)        # cloak panels, darker still so they separate from the body
ARMOUR = (62, 50, 40)            # dark bronze plate: torso, arms, legs
ARMOUR_LIT = (78, 64, 50)        # pauldrons and knees — catch the light, so a shade up
SINEW = (44, 38, 40)             # nerve cords trailing off the hood
GOLD = (176, 140, 58)            # the face plate, the one warm note on the skin
EYE = (236, 214, 128)            # lit apertures in the mask
BLADE = (120, 170, 200)          # warp blade, base pass (the glow pass is what makes it burn)

# Emissive pass — only these two ever appear in the glow texture.
BLADE_GLOW = (188, 232, 255)
EYE_GLOW = (255, 246, 200)

PALETTE = {
    "head": CLOTH_HOOD, "cowl": CLOTH_HOOD,
    "face": GOLD, "eyeL": EYE, "eyeR": EYE,
    "tendrilL1": SINEW, "tendrilR1": SINEW, "tendrilL2": SINEW, "tendrilR2": SINEW,
    "body": ARMOUR, "neck": SINEW, "chestPlate": ARMOUR, "skirt": ARMOUR,
    "capeUpper": CLOTH_CAPE, "capeLower": CLOTH_CAPE,
    "shoulderL": ARMOUR_LIT, "shoulderR": ARMOUR_LIT,
    "armL": ARMOUR, "foreArmL": ARMOUR_LIT, "armR": ARMOUR, "foreArmR": ARMOUR_LIT,
    "bladeR": BLADE,
    "legL": ARMOUR, "legR": ARMOUR, "kneeL": ARMOUR_LIT, "kneeR": ARMOUR_LIT,
    "shinL": ARMOUR, "shinR": ARMOUR, "footL": ARMOUR_LIT, "footR": ARMOUR_LIT,
}

# Which parts appear in the emissive pass, and in what colour.
GLOWING = {"bladeR": BLADE_GLOW, "eyeL": EYE_GLOW, "eyeR": EYE_GLOW}

# Per-face brightness. Box UV order is top, bottom, right(-x), front(-z), left(+x), back(+z).
# Lighting reads best in Minecraft when the top is clearly lit and the underside is nearly black.
FACE_SHADE = {"top": 1.30, "bottom": 0.55, "right": 0.86, "front": 1.06, "left": 0.86, "back": 0.78}


def shade(color, factor):
    return tuple(min(255, max(0, int(round(c * factor)))) for c in color)


def jitter(x, y, seed):
    """Deterministic +-3 per-texel noise, so flat plate reads as worn metal rather than plastic."""
    h = (x * 73856093) ^ (y * 19349663) ^ (seed * 83492791)
    h = (h ^ (h >> 13)) & 0xFFFF
    return (h % 7) - 3


def faces(u, v, w, h, d):
    """The six box-UV sub-rectangles of one cube, in Minecraft's standard layout."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }


def paint_face(px, rect, color, factor, seed, tex_w, tex_h, gradient=False):
    x0, y0, fw, fh = rect
    base = shade(color, factor)
    for dy in range(fh):
        row = base
        if gradient and fh > 1:
            # Blade only: brightest at the tip, so the edge reads as energy rather than as a bar.
            row = shade(color, factor * (0.72 + 0.5 * (dy / (fh - 1))))
        for dx in range(fw):
            x, y = x0 + dx, y0 + dy
            if not (0 <= x < tex_w and 0 <= y < tex_h):
                continue
            n = jitter(x, y, seed)
            # A darker rim around every face gives each cube its own edge at Minecraft's scale,
            # where an unbroken flat colour makes neighbouring cubes melt into one another.
            edge = 0.74 if (dx == 0 or dy == 0 or dx == fw - 1 or dy == fh - 1) else 1.0
            px[x, y] = (
                min(255, max(0, int(row[0] * edge) + n)),
                min(255, max(0, int(row[1] * edge) + n)),
                min(255, max(0, int(row[2] * edge) + n)),
                255,
            )


def main():
    if not DUMP.exists():
        raise SystemExit(f"Missing {DUMP.relative_to(ROOT)} — run `./gradlew test` first.")
    model = json.loads(DUMP.read_text(encoding="utf-8"))
    tex_w, tex_h = int(model["texWidth"]), int(model["texHeight"])

    skin = Image.new("RGBA", (tex_w, tex_h), CLEAR)
    glow = Image.new("RGBA", (tex_w, tex_h), CLEAR)
    spx, gpx = skin.load(), glow.load()

    painted = 0
    unknown = []
    for part in model["parts"]:
        name = part["path"].split("/")[-1]
        color = PALETTE.get(name)
        if color is None:
            unknown.append(name)
            continue
        for index, cube in enumerate(part["cubes"]):
            w, h, d = (int(math.ceil(v)) for v in cube["dimensions"])
            u, v = int(cube["texOffs"][0]), int(cube["texOffs"][1])
            seed = abs(hash((name, index))) % 9973
            is_blade = name == "bladeR"
            for face, rect in faces(u, v, w, h, d).items():
                paint_face(spx, rect, color, FACE_SHADE[face], seed, tex_w, tex_h,
                           gradient=is_blade)
                if name in GLOWING:
                    # The emissive pass is flatter on purpose: a glow with strong directional
                    # shading stops looking like it emits its own light.
                    paint_face(gpx, rect, GLOWING[name],
                               0.85 + 0.15 * FACE_SHADE[face], seed + 1, tex_w, tex_h,
                               gradient=is_blade)
            painted += 1

    if unknown:
        raise SystemExit(f"No palette entry for: {', '.join(sorted(set(unknown)))}")

    TEX_DIR.mkdir(parents=True, exist_ok=True)
    skin.save(TEX_DIR / "dark_templar.png")
    glow.save(TEX_DIR / "dark_templar_glow.png")
    print(f"painted {painted} cubes onto {tex_w}x{tex_h}")
    print(f"wrote {TEX_DIR / 'dark_templar.png'}")
    print(f"wrote {TEX_DIR / 'dark_templar_glow.png'}")


if __name__ == "__main__":
    main()
