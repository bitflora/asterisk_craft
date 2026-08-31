#!/usr/bin/env python3
"""
Generate the Terran Science Vessel's entity textures.

Same approach as `gen_observer_texture.py` and `gen_wraith_texture.py`: read the packed per-cube UV
islands back out of `build/model-export/science_vessel.json` and paint each cube's six faces
individually, shaded by face direction. Output is a starting point, not a finished skin — the point
is that `tools/blockbench/science_vessel.bbmodel` opens onto a correct layout with real artwork on
it, so painting is a paint job rather than a setup job. See docs/texturing.md.

The palette comes off the reference art and is split three ways, because the model is three things:

    hull    the saucer — bone-white Terran plating, the lightest thing the race fields, so the disc
            reads as one broad mass at the altitude a flyer is usually seen from
    frame   keel, outrigger arms and fins — dark gunmetal, so the parts that stick out of the disc
            read as structure hung off it rather than as more of it
    dome    the sensor blister and the two pod lenses — the only lit surfaces on the model

That split is the whole silhouette. A Science Vessel is never trying to be told apart from another
aircraft the way the Wraith is from the Scout; it is trying to be told apart from *an aircraft*, and
a pale disc with a dark frame and one green eye is what does that at fifty blocks.

Two files are written:
    science_vessel.png       the whole model
    science_vessel_glow.png  the emissive pass: the dome cap and the two pod lenses. Everything else
                             stays transparent, since UnitGlowLayer re-submits the whole model and
                             anything opaque here would light up with it

Usage:
    python tools/gen_science_vessel_texture.py
Run it AFTER `./gradlew test` has refreshed the geometry dump, or the islands it paints will be the
ones from the previous layout.
"""
import json
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
DUMP = ROOT / "build" / "model-export" / "science_vessel.json"
TEX_DIR = ROOT / "src/main/resources/assets/asteriskcraft/textures/entity"

CLEAR = (0, 0, 0, 0)

# --- palette ------------------------------------------------------------------------------------
HULL = (186, 182, 170)       # bone-white plating: the hull core
HULL_RIM = (206, 203, 192)   # the saucer rim and prow, a step lighter so the disc edge catches light
HULL_DARK = (146, 143, 134)  # the stern, so the ship has a shaded end
FRAME = (74, 78, 84)         # keel, outrigger arms and fins: dark gunmetal hung off the disc
POD = (116, 118, 124)        # the two outrigger pods, between hull and frame in tone
DOME_BEZEL = (92, 96, 92)    # the drum the sensor cap sits in
DOME = (38, 66, 48)          # dark green glass; the glow pass is what makes it read as lit
LENS = (44, 58, 66)          # the pod caps, unlit — near-black so their glow has somewhere to sit

DOME_GLOW = (108, 246, 138)  # the green eye the reference art is really about
LENS_GLOW = (150, 214, 255)  # cold blue on the pods, so the three lights read apart

PALETTE = {
    ("body", 0): HULL,
    ("rim", 0): HULL_RIM,
    ("nose", 0): HULL_RIM,
    ("stern", 0): HULL_DARK,
    ("dome_base", 0): DOME_BEZEL,
    ("dome", 0): DOME,
    ("keel", 0): FRAME,
    ("fin_left", 0): FRAME,
    ("fin_right", 0): FRAME,
    ("arm_left", 0): FRAME,
    ("arm_right", 0): FRAME,
    ("pod_left", 0): POD,
    ("pod_right", 0): POD,
    ("cap_left", 0): LENS,
    ("cap_right", 0): LENS,
}

# The emissive pass, keyed to its own colour per cube. Three cubes: one big green eye and two small
# blue pod lenses. The hull is deliberately dark on this pass — a saucer that glowed would swallow
# the dome, which is the one thing the silhouette is built around.
GLOWING = {
    ("dome", 0): DOME_GLOW,
    ("cap_left", 0): LENS_GLOW,
    ("cap_right", 0): LENS_GLOW,
}

# Cubes whose glow is their top cap only. The dome is a blister sitting in a bezel: lighting its
# four sides would draw a bright band around the drum instead of a lamp on top of it. The pod caps
# are one texel tall and glow edge to edge, so they are not in here.
TOP_ONLY = {("dome", 0)}

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


def paint_face(px, rect, color, factor, seed, tex_w, tex_h, noise=True, rim=True, clip=None):
    """Paint one box-UV face. `clip` is an optional (dx0, dy0, dx1, dy1) window within it."""
    x0, y0, fw, fh = rect
    base = shade(color, factor)
    for dy in range(fh):
        for dx in range(fw):
            if clip is not None and not (clip[0] <= dx < clip[2] and clip[1] <= dy < clip[3]):
                continue
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


def glow_clip(key, face, fw, fh):
    """
    Which part of one face the emissive pass covers, or None for none of it.

    The pod lenses glow edge to edge — they are small plates and light off all of one is the point.
    The dome glows on its top cap only: it is a blister in a bezel, and lighting its sides would
    band the drum rather than lamp it.
    """
    if key not in TOP_ONLY:
        return (0, 0, fw, fh)
    return (0, 0, fw, fh) if face == "top" else None


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
            continue  # containers have no cubes of their own — "pods" is the only one here
        for index, cube in enumerate(part["cubes"]):
            key = (name, index)
            color = PALETTE.get(key)
            if color is None:
                unknown.append(f"{name}[{index}]")
                continue
            w, h, d = (int(math.ceil(v)) for v in cube["dimensions"])
            u, v = int(cube["texOffs"][0]), int(cube["texOffs"][1])
            seed = abs(hash(key)) % 9973
            for face, rect in faces(u, v, w, h, d).items():
                paint_face(spx, rect, color, FACE_SHADE[face], seed, tex_w, tex_h)
                glow_color = GLOWING.get(key)
                if glow_color is None:
                    continue
                clip = glow_clip(key, face, rect[2], rect[3])
                if clip is None:
                    continue
                # The emissive pass is flatter on purpose: a glow with strong directional shading
                # stops looking like it emits its own light.
                paint_face(gpx, rect, glow_color, 0.85 + 0.15 * FACE_SHADE[face], seed + 1,
                           tex_w, tex_h, noise=False, rim=False, clip=clip)
            painted += 1

    if unknown:
        raise SystemExit(f"No palette entry for: {', '.join(sorted(set(unknown)))}")

    TEX_DIR.mkdir(parents=True, exist_ok=True)
    skin.save(TEX_DIR / "science_vessel.png")
    glow.save(TEX_DIR / "science_vessel_glow.png")
    print(f"painted {painted} cubes onto {tex_w}x{tex_h}")
    print(f"wrote {TEX_DIR / 'science_vessel.png'}")
    print(f"wrote {TEX_DIR / 'science_vessel_glow.png'}")


if __name__ == "__main__":
    main()
