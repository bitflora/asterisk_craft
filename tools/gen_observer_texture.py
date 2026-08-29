#!/usr/bin/env python3
"""
Generate the Protoss Observer's entity textures.

Same approach as `gen_wraith_texture.py`: read the packed per-cube UV islands back out of
`build/model-export/observer.json` and paint each cube's six faces individually, shaded by face
direction. Output is a starting point, not a finished skin — the point is that
`tools/blockbench/observer.bbmodel` opens onto a correct layout with real artwork on it, so painting
is a paint job rather than a setup job. See docs/texturing.md.

The palette comes off the reference art and is split two ways, because the model is two things:

    pod     the cubified sphere at the centre — a dark violet-grey the eye reads as "not lit"
    cage    the three arcs around it, Protoss gold, which is the whole of the silhouette at the
            distance a flyer is usually seen from

That split is doing more work here than on any other unit. An Observer spends most of its life
either invisible or ghosted (`client/DetectionRenderStateModifier`), so what has to survive is a
shape, not a paint job: a dark pod inside bright arcs reads as one recognisable object even at a
fraction of its opacity, where an evenly-toned model would read as a smudge.

Two files are written:
    observer.png       the whole model
    observer_glow.png  the emissive pass: the lens, its bezel rim, and the crown emitter. Everything
                       else stays transparent, since UnitGlowLayer re-submits the whole model and
                       anything opaque here would light up with it

Usage:
    python tools/gen_observer_texture.py
Run it AFTER `./gradlew test` has refreshed the geometry dump, or the islands it paints will be the
ones from the previous layout.
"""
import json
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
DUMP = ROOT / "build" / "model-export" / "observer.json"
TEX_DIR = ROOT / "src/main/resources/assets/asteriskcraft/textures/entity"

CLEAR = (0, 0, 0, 0)

# --- palette ------------------------------------------------------------------------------------
POD = (68, 62, 92)          # dark violet-grey: the core of the cubified sphere
POD_CAP = (86, 80, 112)     # the two caps, a step lighter so the stepped sphere reads as stepped
BEZEL = (168, 140, 62)      # the ring around the eye: gold, so the lens has something to sit in
LENS = (52, 44, 30)         # near-black glass; the glow pass is what makes it read as lit
CAGE = (196, 162, 74)       # Protoss gold — the arcs, and the whole silhouette at distance
CAGE_DARK = (150, 122, 54)  # the two lower arcs, a step down so the crown arc reads as the top
EMITTER = (58, 54, 44)      # dark metal under the crown light
FIN = (110, 96, 130)        # the tail blade, between pod and cage in tone

LENS_GLOW = (255, 168, 72)   # the warm orange eye — the one thing the reference art is about
EMITTER_GLOW = (168, 220, 255)  # cold Protoss blue on the crown, so the two lights read apart

PALETTE = {
    ("body", 0): POD,
    ("cap_top", 0): POD_CAP,
    ("cap_bottom", 0): POD_CAP,
    ("bezel", 0): BEZEL,
    ("lens", 0): LENS,
    ("emitter", 0): EMITTER,
    ("fin", 0): FIN,
    ("arm_top_1", 0): CAGE,
    ("arm_top_2", 0): CAGE,
    ("arm_top_3", 0): CAGE,
    ("arm_left_1", 0): CAGE_DARK,
    ("arm_left_2", 0): CAGE_DARK,
    ("arm_left_3", 0): CAGE_DARK,
    ("arm_right_1", 0): CAGE_DARK,
    ("arm_right_2", 0): CAGE_DARK,
    ("arm_right_3", 0): CAGE_DARK,
}

# The emissive pass, keyed to its own colour per cube. Deliberately only two cubes: the unit's whole
# read is one bright eye, and a cage that glowed too would swallow it.
GLOWING = {
    ("lens", 0): LENS_GLOW,
    ("emitter", 0): EMITTER_GLOW,
}

# The lens is a plate one texel deep facing -z, so only its front cap is the eye. Its four edge
# faces are the rim of the glass and would draw a lit square outline around the pupil if lit
# edge to edge.
FRONT_ONLY = {("lens", 0)}

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

    The crown emitter glows edge to edge — it is a blister, and light coming off all of it is the
    point. The lens glows on its front cap only: it is one texel deep, so its other five faces are
    the rim of the glass and lighting them would ring the pupil in a bright square.
    """
    if key not in FRONT_ONLY:
        return (0, 0, fw, fh)
    return (0, 0, fw, fh) if face == "front" else None


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
            continue  # containers have no cubes of their own — cage and the three arcs
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
    skin.save(TEX_DIR / "observer.png")
    glow.save(TEX_DIR / "observer_glow.png")
    print(f"painted {painted} cubes onto {tex_w}x{tex_h}")
    print(f"wrote {TEX_DIR / 'observer.png'}")
    print(f"wrote {TEX_DIR / 'observer_glow.png'}")


if __name__ == "__main__":
    main()
