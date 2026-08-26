#!/usr/bin/env python3
"""
Generate the Marine's entity textures.

Same approach as `gen_scv_texture.py`: read the packed per-cube UV islands back out of
`build/model-export/marine.json` and paint each cube's six faces individually, shaded by face
direction. Output is a starting point, not a finished skin — the point is that
`tools/blockbench/marine.bbmodel` opens onto a correct layout with real artwork on it, so painting
is a paint job rather than a setup job. See docs/texturing.md.

Two files are written:
    marine.png       the skin — a blue-steel suit, gunmetal rifle. NOT the face: the head is
                     vanilla's own villager head drawn on vanilla's own texture by
                     client/terran/MarineHeadLayer, so nothing here paints one
    marine_glow.png  the emissive pass: ONLY the helmet lamp, the antenna tip and the muzzle;
                     everything else transparent, since UnitGlowLayer re-submits the whole model

The palette follows the Terran hull palette the SCV already established (pale steel, violet trim)
but pushed toward armour: the suit is a colder blue-steel and the helmet plates lighter than the
robe under them, so the head reads first and the open front reads as a recess.

Usage:
    python tools/gen_marine_texture.py
Run it AFTER `./gradlew test` has refreshed the geometry dump, or the islands it paints will be the
ones from the previous layout.
"""
import json
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
DUMP = ROOT / "build" / "model-export" / "marine.json"
TEX_DIR = ROOT / "src/main/resources/assets/asteriskcraft/textures/entity"

CLEAR = (0, 0, 0, 0)

# --- palette ------------------------------------------------------------------------------------
SUIT = (108, 126, 156)          # the blue-steel the suit is mostly made of
SUIT_DARK = (74, 88, 116)       # the robe's shadowed hem and the limbs
PLATE = (150, 158, 172)         # helmet plates: lighter than the suit, so the head reads first
PLATE_DARK = (92, 98, 112)      # brim and back plate, ringing the opening in shadow
COLLAR = (168, 142, 62)         # the neck ring — the one warm note on the suit, as the SCV's tools
GUN = (66, 68, 74)              # rifle receiver, barrel, stock
GUN_TRIM = (98, 100, 108)       # magazine and sight, a shade up so they read off the barrel

LAMP = (128, 196, 224)          # helmet lamp, base pass
LAMP_GLOW = (206, 240, 255)
AERIAL = (216, 120, 92)         # antenna tip, base pass
AERIAL_GLOW = (255, 188, 150)
MUZZLE = (240, 196, 96)         # rifle muzzle, base pass
MUZZLE_GLOW = (255, 244, 190)

PALETTE = {
    "helmet_crown": PLATE,
    "helmet_back": PLATE_DARK,
    "helmet_side_left": PLATE, "helmet_side_right": PLATE,
    "helmet_brim": PLATE_DARK,
    "helmet_lamp": LAMP,
    "antenna": PLATE_DARK, "antenna_tip": AERIAL,
    "body": SUIT,
    "robe": SUIT_DARK,
    "neck_ring": COLLAR,
    "pauldron_left": SUIT, "pauldron_right": SUIT,
    "arm_left": SUIT, "arm_right": SUIT, "arms_folded": SUIT,
    "rifle_stock": GUN, "rifle_receiver": GUN, "rifle_barrel": GUN,
    "rifle_magazine": GUN_TRIM, "rifle_sight": GUN_TRIM, "rifle_muzzle": MUZZLE,
    "leg_left": SUIT_DARK, "leg_right": SUIT_DARK,
}

# Which parts appear in the emissive pass, and in what colour. Deliberately three.
GLOWING = {"helmet_lamp": LAMP_GLOW, "antenna_tip": AERIAL_GLOW, "rifle_muzzle": MUZZLE_GLOW}

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
            continue  # an empty container part (arms, rifle) has nothing to paint
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
    skin.save(TEX_DIR / "marine.png")
    glow.save(TEX_DIR / "marine_glow.png")
    print(f"painted {painted} cubes onto {tex_w}x{tex_h}")
    print(f"wrote {TEX_DIR / 'marine.png'}")
    print(f"wrote {TEX_DIR / 'marine_glow.png'}")


if __name__ == "__main__":
    main()
