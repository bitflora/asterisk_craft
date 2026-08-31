#!/usr/bin/env python3
"""
Generate the Terran Goliath's entity textures.

Same approach as `gen_missile_turret_texture.py`: read the packed per-cube UV islands back out of
`build/model-export/goliath.json` and paint each cube's six faces individually, shaded by face
direction. Output is a starting point, not a finished skin — the point is that
`tools/blockbench/goliath.bbmodel` opens onto a correct layout with real artwork on it, so painting
is a paint job rather than a setup job. See docs/texturing.md.

**Most of this model is not here**, which is the thing to know before editing either PNG. The
chassis is vanilla's iron golem, drawn by `GoliathGolemLayer` off `iron_golem.png`; the pilot is
vanilla's villager, drawn by `GoliathPilotLayer` off `villager.png`; and *the two cannon pods that
replaced the golem's arms* come off `iron_golem.png` too, because they are still the golem's arms
and carry vanilla's arm UVs (see `GoliathModel`). So the only geometry this mod paints is the two
muzzle assemblies and the cockpit tub, and this script writes exactly those. The `pod_*` parts are
skipped on purpose, not by omission.

Two files are written:
    goliath.png       muzzle collars, barrels and the cockpit tub
    goliath_glow.png  the emissive pass: the barrels alone, so a Goliath's guns read as hot at
                      night. Everything else stays transparent, since UnitGlowLayer re-submits the
                      whole model

Usage:
    python tools/gen_goliath_texture.py
Run it AFTER `./gradlew test` has refreshed the geometry dump, or the islands it paints will be the
ones from the previous layout.
"""
import json
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
DUMP = ROOT / "build" / "model-export" / "goliath.json"
TEX_DIR = ROOT / "src/main/resources/assets/asteriskcraft/textures/entity"

CLEAR = (0, 0, 0, 0)

# The parts painted from vanilla's iron_golem.png instead of from here. Skipped rather than
# rejected, and the skip is load-bearing rather than tidy: those cubes are drawn twice, and the model
# pass relies on finding NOTHING at their texels so the cutout render type discards them there. Paint
# over their islands and every cannon gains a ghost double in mod colours.
BORROWED = {"pod_left", "pod_right"}

# --- palette ------------------------------------------------------------------------------------
# Gun-metal against vanilla's weathered grey-green iron: the mod's own pieces are the *machined*
# parts of a machine that is otherwise a golem, so they read darker and colder than the plating they
# are bolted to rather than trying to match it.
COLLAR = (86, 90, 96)      # the muzzle housing each barrel cluster stands out of
BARREL = (58, 60, 66)      # the outer two barrels of each three
BARREL_HOT = (120, 74, 52)  # the centre one, warmer, so an abutting row still reads as three
TUB = (104, 96, 78)        # the cockpit the pilot sits in — khaki, to read as crew space not gun

BARREL_GLOW = (255, 168, 96)  # the emissive pass, on the barrels alone

# Keyed (part name, cube index). Cube 0 of each muzzle is the collar; 1-3 are the barrels, with 2
# the longer centre one.
PALETTE = {
    ("muzzle_left", 0): COLLAR,
    ("muzzle_left", 1): BARREL,
    ("muzzle_left", 2): BARREL_HOT,
    ("muzzle_left", 3): BARREL,
    ("muzzle_right", 0): COLLAR,
    ("muzzle_right", 1): BARREL,
    ("muzzle_right", 2): BARREL_HOT,
    ("muzzle_right", 3): BARREL,
    ("tub", 0): TUB,
}

# The barrels alone: a cockpit that glowed in the dark would read as on fire, and the collar is
# housing rather than ordnance.
GLOWING = {
    ("muzzle_left", 1), ("muzzle_left", 2), ("muzzle_left", 3),
    ("muzzle_right", 1), ("muzzle_right", 2), ("muzzle_right", 3),
}

# Per-face brightness. Box UV order is top, bottom, right(-x), front(-z), left(+x), back(+z).
FACE_SHADE = {"top": 1.30, "bottom": 0.55, "right": 0.86, "front": 1.06, "left": 0.86, "back": 0.78}


def shade(color, factor):
    return tuple(min(255, max(0, int(round(c * factor)))) for c in color)


def jitter(x, y, seed):
    """Deterministic +-3 per-texel noise, so a flat panel reads as painted metal, not plastic."""
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
        if not part["cubes"] or name in BORROWED:
            continue  # containers have no cubes; the pods are vanilla's to paint, not ours
        for index, cube in enumerate(part["cubes"]):
            color = PALETTE.get((name, index))
            if color is None:
                unknown.append(f"{name}[{index}]")
                continue
            w, h, d = (int(math.ceil(v)) for v in cube["dimensions"])
            u, v = int(cube["texOffs"][0]), int(cube["texOffs"][1])
            seed = abs(hash((name, index))) % 9973
            for face, rect in faces(u, v, w, h, d).items():
                paint_face(spx, rect, color, FACE_SHADE[face], seed, tex_w, tex_h)
                if (name, index) in GLOWING:
                    # The emissive pass is flatter on purpose: a glow with strong directional
                    # shading stops looking like it emits its own light.
                    paint_face(gpx, rect, BARREL_GLOW,
                               0.85 + 0.15 * FACE_SHADE[face], seed + 1, tex_w, tex_h,
                               noise=False, rim=False)
            painted += 1

    if unknown:
        raise SystemExit(f"No palette entry for: {', '.join(sorted(set(unknown)))}")

    TEX_DIR.mkdir(parents=True, exist_ok=True)
    skin.save(TEX_DIR / "goliath.png")
    glow.save(TEX_DIR / "goliath_glow.png")
    print(f"painted {painted} cubes onto {tex_w}x{tex_h}")
    print(f"wrote {TEX_DIR / 'goliath.png'}")
    print(f"wrote {TEX_DIR / 'goliath_glow.png'}")


if __name__ == "__main__":
    main()
