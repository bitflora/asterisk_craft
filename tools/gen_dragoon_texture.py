#!/usr/bin/env python3
"""
Generate the Dragoon entity textures: a flat, single-colour-per-zone skin whose regions match the
cube UV footprints declared in client/DragoonModel.java. Because each zone is one solid colour, exact
UV alignment within a zone is forgiving and cubes freely share texture offsets — every cube of a given
material simply points its texOffs at that material's top-left corner.

The silhouette is the StarCraft Dragoon: a squat gold-armoured body pod with a blue energy core, riding
high on four arched spider legs, with a domed cockpit holding a single glowing orange-red eye.

Two files are written from one run:
    dragoon.png       full skin (all zones painted)
    dragoon_glow.png  emissive pass: transparent EVERYWHERE except the eye zone, so only the eye glows
                      at full brightness via UnitGlowLayer

Zones (x0,y0)-(x1,y1) and their model texOffs:
    GOLD  (0,0)-(128,60)     gold armour  -> texOffs(0,0)    hull, back hump, cockpit dome
    BLUE  (0,64)-(56,110)    energy blue  -> texOffs(0,64)   core underbelly
    DARK  (60,64)-(104,110)  dark metal   -> texOffs(60,64)  legs, feet, eye socket
    EYE   (104,64)-(124,90)  orange       -> texOffs(104,64) cockpit eye                     (GLOWS)

Usage:
    python tools/gen_dragoon_texture.py
Writes both PNGs under src/main/resources/assets/asteriskcraft/textures/entity/.
"""
from pathlib import Path

from PIL import Image

W = H = 128

# Protoss/Dragoon palette (gold + blue reused from the Photon Cannon for a consistent Protoss look).
CLEAR = (0, 0, 0, 0)
GOLD = (198, 158, 78, 255)    # armour: hull, back hump, cockpit dome
BLUE = (58, 104, 196, 255)    # energy core underbelly
DARK = (40, 40, 50, 255)      # legs, feet, eye socket (near-black gunmetal)
EYE = (120, 50, 20, 255)      # cockpit eye (base skin — dim; the glow pass makes it light up)

# Emissive (glow-pass) colour — bright orange-red so the eye pops at full brightness.
EYE_GLOW = (255, 120, 40, 255)

# Zone rectangles (x0, y0, x1, y1). texOffs in the model points at each zone's (x0, y0).
Z_GOLD = (0, 0, 128, 60)
Z_BLUE = (0, 64, 56, 110)
Z_DARK = (60, 64, 104, 110)
Z_EYE = (104, 64, 124, 90)


def _rect(px, box, color) -> None:
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        for x in range(x0, x1):
            px[x, y] = color


def _save(img: Image.Image, name: str) -> None:
    out = Path(__file__).resolve().parent.parent \
        / "src/main/resources/assets/asteriskcraft/textures/entity" / name
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out)
    print(f"wrote {out}")


def main() -> None:
    # --- Full skin -------------------------------------------------------
    skin = Image.new("RGBA", (W, H), CLEAR)
    px = skin.load()
    _rect(px, Z_GOLD, GOLD)
    _rect(px, Z_BLUE, BLUE)
    _rect(px, Z_DARK, DARK)
    _rect(px, Z_EYE, EYE)
    _save(skin, "dragoon.png")

    # --- Emissive glow pass: only the eye zone --------------------------
    glow = Image.new("RGBA", (W, H), CLEAR)
    gpx = glow.load()
    _rect(gpx, Z_EYE, EYE_GLOW)
    _save(glow, "dragoon_glow.png")


if __name__ == "__main__":
    main()
