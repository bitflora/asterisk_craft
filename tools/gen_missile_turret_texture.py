#!/usr/bin/env python3
"""
Generate the Terran Missile Turret's entity textures.

Same approach as `gen_bunker_texture.py`: read the packed per-cube UV islands back out of
`build/model-export/missile_turret.json` and paint each cube's six faces individually, shaded by
face direction. Output is a starting point, not a finished skin — the point is that
`tools/blockbench/missile_turret.bbmodel` opens onto a correct layout with real artwork on it, so
painting is a paint job rather than a setup job. See docs/texturing.md.

**This model is almost entirely not here**, which is the thing to know before editing either PNG.
The turret's head, body, legs *and the two racks that replaced its arms* are all drawn by
`MissileTurretGolemLayer` off vanilla's own `iron_golem.png` — the racks included, because they are
still the golem's arms and carry vanilla's arm UVs (see `MissileTurretModel`). So the only geometry
this mod paints is the six missiles standing out of the two muzzles, and this script writes exactly
those and nothing else. The `shell_*` parts are skipped on purpose, not by omission.

The palette is keyed per *cube* rather than per part, the way the missiles differ from each other:
the centre one of each three is warmer, so an abutting row of tips still reads as three.

Two files are written:
    missile_turret.png       the six missiles — pale bodies, a warmer one in the middle
    missile_turret_glow.png  the emissive pass: the same six, so a loaded turret reads as loaded at
                             night. Everything else stays transparent, since UnitGlowLayer
                             re-submits the whole model

Usage:
    python tools/gen_missile_turret_texture.py
Run it AFTER `./gradlew test` has refreshed the geometry dump, or the islands it paints will be the
ones from the previous layout.
"""
import json
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
DUMP = ROOT / "build" / "model-export" / "missile_turret.json"
TEX_DIR = ROOT / "src/main/resources/assets/asteriskcraft/textures/entity"

CLEAR = (0, 0, 0, 0)

# The parts painted from vanilla's iron_golem.png instead of from here. Skipped rather than
# rejected, and the skip is load-bearing rather than tidy: those cubes are drawn twice, and the model
# pass relies on finding NOTHING at their texels so the cutout render type discards them there. Paint
# over their islands and every rack gains a ghost double in mod colours.
BORROWED = {"shell_left", "shell_right"}

# --- palette ------------------------------------------------------------------------------------
# Bone-pale on purpose. These sit in the mouth of a rack painted from vanilla's weathered grey-green
# iron, and the missiles are the one thing on the whole unit meant to catch the eye at distance.
MISSILE = (188, 186, 178)     # the outer two of each three
MISSILE_TIP = (150, 62, 48)   # the centre missile, warmer, so an abutting row still reads as three

MISSILE_GLOW = (255, 208, 138)  # the emissive pass, on the missiles alone

# Keyed (part name, cube index): cubes 0 and 2 are the outer missiles, 1 the longer centre one.
PALETTE = {
    ("missiles_left", 0): MISSILE,
    ("missiles_left", 1): MISSILE_TIP,
    ("missiles_left", 2): MISSILE,
    ("missiles_right", 0): MISSILE,
    ("missiles_right", 1): MISSILE_TIP,
    ("missiles_right", 2): MISSILE,
}

# Every cube here is emissive: the whole mod-owned half of this model is ordnance.
GLOWING = set(PALETTE)

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
            continue  # containers have no cubes; the shells are vanilla's to paint, not ours
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
                    paint_face(gpx, rect, MISSILE_GLOW,
                               0.85 + 0.15 * FACE_SHADE[face], seed + 1, tex_w, tex_h,
                               noise=False, rim=False)
            painted += 1

    if unknown:
        raise SystemExit(f"No palette entry for: {', '.join(sorted(set(unknown)))}")

    TEX_DIR.mkdir(parents=True, exist_ok=True)
    skin.save(TEX_DIR / "missile_turret.png")
    glow.save(TEX_DIR / "missile_turret_glow.png")
    print(f"painted {painted} cubes onto {tex_w}x{tex_h}")
    print(f"wrote {TEX_DIR / 'missile_turret.png'}")
    print(f"wrote {TEX_DIR / 'missile_turret_glow.png'}")


if __name__ == "__main__":
    main()
