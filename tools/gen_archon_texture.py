#!/usr/bin/env python3
"""
Generate the Archon's entity textures.

Same shape as `gen_dark_templar_texture.py` — read the packed layout back out of
`build/model-export/archon.json` and paint each cube's six faces individually — with one inversion
that is the whole point of this unit and is easy to undo by accident.

The Archon is two things drawn from two textures:

  * The **figure** (skull, torso, arms, legs) is painted into `archon.png` and is *absent* from
    `archon_glow.png`, except for the eyes.
  * The **ball of light** (the four `orb*` parts) is the other way round: it is painted into
    `archon_glow.png` and must be left **fully transparent in `archon.png`**. `MobRenderer`'s body
    pass is a cutout, so it discards those texels outright; `UnitGlowLayer` then re-submits the whole
    model against the glow texture with an additive, full-bright render type, and the shells appear
    as a glowing sphere with the figure inside it.

Painting an `orb*` island into `archon.png` turns the ball into an opaque crate around the figure.
That is the one mistake to watch for, and it is why the two passes are driven off separate tables
here (`PALETTE` and `GLOWING`) with the orb parts appearing in only one of them.

The glow shells are also painted with **falling alpha from the core outward**, which the other units'
glow textures have no need for: three nested additive shells at full alpha stack into a white blob,
while a bright core fading to a faint rim reads as a ball of light.

Output is a starting point, not a finished skin: the point is that `tools/blockbench/archon.bbmodel`
opens onto a correct layout with real artwork on it. See docs/texturing.md.

Usage:
    python tools/gen_archon_texture.py
Run it AFTER `./gradlew test` has refreshed the geometry dump, or the islands it paints will be the
ones from the previous layout.
"""
import json
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
DUMP = ROOT / "build" / "model-export" / "archon.json"
TEX_DIR = ROOT / "src/main/resources/assets/asteriskcraft/textures/entity"

CLEAR = (0, 0, 0, 0)

# --- palette ------------------------------------------------------------------------------------
# The figure is bare, not armoured: an Archon is two Templar burned down to energy, so there is no
# gold plate to paint. Everything on it is skin, and the skin is lit from inside by the ball it hangs
# in — hence the blue cast rather than the Zealot's warm flesh.
SKIN = (108, 122, 158)           # torso, arms, legs: pale blue-grey Protoss flesh
SKIN_LIT = (128, 144, 182)       # forearms, shins, skull — the parts nearest the shell
SKIN_DEEP = (78, 90, 122)        # face recess, so the head reads as tapering rather than as a box
CORD = (86, 96, 128)             # nerve cords trailing off the skull
EYE = (226, 240, 255)            # lit apertures

# Emissive pass. The eyes burn white; the shells run from a hot core out to a faint rim.
EYE_GLOW = (255, 255, 255)
ORB_CORE = (206, 232, 255)       # innermost shell: nearly white
ORB_MID = (128, 186, 255)        # the two stretched shells: the blue the reference art is built on
ORB_RIM = (86, 140, 236)         # outermost reach: deeper blue, so the ball has an edge

# Which cubes appear in the body pass, and in what colour. The orb parts are deliberately ABSENT.
PALETTE = {
    "head": SKIN_LIT, "face": SKIN_DEEP, "eyeL": EYE, "eyeR": EYE,
    "braidL": CORD, "braidR": CORD,
    "body": SKIN,
    "armL": SKIN, "armR": SKIN, "foreArmL": SKIN_LIT, "foreArmR": SKIN_LIT,
    "legL": SKIN, "legR": SKIN, "shinL": SKIN_LIT, "shinR": SKIN_LIT,
}

# Which cubes appear in the emissive pass, in what colour, and at what alpha. Alpha falls outward:
# three shells stacked at full opacity add up to a featureless white blob, while a bright core behind
# a translucent rim is what actually reads as a ball of light.
GLOWING = {
    "eyeL": (EYE_GLOW, 255),
    "eyeR": (EYE_GLOW, 255),
    "orb": (ORB_CORE, 210),      # the 16-cube core
    "orbWide": (ORB_MID, 150),
    "orbTall": (ORB_MID, 150),
    "orbDeep": (ORB_RIM, 120),
}

# The parts that must never reach the body texture, checked rather than assumed: a stray palette
# entry here is the failure that turns the ball into a crate.
ORB_PARTS = {"orb", "orbWide", "orbTall", "orbDeep"}

# Per-face brightness. Box UV order is top, bottom, right(-x), front(-z), left(+x), back(+z).
FACE_SHADE = {"top": 1.30, "bottom": 0.55, "right": 0.86, "front": 1.06, "left": 0.86, "back": 0.78}

# The shells get a much flatter version of the same: a glow with strong directional shading stops
# looking like it emits its own light, and on a sphere it reads as a lit cube instead.
ORB_FACE_SHADE = {k: 0.94 + 0.06 * v for k, v in FACE_SHADE.items()}


def shade(color, factor):
    return tuple(min(255, max(0, int(round(c * factor)))) for c in color)


def jitter(x, y, seed):
    """Deterministic +-3 per-texel noise, so a flat fill reads as material rather than plastic."""
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


def paint_face(px, rect, color, factor, seed, tex_w, tex_h, alpha=255, rim=True):
    x0, y0, fw, fh = rect
    base = shade(color, factor)
    for dy in range(fh):
        for dx in range(fw):
            x, y = x0 + dx, y0 + dy
            if not (0 <= x < tex_w and 0 <= y < tex_h):
                continue
            n = jitter(x, y, seed)
            # A darker rim around every face gives each cube its own edge at Minecraft's scale, where
            # an unbroken flat colour makes neighbouring cubes melt into one another. The shells skip
            # it: an outline is the one thing a ball of light must not have.
            edge = 0.74 if (rim and (dx == 0 or dy == 0 or dx == fw - 1 or dy == fh - 1)) else 1.0
            px[x, y] = (
                min(255, max(0, int(base[0] * edge) + n)),
                min(255, max(0, int(base[1] * edge) + n)),
                min(255, max(0, int(base[2] * edge) + n)),
                alpha,
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
        if name in ORB_PARTS and name in PALETTE:
            raise SystemExit(f"'{name}' is a shell of the ball of light and must stay transparent in"
                             " archon.png — remove it from PALETTE. See the module docstring.")
        if name not in PALETTE and name not in GLOWING:
            unknown.append(name)
            continue
        for index, cube in enumerate(part["cubes"]):
            w, h, d = (int(math.ceil(v)) for v in cube["dimensions"])
            u, v = int(cube["texOffs"][0]), int(cube["texOffs"][1])
            seed = abs(hash((name, index))) % 9973
            is_orb = name in ORB_PARTS
            for face, rect in faces(u, v, w, h, d).items():
                if name in PALETTE:
                    paint_face(spx, rect, PALETTE[name], FACE_SHADE[face], seed, tex_w, tex_h)
                if name in GLOWING:
                    color, alpha = GLOWING[name]
                    paint_face(gpx, rect, color,
                               ORB_FACE_SHADE[face] if is_orb else 0.85 + 0.15 * FACE_SHADE[face],
                               seed + 1, tex_w, tex_h, alpha=alpha, rim=not is_orb)
            painted += 1

    if unknown:
        raise SystemExit(f"No palette entry for: {', '.join(sorted(set(unknown)))}")

    TEX_DIR.mkdir(parents=True, exist_ok=True)
    skin.save(TEX_DIR / "archon.png")
    glow.save(TEX_DIR / "archon_glow.png")
    print(f"painted {painted} cubes onto {tex_w}x{tex_h}")
    print(f"wrote {TEX_DIR / 'archon.png'}")
    print(f"wrote {TEX_DIR / 'archon_glow.png'}")


if __name__ == "__main__":
    main()
