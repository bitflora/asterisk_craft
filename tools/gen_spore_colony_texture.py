#!/usr/bin/env python3
"""
Generate the Spore Colony's entity textures.

Same approach as `gen_dark_templar_texture.py`: read the packed per-cube UV islands back out of
`build/model-export/spore_colony.json` and paint each cube's six faces individually, shaded by face
direction. Output is a starting point, not a finished skin — the point is that
`tools/blockbench/spore_colony.bbmodel` opens onto a correct layout with real artwork on it, so
painting is a paint job rather than a setup job. See docs/texturing.md.

Two files are written:
    spore_colony.png       the skin — a mottled purple body under a near-black carapace, dark stone
                           claws, and the orange chimney with its white eye
    spore_colony_glow.png  the emissive pass: ONLY the mouth at the top of the chimney, everything
                           else transparent, since UnitGlowLayer re-submits the whole model

The palette follows the reference art, which is built on one cool mass (purple body, black shell,
grey claws) and one warm one (the chimney), with the eye as the single bright note. The body gets
heavier per-texel noise than anything else, because the art blotches it with darker spots and flat
purple at this scale reads as plastic. The eye is deliberately excluded from both the mottling and
the glow pass: it is a painted dot in the art, not a lamp.

Usage:
    python tools/gen_spore_colony_texture.py
Run it AFTER `./gradlew test` has refreshed the geometry dump, or the islands it paints will be the
ones from the previous layout.
"""
import json
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
DUMP = ROOT / "build" / "model-export" / "spore_colony.json"
TEX_DIR = ROOT / "src/main/resources/assets/asteriskcraft/textures/entity"

CLEAR = (0, 0, 0, 0)

# --- palette ------------------------------------------------------------------------------------
# Keyed by leaf part name, straight off the reference art: a purple body, a near-black carapace, dark
# stone claws, and the orange chimney that is the whole read at a distance.
BODY = (128, 72, 152)          # the blob, mid purple
BODY_TOP = (142, 84, 166)      # its crown, a shade up so the ellipsoid turns toward the light
SHELL = (48, 30, 58)           # carapace: nearly black, barely purple
CLAW = (92, 96, 104)           # the stone claws — the only cool grey on the model
CHIMNEY = (188, 82, 40)        # the throat
CHIMNEY_MID = (198, 92, 46)    # widening, catching more light
CHIMNEY_RIM = (210, 104, 54)   # the flare, brightest of the three
MOUTH = (104, 38, 16)          # the hole in the flare, in shadow
EYE = (242, 242, 248)          # the single white eye

# Emissive pass — only the mouth appears in the glow texture. The eye is deliberately left out: in
# the art it is a flat white dot, not a lamp, and lighting it turns a face into a headlight.
MOUTH_GLOW = (255, 156, 68)

PALETTE = {
    "body": BODY, "bodyTop": BODY_TOP,
    "shellBack": SHELL, "shellL": SHELL, "shellR": SHELL,
    "clawFL": CLAW, "clawFR": CLAW, "clawL": CLAW, "clawR": CLAW,
    "chimney": CHIMNEY, "chimneyMid": CHIMNEY_MID, "chimneyRim": CHIMNEY_RIM,
    "mouth": MOUTH, "eye": EYE,
}

# Which parts appear in the emissive pass, and in what colour.
GLOWING = {"mouth": MOUTH_GLOW}

# Parts painted with heavier noise. The art gives the body visible darker blotches; nothing else is
# mottled, and the eye must stay a clean flat dot.
MOTTLED = {"body", "bodyTop"}

# Per-face brightness. Box UV order is top, bottom, right(-x), front(-z), left(+x), back(+z).
FACE_SHADE = {"top": 1.30, "bottom": 0.55, "right": 0.86, "front": 1.06, "left": 0.86, "back": 0.78}


def shade(color, factor):
    return tuple(min(255, max(0, int(round(c * factor)))) for c in color)


def jitter(x, y, seed, amplitude=3):
    """Deterministic per-texel noise, so a flat surface reads as organic rather than plastic."""
    h = (x * 73856093) ^ (y * 19349663) ^ (seed * 83492791)
    h = (h ^ (h >> 13)) & 0xFFFF
    span = amplitude * 2 + 1
    return (h % span) - amplitude


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


def paint_face(px, rect, color, factor, seed, tex_w, tex_h, amplitude=3):
    x0, y0, fw, fh = rect
    base = shade(color, factor)
    for dy in range(fh):
        for dx in range(fw):
            x, y = x0 + dx, y0 + dy
            if not (0 <= x < tex_w and 0 <= y < tex_h):
                continue
            n = jitter(x, y, seed, amplitude)
            # A darker rim around every face gives each cube its own edge at Minecraft's scale,
            # where an unbroken flat colour makes neighbouring cubes melt into one another.
            edge = 0.74 if (dx == 0 or dy == 0 or dx == fw - 1 or dy == fh - 1) else 1.0
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
        color = PALETTE.get(name)
        if color is None:
            unknown.append(name)
            continue
        amplitude = 9 if name in MOTTLED else 3
        for index, cube in enumerate(part["cubes"]):
            w, h, d = (int(math.ceil(v)) for v in cube["dimensions"])
            u, v = int(cube["texOffs"][0]), int(cube["texOffs"][1])
            seed = abs(hash((name, index))) % 9973
            for face, rect in faces(u, v, w, h, d).items():
                paint_face(spx, rect, color, FACE_SHADE[face], seed, tex_w, tex_h, amplitude)
                if name in GLOWING:
                    # The emissive pass is flatter on purpose: a glow with strong directional
                    # shading stops looking like it emits its own light.
                    paint_face(gpx, rect, GLOWING[name], 0.85 + 0.15 * FACE_SHADE[face],
                               seed + 1, tex_w, tex_h, 2)
            painted += 1

    if unknown:
        raise SystemExit(f"No palette entry for: {', '.join(sorted(set(unknown)))}")

    TEX_DIR.mkdir(parents=True, exist_ok=True)
    skin.save(TEX_DIR / "spore_colony.png")
    glow.save(TEX_DIR / "spore_colony_glow.png")
    print(f"painted {painted} cubes onto {tex_w}x{tex_h}")
    print(f"wrote {TEX_DIR / 'spore_colony.png'}")
    print(f"wrote {TEX_DIR / 'spore_colony_glow.png'}")


if __name__ == "__main__":
    main()
