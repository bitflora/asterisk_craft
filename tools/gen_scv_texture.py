#!/usr/bin/env python3
"""
Generate the SCV's entity textures.

Same approach as `gen_dark_templar_texture.py`: read the packed per-cube UV islands back out of
`build/model-export/scv.json` and paint each cube's six faces individually, shaded by face
direction. Output is a starting point, not a finished skin — the point is that
`tools/blockbench/scv.bbmodel` opens onto a correct layout with real artwork on it, so painting is
a paint job rather than a setup job. See docs/texturing.md.

Two files are written:
    scv.png       the skin — steel-white plate, dark violet trim, gold tools, a villager in the cab
    scv_glow.png  the emissive pass: ONLY the cockpit lamp and the cutter tip, everything else
                  transparent, since UnitGlowLayer re-submits the whole model

The palette is straight off the reference art: a pale steel hull with heavy violet armour on the
shoulder pods, thighs and feet, and gold reserved for the two things on the end of the booms.

Nothing here paints the pilot. The villager in the cab is vanilla's own baby villager head drawn on
its own texture by client/terran/ScvPilotLayer, so all this file does for the cockpit is darken the
hull's front face into a recess for it to sit in.

Usage:
    python tools/gen_scv_texture.py
Run it AFTER `./gradlew test` has refreshed the geometry dump, or the islands it paints will be the
ones from the previous layout.
"""
import json
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
DUMP = ROOT / "build" / "model-export" / "scv.json"
TEX_DIR = ROOT / "src/main/resources/assets/asteriskcraft/textures/entity"

CLEAR = (0, 0, 0, 0)

# --- palette ------------------------------------------------------------------------------------
HULL = (196, 198, 204)          # the pale steel the chassis is mostly made of
HULL_SHADOW = (150, 152, 160)   # recessed panels: the hips, the shins, the boom shafts
VIOLET = (92, 74, 118)          # the heavy armour: shoulder pods, thighs, feet
VIOLET_DARK = (64, 50, 86)      # canopy frame, so the cockpit opening is ringed in shadow
CAB_DARK = (54, 54, 62)         # the recess itself, behind the pilot
GOLD = (198, 158, 54)           # claw prongs and cutter barrel — the only warm notes on the hull
VENT = (72, 72, 80)             # rear heat vent

LAMP = (128, 196, 224)          # cockpit wash, base pass
LAMP_GLOW = (206, 240, 255)
TIP = (240, 196, 96)            # cutter tip, base pass
TIP_GLOW = (255, 244, 190)

PALETTE = {
    "chest": HULL,
    "chest_vent": VENT,
    "canopy_hood": VIOLET_DARK, "canopy_sill": VIOLET_DARK,
    "canopy_post_left": VIOLET_DARK, "canopy_post_right": VIOLET_DARK,
    "cockpit_lamp": LAMP,
    "shoulder_left": VIOLET, "shoulder_right": VIOLET,
    "arm_left": HULL_SHADOW, "claw_upper": GOLD, "claw_lower": GOLD,
    "arm_right": HULL_SHADOW, "cutter_barrel": GOLD, "cutter_tip": TIP,
    "hips": HULL_SHADOW,
    "thigh_left": VIOLET, "shin_left": HULL_SHADOW, "foot_left": VIOLET,
    "thigh_right": VIOLET, "shin_right": HULL_SHADOW, "foot_right": VIOLET,
}

# Which parts appear in the emissive pass, and in what colour. Deliberately two.
GLOWING = {"cockpit_lamp": LAMP_GLOW, "cutter_tip": TIP_GLOW}

# Per-face brightness. Box UV order is top, bottom, right(-x), front(-z), left(+x), back(+z).
FACE_SHADE = {"top": 1.30, "bottom": 0.55, "right": 0.86, "front": 1.06, "left": 0.86, "back": 0.78}


def shade(color, factor):
    return tuple(min(255, max(0, int(round(c * factor)))) for c in color)


def jitter(x, y, seed):
    """Deterministic +-3 per-texel noise, so flat plate reads as worn metal rather than plastic."""
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
        if not part["cubes"]:
            continue  # an empty container part (pilot_mount) has nothing to paint
        color = PALETTE.get(name)
        if color is None:
            unknown.append(name)
            continue
        for index, cube in enumerate(part["cubes"]):
            w, h, d = (int(math.ceil(v)) for v in cube["dimensions"])
            u, v = int(cube["texOffs"][0]), int(cube["texOffs"][1])
            seed = abs(hash((name, index))) % 9973
            for face, rect in faces(u, v, w, h, d).items():
                # The recess behind the pilot is painted onto the hull's own front face rather than
                # given a cube, so the cockpit reads as a hole without geometry to make one.
                fill = CAB_DARK if (name == "chest" and face == "front") else color
                paint_face(spx, rect, fill, FACE_SHADE[face], seed, tex_w, tex_h)
                if name in GLOWING:
                    # The emissive pass is flatter on purpose: a glow with strong directional
                    # shading stops looking like it emits its own light.
                    paint_face(gpx, rect, GLOWING[name],
                               0.85 + 0.15 * FACE_SHADE[face], seed + 1, tex_w, tex_h,
                               noise=False, rim=False)
            painted += 1

    if unknown:
        raise SystemExit(f"No palette entry for: {', '.join(sorted(set(unknown)))}")

    TEX_DIR.mkdir(parents=True, exist_ok=True)
    skin.save(TEX_DIR / "scv.png")
    glow.save(TEX_DIR / "scv_glow.png")
    print(f"painted {painted} cubes onto {tex_w}x{tex_h}")
    print(f"wrote {TEX_DIR / 'scv.png'}")
    print(f"wrote {TEX_DIR / 'scv_glow.png'}")


if __name__ == "__main__":
    main()
