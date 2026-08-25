#!/usr/bin/env python3
"""
Generate the Firebat's entity textures.

Same approach as `gen_marine_texture.py`: read the packed per-cube UV islands back out of
`build/model-export/firebat.json` and paint each cube's six faces individually, shaded by face
direction. Output is a starting point, not a finished skin — the point is that
`tools/blockbench/firebat.bbmodel` opens onto a correct layout with real artwork on it, so painting
is a paint job rather than a setup job. See docs/texturing.md.

Two files are written:
    firebat.png       the skin — red heavy plate over a dark undersuit, gunmetal flamethrower, a
                      brass-banded fuel tank. NOT the face: the head is vanilla's own pillager head
                      drawn on vanilla's own texture by client/terran/FirebatHeadLayer, so nothing
                      here paints one
    firebat_glow.png  the emissive pass: ONLY the two pilot lights at the muzzles; everything else
                      transparent, since UnitGlowLayer re-submits the whole model

The palette is the point of divergence from the Marine. That unit is blue-steel and reads as
infantry; a Firebat is the same silhouette in **red**, which is what the design asks for and also
what a suit built to stand two blocks from what it is burning should look like. The red is kept
dark and slightly desaturated so it stays armour rather than becoming a costume, with the pauldrons
and helmetless head giving it the heavier read.

Usage:
    python tools/gen_firebat_texture.py
Run it AFTER `./gradlew test` has refreshed the geometry dump, or the islands it paints will be the
ones from the previous layout.
"""
import json
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
DUMP = ROOT / "build" / "model-export" / "firebat.json"
TEX_DIR = ROOT / "src/main/resources/assets/asteriskcraft/textures/entity"

CLEAR = (0, 0, 0, 0)

# --- palette ------------------------------------------------------------------------------------
ARMOR = (156, 46, 40)           # the red plate the suit is mostly made of — dark, so it reads metal
ARMOR_DARK = (104, 30, 28)      # the robe's shadowed hem and the limbs
PLATE = (188, 62, 50)           # pauldrons: lighter than the suit, so the shoulders read first
COLLAR = (168, 142, 62)         # the neck ring — the one warm note the SCV and Marine both carry
GUN = (58, 58, 64)              # flamethrower body, barrels, grip
GUN_TRIM = (92, 92, 100)        # feed line, a shade up so it reads off the body
TANK = (196, 158, 66)           # fuel bottles: brass, the only thing on the model that is not red
TANK_DARK = (132, 104, 42)      # yoke and hose, ringing the bottles in shadow

PILOT = (250, 178, 72)          # pilot lights, base pass
PILOT_GLOW = (255, 238, 186)

PALETTE = {
    "body": ARMOR,
    "robe": ARMOR_DARK,
    "neck_ring": COLLAR,
    "pauldron_left": PLATE, "pauldron_right": PLATE,
    "tank_left": TANK, "tank_right": TANK,
    "tank_yoke": TANK_DARK, "fuel_hose": TANK_DARK,
    "arm_left": ARMOR, "arm_right": ARMOR, "arms_folded": ARMOR,
    "flamer_body": GUN, "flamer_grip": GUN,
    "flamer_barrel_left": GUN, "flamer_barrel_right": GUN,
    "flamer_feed": GUN_TRIM,
    "pilot_light_left": PILOT, "pilot_light_right": PILOT,
    "leg_left": ARMOR_DARK, "leg_right": ARMOR_DARK,
}

# Which parts appear in the emissive pass, and in what colour. Deliberately two.
GLOWING = {"pilot_light_left": PILOT_GLOW, "pilot_light_right": PILOT_GLOW}

# Per-face brightness. Box UV order is top, bottom, right(-x), front(-z), left(+x), back(+z).
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


def paint_face(px, rect, color, factor, seed, tex_w, tex_h, noise=True, rim=True):
    x0, y0, fw, fh = rect
    base = shade(color, factor)
    for dy in range(fh):
        for dx in range(fw):
            x, y = x0 + dx, y0 + dy
            if not (0 <= x < tex_w and 0 <= y < tex_h):
                continue
            n = jitter(x, y, seed) if noise else 0
            # A darker rim around every face gives each cube its own edge at Minecraft's scale,
            # where an unbroken flat colour makes neighbouring cubes melt into one another.
            on_edge = dx == 0 or dy == 0 or dx == fw - 1 or dy == fh - 1
            edge = 0.74 if (rim and on_edge) else 1.0
            px[x, y] = (
                min(255, max(0, int(base[0] * edge) + n)),
                min(255, max(0, int(base[1] * edge) + n)),
                min(255, max(0, int(base[2] * edge) + n)),
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
        if not part["cubes"]:
            continue  # an empty container part (head, arms, flamer, tank) has nothing to paint
        color = PALETTE.get(name)
        if color is None:
            unknown.append(name)
            continue
        for index, cube in enumerate(part["cubes"]):
            w, h, d = (int(math.ceil(v)) for v in cube["dimensions"])
            u, v = int(cube["texOffs"][0]), int(cube["texOffs"][1])
            seed = abs(hash((name, index))) % 9973
            for face, rect in faces(u, v, w, h, d).items():
                paint_face(spx, rect, color, FACE_SHADE[face], seed, tex_w, tex_h)
                if name in GLOWING:
                    # The emissive pass is flatter on purpose: a glow with strong directional
                    # shading stops looking like it emits its own light.
                    paint_face(gpx, rect, GLOWING[name],
                               0.85 + 0.15 * FACE_SHADE[face], seed + 1, tex_w, tex_h,
                               noise=False, rim=False)
            painted += 1

    if unknown:
        raise SystemExit(f"No palette entry for: {', '.join(sorted(set(unknown)))}")

    TEX_DIR.mkdir(parents=True, exist_ok=True)
    skin.save(TEX_DIR / "firebat.png")
    glow.save(TEX_DIR / "firebat_glow.png")
    print(f"painted {painted} cubes onto {tex_w}x{tex_h}")
    print(f"wrote {TEX_DIR / 'firebat.png'}")
    print(f"wrote {TEX_DIR / 'firebat_glow.png'}")


if __name__ == "__main__":
    main()
