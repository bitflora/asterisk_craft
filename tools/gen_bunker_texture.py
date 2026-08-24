#!/usr/bin/env python3
"""
Generate the Terran Bunker's entity textures.

Same approach as `gen_marine_texture.py`: read the packed per-cube UV islands back out of
`build/model-export/bunker.json` and paint each cube's six faces individually, shaded by face
direction. Output is a starting point, not a finished skin — the point is that
`tools/blockbench/bunker.bbmodel` opens onto a correct layout with real artwork on it, so painting
is a paint job rather than a setup job. See docs/texturing.md.

Two files are written:
    bunker.png       the hull — poured concrete over a steel frame, in the Terran palette the SCV
                     and Marine already established (pale steel, warm trim)
    bunker_glow.png  the emissive pass: ONLY the four barrel muzzles, so a loaded Bunker reads as
                     loaded in the dark too. Everything else stays transparent, since UnitGlowLayer
                     re-submits the whole model

The palette is deliberately duller than either Terran unit's. A Bunker is the thing soldiers hide
behind, so it wants to read as terrain that happens to be yours: concrete body, darker frame posts,
a near-black slit band so the openings look like openings, and warm gunmetal on the barrels — the
only part of the building that is equipment rather than structure.

Usage:
    python tools/gen_bunker_texture.py
Run it AFTER `./gradlew test` has refreshed the geometry dump, or the islands it paints will be the
ones from the previous layout.
"""
import json
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
DUMP = ROOT / "build" / "model-export" / "bunker.json"
TEX_DIR = ROOT / "src/main/resources/assets/asteriskcraft/textures/entity"

CLEAR = (0, 0, 0, 0)

# --- palette ------------------------------------------------------------------------------------
CONCRETE = (146, 148, 142)      # the hull: poured grey, the largest surface on the model
CONCRETE_DARK = (112, 114, 110) # the foundation, sitting in its own shadow against the ground
ROOF = (128, 132, 138)          # a touch cooler and bluer than the hull, so the lid reads as metal
FRAME = (86, 90, 98)            # corner posts: the steel the concrete was poured around
SLIT = (28, 30, 34)             # near-black, so an opening looks like a hole and not a stripe
BARREL = (72, 74, 80)           # gunmetal, the one part of the building that is equipment

MUZZLE_GLOW = (255, 226, 150)   # the emissive pass, on the barrels alone

PALETTE = {
    "foundation": CONCRETE_DARK,
    "hull": CONCRETE,
    "roof": ROOF,
    "posts": FRAME,
    "slits": SLIT,
    "barrel_north": BARREL,
    "barrel_south": BARREL,
    "barrel_east": BARREL,
    "barrel_west": BARREL,
}

# Which parts appear in the emissive pass, and in what colour. Deliberately only the barrels: the
# building itself emits nothing, and what a player needs to see at night is whether it is loaded.
GLOWING = {
    "barrel_north": MUZZLE_GLOW,
    "barrel_south": MUZZLE_GLOW,
    "barrel_east": MUZZLE_GLOW,
    "barrel_west": MUZZLE_GLOW,
}

# Per-face brightness. Box UV order is top, bottom, right(-x), front(-z), left(+x), back(+z).
FACE_SHADE = {"top": 1.30, "bottom": 0.55, "right": 0.86, "front": 1.06, "left": 0.86, "back": 0.78}


def shade(color, factor):
    return tuple(min(255, max(0, int(round(c * factor)))) for c in color)


def jitter(x, y, seed):
    """Deterministic +-3 per-texel noise, so flat concrete reads as poured rather than plastic."""
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
            continue  # an empty container part has nothing to paint
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
    skin.save(TEX_DIR / "bunker.png")
    glow.save(TEX_DIR / "bunker_glow.png")
    print(f"painted {painted} cubes onto {tex_w}x{tex_h}")
    print(f"wrote {TEX_DIR / 'bunker.png'}")
    print(f"wrote {TEX_DIR / 'bunker_glow.png'}")


if __name__ == "__main__":
    main()
