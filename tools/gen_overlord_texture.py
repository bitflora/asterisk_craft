#!/usr/bin/env python3
"""
Generate the Overlord's entity textures.

Same approach as `gen_lurker_texture.py`: read the packed per-cube UV islands back out of
`build/model-export/overlord.json` and paint each cube's six faces individually, shaded by face
direction. Output is a starting point, not a finished skin — the point is that
`tools/blockbench/overlord.bbmodel` opens onto a correct layout with real artwork on it, so painting
is a paint job rather than a setup job. See docs/texturing.md.

Two files are written:
    overlord.png       the skin — a brown hide over pale bone
    overlord_glow.png  the emissive pass: the eyes only, everything else transparent, since
                       UnitGlowLayer re-submits the whole model

The palette is sampled from `hydralisk.png` the same way the Lurker's was, because everything in the
swarm is meant to read as the same animal. The Overlord is the brown one: the brief for this unit was
a brown sac, so where the Lurker leans on its bone plating this leans on the hide, and the only
non-brown parts are the skull's own bone and teeth.

Two things about this unit specifically drive the choices below:

* **The body is one enormous flat cube.** At 16 units across it is by far the largest unbroken
  surface in the mod, and it renders at 4x, so every texel is four times the size of a normal unit's.
  Flat brown at that scale reads as cardboard. It gets the heaviest mottling of anything here, and a
  wider jitter amplitude than the Lurker's hide ever used.
* **The head has to separate from the sac.** A Zergling skull painted in the body's own brown
  disappears into the 16-unit cube directly behind it. The cranium is therefore a full step darker
  than the hide, and everything on it — horns, ridge, teeth — is bone, so the head always has
  contrast against what it is mounted on.

Usage:
    python tools/gen_overlord_texture.py
Run it AFTER `./gradlew test` has refreshed the geometry dump, or the islands it paints will be the
ones from the previous layout.
"""
import json
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
DUMP = ROOT / "build" / "model-export" / "overlord.json"
TEX_DIR = ROOT / "src/main/resources/assets/asteriskcraft/textures/entity"

CLEAR = (0, 0, 0, 0)

# --- palette ------------------------------------------------------------------------------------
# Sampled from hydralisk.png, as the Lurker's was: the same animal in a different shape.
HIDE = (108, 62, 33)         # the sac itself — a touch lighter than the Lurker's so it reads brown
HIDE_DARK = (72, 40, 22)     # the cranium and the tentacles, so both separate from the sac
HIDE_DEEP = (52, 28, 15)     # the deepest crease colour, for the tentacle tips
BONE = (170, 122, 68)        # horns and the brow ridge
BONE_DIM = (138, 96, 54)     # bone in shadow — the snout ridge and the lower jaw
FANG = (206, 176, 122)       # teeth, the brightest non-emissive thing on the model
EYE = (255, 224, 160)        # matches the Hydralisk's lit eye

# Emissive pass, off hydralisk_glow.png. The eyes and nothing else: this unit carries no spines and
# no weapon, and a glowing sac would read as a light source rather than as an animal.
EYE_GLOW = (255, 224, 160)

PALETTE = {
    # The sac
    "body": HIDE,
    # The fringe. Darker than the sac so nine of them crossing in front of it stay legible.
    "tentacle1": HIDE_DARK, "tentacle2": HIDE_DARK, "tentacle3": HIDE_DARK,
    "tentacle4": HIDE_DARK, "tentacle5": HIDE_DARK, "tentacle6": HIDE_DARK,
    "tentacle7": HIDE_DEEP, "tentacle8": HIDE_DEEP, "tentacle9": HIDE_DEEP,
    # Head — the Zergling's, and painted like one
    "head": HIDE_DARK,
    "horn_left": BONE, "horn_right": BONE,
    "eye_left": EYE, "eye_right": EYE,
    "upper_jaw": HIDE_DARK, "lower_jaw": HIDE_DARK, "snout_ridge": BONE_DIM,
    "fang_upper_left": FANG, "fang_upper_right": FANG,
    "fang_lower_left": FANG, "fang_lower_right": FANG,
}

GLOWING = {"eye_left": EYE_GLOW, "eye_right": EYE_GLOW}

# Parts painted with heavier noise. The sac gets the widest of all — see the module docs on why a
# 16-unit cube at 4x render scale cannot be flat.
MOTTLED = {"head", "upper_jaw", "lower_jaw"}
HEAVILY_MOTTLED = {"body"}

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
                if name not in GLOWING:
                    continue
                # The emissive pass is flatter on purpose: a glow with strong directional shading
                # stops looking like it emits its own light.
                paint_face(gpx, rect, GLOWING[name], 0.85 + 0.15 * FACE_SHADE[face],
                           seed + 1, tex_w, tex_h, 2)
            painted += 1

    if unknown:
        raise SystemExit(f"No palette entry for: {', '.join(sorted(set(unknown)))}")

    TEX_DIR.mkdir(parents=True, exist_ok=True)
    skin.save(TEX_DIR / "overlord.png")
    glow.save(TEX_DIR / "overlord_glow.png")
    print(f"painted {painted} cubes onto {tex_w}x{tex_h}")
    print(f"wrote {TEX_DIR / 'overlord.png'}")
    print(f"wrote {TEX_DIR / 'overlord_glow.png'}")


if __name__ == "__main__":
    main()
