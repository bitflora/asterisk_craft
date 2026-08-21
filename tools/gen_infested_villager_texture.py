#!/usr/bin/env python3
"""
Generate the Infested Villager's entity textures.

Same approach as `gen_lurker_texture.py`: read the packed per-cube UV islands back out of
`build/model-export/infested_villager.json` and paint each cube's six faces individually, shaded by
face direction. Output is a starting point, not a finished skin — the point is that
`tools/blockbench/infested_villager.bbmodel` opens onto a correct layout with real artwork on it, so
painting is a paint job rather than a setup job. See docs/texturing.md.

Two files are written:
    infested_villager.png       the skin
    infested_villager_glow.png  the emissive pass: the two eyes and nothing else, since
                                UnitGlowLayer re-submits the whole model

The palette is built around the one idea the model is built around: this is **a villager**, with a
Zergling's head and a few horns pushing out through the robe. So the body is painted like a villager
— robe brown, sleeves a shade up from it, folded hands in a villager's skin gone grey — and the only
Zerg colours anywhere on the sheet are the red-brown skull and the bone yellow of the horns.

Two things about this unit specifically drive the choices below:

* **The contrast has to do the work.** If the robe drifts toward the skull's red-brown, the whole
  read collapses into "some Zerg thing" and the joke is gone. The body stays a colour no other unit
  in the mod uses, and the head stays a colour the body never touches.
* **The horns have to carry at distance.** Bone yellow is the only bright value on the model, and it
  is on exactly the parts that are not villager — which is also, conveniently, what makes the
  silhouette legible across a field. That is not cosmetic: this unit detonates for 250 and a player
  gets about a second and a half to recognise it and leave.

Usage:
    python tools/gen_infested_villager_texture.py
Run it AFTER `./gradlew test` has refreshed the geometry dump, or the islands it paints will be the
ones from the previous layout.
"""
import json
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
DUMP = ROOT / "build" / "model-export" / "infested_villager.json"
TEX_DIR = ROOT / "src/main/resources/assets/asteriskcraft/textures/entity"

CLEAR = (0, 0, 0, 0)

# --- palette --------------------------------------------------------------------------------------
# The body is painted as a *villager*, not as a Zerg unit, because that contrast is the entire idea:
# a person something got into. So the robe takes villager brown, the folded hands take a villager's
# skin gone grey and sick, and the only Zerg colours on the model are the skull and the horns.
ROBE = (94, 68, 51)          # villager robe brown
ROBE_DARK = (68, 48, 36)     # the robe's overhang and the legs under it
SLEEVE = (108, 79, 59)       # the arms, a shade up from the robe so they separate where they cross
FLESH = (147, 118, 95)       # the folded hands: villager skin, gone grey
SKULL = (96, 45, 34)         # the Zergling head — the only red-brown on the model
SKULL_DARK = (68, 31, 24)    # its jaws, so the maw separates from the cranium at a distance
BONE = (214, 186, 104)       # every horn, and the brow and snout ridges
BONE_DIM = (168, 143, 76)    # bone in shadow
FANG = (233, 214, 157)       # teeth, the brightest non-emissive thing on the model
EYE = (255, 206, 88)         # the lit eye

# Emissive pass: the eyes only. Nothing else glows — the fuse's own white flash is what lights the
# unit up when that matters, and a permanently lit horn would compete with that warning.
EYE_GLOW = (255, 214, 120)

PALETTE = {
    # Body: vanilla's villager, painted like one
    "body": ROBE, "robe": ROBE_DARK,
    "leg_left": ROBE_DARK, "leg_right": ROBE_DARK,
    "arm_left": SLEEVE, "arm_right": SLEEVE, "arms_folded": FLESH,
    # Head: the one part that is not a villager
    "head": SKULL, "brow_ridge": BONE_DIM,
    "upper_jaw": SKULL_DARK, "lower_jaw": SKULL_DARK, "snout_ridge": BONE_DIM,
    "fang_upper_left": FANG, "fang_upper_right": FANG,
    "fang_lower_left": FANG, "fang_lower_right": FANG,
    "eye_left": EYE, "eye_right": EYE,
    # Horns: all one colour, because they are all the same thing pushing through
    "horn_head_left": BONE, "horn_head_right": BONE,
    "horn_shoulder_left": BONE, "horn_back": BONE,
    "horn_flank_right": BONE, "horn_arm_left": BONE, "horn_thigh_right": BONE,
}

GLOWING = {"eye_left": EYE_GLOW, "eye_right": EYE_GLOW}

# Parts painted with heavier noise: cloth and flesh both blotch, and flat brown at this scale reads as
# plastic. Bone and teeth stay clean, so the horns keep a hard edge against the robe they came
# through.
MOTTLED = {"body", "robe", "leg_left", "leg_right", "arm_left", "arm_right", "arms_folded",
           "head", "upper_jaw", "lower_jaw"}

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
    """Paints one box-UV face."""
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
        if not part["cubes"]:
            continue  # a container part (e.g. "arms") — a pivot with no geometry to paint
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
    skin.save(TEX_DIR / "infested_villager.png")
    glow.save(TEX_DIR / "infested_villager_glow.png")
    print(f"painted {painted} cubes onto {tex_w}x{tex_h}")
    print(f"wrote {TEX_DIR / 'infested_villager.png'}")
    print(f"wrote {TEX_DIR / 'infested_villager_glow.png'}")


if __name__ == "__main__":
    main()
