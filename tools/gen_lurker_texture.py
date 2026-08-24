#!/usr/bin/env python3
"""
Generate the Lurker's entity textures.

Same approach as `gen_spore_colony_texture.py`: read the packed per-cube UV islands back out of
`build/model-export/lurker.json` and paint each cube's six faces individually, shaded by face
direction. Output is a starting point, not a finished skin — the point is that
`tools/blockbench/lurker.bbmodel` opens onto a correct layout with real artwork on it, so painting is
a paint job rather than a setup job. See docs/texturing.md.

Two files are written:
    lurker.png       the skin — Hydralisk browns over pale bone plating
    lurker_glow.png  the emissive pass: the eyes and the tips of the back and crest spines only,
                     everything else transparent, since UnitGlowLayer re-submits the whole model

The palette is sampled from `hydralisk.png` rather than invented, because the two are meant to read
as the same animal: a warm mid-brown hide (the most common texel in that skin is about 90,47,26),
near-black chitin in the creases, and the pale tan (170,122,68) that its hood and bone plating are
painted in. The glow colours come the same way, off `hydralisk_glow.png`.

Two things about this unit specifically drive the choices below:

* **The spines are the burrowed silhouette.** They are the only part of the model a dug-in Lurker
  shows, so they get the bone tan rather than the hide brown and their tips are the brightest thing
  in the emissive pass — a buried one has to read as a row of lit spikes and nothing else.
* **The legs need to separate from the body.** Eight of them cross in front of a body painted in a
  near neighbouring colour, and at 16px a leg in the hide brown disappears into the abdomen behind
  it. The femurs are darker than the body and the shins lighter, so a leg always has contrast against
  whatever it crosses.

Usage:
    python tools/gen_lurker_texture.py
Run it AFTER `./gradlew test` has refreshed the geometry dump, or the islands it paints will be the
ones from the previous layout.
"""
import json
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
DUMP = ROOT / "build" / "model-export" / "lurker.json"
TEX_DIR = ROOT / "src/main/resources/assets/asteriskcraft/textures/entity"

CLEAR = (0, 0, 0, 0)

# --- palette ------------------------------------------------------------------------------------
# The whole animal is brown: a mottled hide over darker chitin, with the plating a lighter tan-brown
# rather than the bone white the Hydralisk carries. Sampled from hydralisk.png, since the Lurker is
# the same animal on a spider's frame — the most common texel in that skin is about 90,47,26.
HIDE = (92, 48, 27)          # the soft body, the commonest colour in the Hydralisk's skin
HIDE_DARK = (57, 28, 17)     # creases and the plating that sits in shadow
PLATE = (134, 90, 50)        # the lighter tan-brown of the hood, the brow and every spine
PLATE_DIM = (108, 71, 39)    # plate in shadow — the snout ridge and the mandibles
CHITIN = (68, 34, 20)        # legs: darker than the body, so eight of them read against it
FANG = (176, 140, 96)        # teeth, the palest thing on the model and still a brown
EYE = (255, 224, 160)        # matches the Hydralisk's lit eye

# Emissive pass, off hydralisk_glow.png: a hot amber at the spine tips, a paler one in the eyes.
EYE_GLOW = (255, 224, 160)
SPINE_GLOW = (255, 144, 65)

PALETTE = {
    # Body: vanilla's spider cephalothorax and abdomen
    "thorax": HIDE, "abdomen": HIDE,
    # The rack, and the crest that sits with it above the surface when the unit is burrowed
    "spine1": PLATE, "spine2": PLATE, "spine3": PLATE, "spine4": PLATE,
    "spine5": PLATE, "spine6": PLATE,
    "crest_spine_left": PLATE, "crest_spine_right": PLATE,
    # Head — the Hydralisk's, transplanted onto the spider's head pivot
    "head": HIDE,
    "brow_ridge": PLATE, "hood": PLATE,
    "hood_horn_left": PLATE, "hood_horn_right": PLATE,
    "upper_jaw": HIDE, "lower_jaw": HIDE, "snout_ridge": PLATE_DIM,
    "fang_upper_left": FANG, "fang_upper_right": FANG,
    "fang_lower_left": FANG, "fang_lower_right": FANG,
    "mandible_left": PLATE_DIM, "mandible_right": PLATE_DIM,
    "eye_left": EYE, "eye_right": EYE,
    # Legs: vanilla's eight, darker than the body they cross so none of them vanishes against it
    "right_hind_leg": CHITIN, "left_hind_leg": CHITIN,
    "right_middle_hind_leg": CHITIN, "left_middle_hind_leg": CHITIN,
    "right_middle_front_leg": CHITIN, "left_middle_front_leg": CHITIN,
    "right_front_leg": CHITIN, "left_front_leg": CHITIN,
}

# Which parts appear in the emissive pass, and in what colour. Kept to the eyes and the spikes that
# stay above ground when the unit burrows — that fringe of light is what a buried Lurker *is*.
GLOWING = {
    "eye_left": EYE_GLOW, "eye_right": EYE_GLOW,
    "spine1": SPINE_GLOW, "spine2": SPINE_GLOW, "spine3": SPINE_GLOW,
    "spine4": SPINE_GLOW, "spine5": SPINE_GLOW, "spine6": SPINE_GLOW,
    "crest_spine_left": SPINE_GLOW, "crest_spine_right": SPINE_GLOW,
}

# Only the top few texels of a spine glow, so it lights at the tip rather than along its whole
# length. Faces shorter than this are lit throughout.
GLOW_TIP_TEXELS = 3

# Parts painted with heavier noise — which here is most of the animal, because "mottled brown" is
# the whole colour scheme and flat brown reads as plastic at 16px. Only the teeth and the eyes stay
# clean, since a blotchy tooth stops reading as a tooth.
MOTTLED = {
    "thorax", "abdomen", "head", "upper_jaw", "lower_jaw",
    "hood", "hood_horn_left", "hood_horn_right", "brow_ridge", "snout_ridge",
    "mandible_left", "mandible_right",
    "right_hind_leg", "left_hind_leg",
    "right_middle_hind_leg", "left_middle_hind_leg",
    "right_middle_front_leg", "left_middle_front_leg",
    "right_front_leg", "left_front_leg",
}

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


def paint_face(px, rect, color, factor, seed, tex_w, tex_h, amplitude=3, rows=None):
    """
    Paints one box-UV face. `rows` limits painting to the first N texel rows of the face, which is
    how a spine's glow is confined to its tip: on a side face the model's "up" runs down the
    rectangle from its top edge, so the first rows are the end of the spike.
    """
    x0, y0, fw, fh = rect
    limit = fh if rows is None else min(fh, rows)
    base = shade(color, factor)
    for dy in range(limit):
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
        # An eye is a lamp, not a spike: light all of it, not just its first rows.
        tip_only = name not in ("eye_left", "eye_right")
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
                rows = GLOW_TIP_TEXELS if (tip_only and face not in ("top", "bottom")) else None
                paint_face(gpx, rect, GLOWING[name], 0.85 + 0.15 * FACE_SHADE[face],
                           seed + 1, tex_w, tex_h, 2, rows=rows)
            painted += 1

    if unknown:
        raise SystemExit(f"No palette entry for: {', '.join(sorted(set(unknown)))}")

    TEX_DIR.mkdir(parents=True, exist_ok=True)
    skin.save(TEX_DIR / "lurker.png")
    glow.save(TEX_DIR / "lurker_glow.png")
    print(f"painted {painted} cubes onto {tex_w}x{tex_h}")
    print(f"wrote {TEX_DIR / 'lurker.png'}")
    print(f"wrote {TEX_DIR / 'lurker_glow.png'}")


if __name__ == "__main__":
    main()
