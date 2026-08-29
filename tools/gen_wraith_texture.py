#!/usr/bin/env python3
"""
Generate the Terran Wraith's entity textures.

Same approach as `gen_missile_turret_texture.py` and `gen_bunker_texture.py`: read the packed
per-cube UV islands back out of `build/model-export/wraith.json` and paint each cube's six faces
individually, shaded by face direction. Output is a starting point, not a finished skin — the point
is that `tools/blockbench/wraith.bbmodel` opens onto a correct layout with real artwork on it, so
painting is a paint job rather than a setup job. See docs/texturing.md.

Unlike the Missile Turret's, nothing on this model is borrowed: a Wraith is an aircraft, not a
villager or a golem wearing hardware, so there is no vanilla texture underneath it and every cube
here is the mod's to paint.

The palette comes off the reference art, which is a **slate blue-grey aircraft with warm brown
ordnance**, and it is split three ways on purpose so the silhouette still reads at the distance a
flyer is usually seen from:

    hull        the fuselage, nose and tail — the mid-tone the eye averages the aircraft to
    panel       wings and nacelles, a step darker, so the planform separates from the body
    ordnance    the two cannons, warm brown against all that blue — the art's one colour accent,
                and the part of the silhouette that says which way the aircraft is pointing

Two files are written:
    wraith.png       the whole model
    wraith_glow.png  the emissive pass: canopy, the two exhausts, and the muzzle end of each
                     cannon. Everything else stays transparent, since UnitGlowLayer re-submits the
                     whole model and anything opaque here would light up with it

Usage:
    python tools/gen_wraith_texture.py
Run it AFTER `./gradlew test` has refreshed the geometry dump, or the islands it paints will be the
ones from the previous layout.
"""
import json
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
DUMP = ROOT / "build" / "model-export" / "wraith.json"
TEX_DIR = ROOT / "src/main/resources/assets/asteriskcraft/textures/entity"

CLEAR = (0, 0, 0, 0)

# --- palette ------------------------------------------------------------------------------------
HULL = (104, 112, 136)       # slate blue-grey: fuselage, nose, tail plate
HULL_LIGHT = (132, 140, 164)  # the nose tip and the fins, so the ends of the aircraft catch light
PANEL = (78, 84, 104)        # wings and nacelles, a step darker than the hull
PANEL_DARK = (58, 62, 78)    # pylons and wingtips — the deepest tone, in shadow under the planform
CANOPY = (46, 62, 88)        # dark glass; the glow pass is what actually makes it read as lit
ORDNANCE = (112, 84, 60)     # warm brown, the art's one accent, on the two cannons
EXHAUST = (44, 44, 50)       # near-black metal, so the lit ring in the glow pass has something to sit in

CANOPY_GLOW = (128, 198, 255)   # cold cockpit light
EXHAUST_GLOW = (255, 156, 84)   # engine heat
MUZZLE_GLOW = (198, 236, 255)   # charged lasers, matching the ELECTRIC_SPARK beam the unit fires

PALETTE = {
    ("body", 0): HULL,
    ("nose", 0): HULL,
    ("noseTip", 0): HULL_LIGHT,
    ("canopy", 0): CANOPY,
    ("cannonL", 0): ORDNANCE,
    ("cannonR", 0): ORDNANCE,
    ("wingL", 0): PANEL,
    ("wingR", 0): PANEL,
    ("wingTipL", 0): PANEL_DARK,
    ("wingTipR", 0): PANEL_DARK,
    ("pylonL", 0): PANEL_DARK,
    ("pylonR", 0): PANEL_DARK,
    ("nacelleL", 0): PANEL,
    ("nacelleR", 0): PANEL,
    ("exhaustL", 0): EXHAUST,
    ("exhaustR", 0): EXHAUST,
    ("tail", 0): HULL,
    ("finL", 0): HULL_LIGHT,
    ("finR", 0): HULL_LIGHT,
}

# The emissive pass, keyed to its own colour per cube — three different lights on one aircraft.
GLOWING = {
    ("canopy", 0): CANOPY_GLOW,
    ("exhaustL", 0): EXHAUST_GLOW,
    ("exhaustR", 0): EXHAUST_GLOW,
    ("cannonL", 0): MUZZLE_GLOW,
    ("cannonR", 0): MUZZLE_GLOW,
}

# The cannons are nine blocks long and only their tips are charged, so lighting the whole barrel
# would draw two glowing rods across the sky. Only these faces of a cannon are lit — the front cap
# (its muzzle) and a strip of the sides, handled below.
MUZZLE_ONLY = {("cannonL", 0), ("cannonR", 0)}
# How far back from the muzzle the side glow runs, in texels.
MUZZLE_DEPTH = 2

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

    Everything but a cannon glows edge to edge. A cannon glows only at its muzzle: the whole front
    cap, and the last MUZZLE_DEPTH texels of the four long faces. The model's cannons run forward
    (-z), and a box-UV face is laid out with -z at its near edge, so the muzzle end of a side face
    is dx < MUZZLE_DEPTH on the top/bottom/side strips and the full rect on "front".
    """
    if key not in MUZZLE_ONLY:
        return (0, 0, fw, fh)
    if face == "front":
        return (0, 0, fw, fh)
    if face == "back":
        return None  # the breech end, buried in the hull
    return (0, 0, min(MUZZLE_DEPTH, fw), fh)


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
            continue  # containers have no cubes of their own
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
    skin.save(TEX_DIR / "wraith.png")
    glow.save(TEX_DIR / "wraith_glow.png")
    print(f"painted {painted} cubes onto {tex_w}x{tex_h}")
    print(f"wrote {TEX_DIR / 'wraith.png'}")
    print(f"wrote {TEX_DIR / 'wraith_glow.png'}")


if __name__ == "__main__":
    main()
