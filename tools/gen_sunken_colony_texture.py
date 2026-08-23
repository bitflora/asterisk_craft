#!/usr/bin/env python3
"""
Generate the Sunken Colony's entity textures.

Same approach as `gen_spore_colony_texture.py`: read the packed per-cube UV islands back out of
`build/model-export/sunken_colony.json` and paint each cube's six faces individually, shaded by face
direction. Output is a starting point, not a finished skin — the point is that
`tools/blockbench/sunken_colony.bbmodel` opens onto a correct layout with real artwork on it, so
painting is a paint job rather than a setup job. See docs/texturing.md.

Two files are written:
    sunken_colony.png       the skin — a near-black root mound and skirt, dark red tentacle and
                            ground tendrils, pale bone spines and claw, and the glowing red maw
    sunken_colony_glow.png  the emissive pass: ONLY the maw, everything else transparent, since
                            UnitGlowLayer re-submits the whole model

The colours are lifted straight off the unit's original flat-painted art (sampled from the shipped
texture before this script existed) so the reskin stays the same animal, just no longer plastic-flat:
that art had one solid colour per part and no per-face shading at all. The root mound and its skirt
are the two biggest unbroken surfaces on the model — the mound's top is a 14x14 island and the skirt's
is 16x16, wider than anything on the Overlord — so both take the Overlord's heaviest jitter amplitude.
The tentacle and the low ground tendrils are the same hide and get the standard mottled amplitude, the
way the Overlord's fringe does. The bone spines, the claw, and the maw stay clean: bone reads as bone
precisely because it isn't blotchy, and the maw is the emissive read-at-a-distance cue and must stay
legible rather than noisy.

Usage:
    python tools/gen_sunken_colony_texture.py
Run it AFTER `./gradlew test` has refreshed the geometry dump, or the islands it paints will be the
ones from the previous layout.
"""
import json
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
DUMP = ROOT / "build" / "model-export" / "sunken_colony.json"
TEX_DIR = ROOT / "src/main/resources/assets/asteriskcraft/textures/entity"

CLEAR = (0, 0, 0, 0)

# --- palette ------------------------------------------------------------------------------------
# Sampled from the unit's original flat art: a near-black root mass, a dark red tentacle/tendril hide,
# pale bone spines and claw, and a bright red maw.
ROOT_MASS = (38, 20, 24)     # the mound and its skirt
TENTACLE = (122, 28, 30)     # the segmented tentacle and the low ground tendrils
BONE = (208, 194, 168)       # spines and the claw tip
MAW = (150, 30, 20)          # the glowing mouth

# Emissive pass — only the maw appears in the glow texture, matching the original art's glow colour.
MAW_GLOW = (255, 90, 40)

PALETTE = {
    "base": ROOT_MASS, "skirt": ROOT_MASS,
    "tentacle1": TENTACLE, "tentacle2": TENTACLE, "tentacle3": TENTACLE,
    "rootL": TENTACLE, "rootR": TENTACLE,
    "spineFL": BONE, "spineFR": BONE, "spineBL": BONE, "spineBR": BONE, "claw": BONE,
    "maw": MAW,
}

# Which parts appear in the emissive pass, and in what colour.
GLOWING = {"maw": MAW_GLOW}

# Parts painted with heavier noise. The mound and skirt are the widest unbroken surfaces on the
# model, so they get the Overlord's heaviest amplitude; the tentacle and tendrils get the standard
# mottled level. Bone and the maw stay flat — see the module docs on why.
MOTTLED = {"tentacle1", "tentacle2", "tentacle3", "rootL", "rootR"}
HEAVILY_MOTTLED = {"base", "skirt"}

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
        if name in HEAVILY_MOTTLED:
            amplitude = 14
        elif name in MOTTLED:
            amplitude = 9
        else:
            amplitude = 3
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
    skin.save(TEX_DIR / "sunken_colony.png")
    glow.save(TEX_DIR / "sunken_colony_glow.png")
    print(f"painted {painted} cubes onto {tex_w}x{tex_h}")
    print(f"wrote {TEX_DIR / 'sunken_colony.png'}")
    print(f"wrote {TEX_DIR / 'sunken_colony_glow.png'}")


if __name__ == "__main__":
    main()
