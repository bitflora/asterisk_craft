#!/usr/bin/env python3
"""
Generate the Photon Cannon entity textures: a flat, single-colour-per-zone skin whose regions match
the cube UV footprints declared in client/PhotonCannonModel.java. Because each zone is one solid
colour, exact UV alignment within a zone is forgiving and cubes freely share texture offsets — every
cube of a given material simply points its texOffs at that material's top-left corner.

The silhouette is the StarCraft II Photon Cannon: a golden star base of raised armour petals, a
forward-tilted drum with a glowing blue lens "eye", and a domed gold head with two glowing purple
eyes and blue accent panels. The disc/petal cubes unwrap large, hence a 256x256 sheet.

Two files are written from one run:
    photon_cannon.png       full skin (all zones painted)
    photon_cannon_glow.png  emissive pass: transparent EVERYWHERE except the lens/accent (blue) and
                            eye (purple) zones, so only those glow at full brightness via UnitGlowLayer

Zones (x0,y0)-(x1,y1) and their model texOffs:
    GOLD    (0,0)-(160,64)     gold armour  -> texOffs(0,0)     petals, hub, drum ring, neck, dome
    DARK    (170,0)-(230,44)   near-black   -> texOffs(170,0)   head band, drum interior
    BLUE    (0,120)-(48,164)   energy blue  -> texOffs(0,120)   lens disc, blue accent panels  (GLOWS)
    PURPLE  (110,120)-(150,152) violet      -> texOffs(110,120) head eyes                       (GLOWS)

Usage:
    python tools/gen_photon_cannon_texture.py
Writes both PNGs under src/main/resources/assets/asteriskcraft/textures/entity/.
"""
from pathlib import Path

from PIL import Image

W = H = 256

# Protoss palette.
CLEAR = (0, 0, 0, 0)
GOLD = (198, 158, 78, 255)    # armour: petals, hub, drum ring, neck, dome
DARK = (30, 28, 36, 255)      # head band + drum interior (near-black, faint blue)
BLUE = (92, 200, 245, 255)    # lens + accents (base skin)
PURPLE = (150, 92, 220, 255)  # head eyes (base skin)

# Emissive (glow-pass) colours — brighter so the lit parts pop against the dark.
BLUE_GLOW = (150, 240, 255, 255)
PURPLE_GLOW = (198, 130, 255, 255)

# Zone rectangles (x0, y0, x1, y1). texOffs in the model points at each zone's (x0, y0).
Z_GOLD = (0, 0, 160, 64)
Z_DARK = (170, 0, 230, 44)
Z_BLUE = (0, 120, 48, 164)
Z_PURPLE = (110, 120, 150, 152)


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
    _rect(px, Z_DARK, DARK)
    _rect(px, Z_BLUE, BLUE)
    _rect(px, Z_PURPLE, PURPLE)
    _save(skin, "photon_cannon.png")

    # --- Emissive glow pass: only lens/accents (blue) and eyes (purple) --
    glow = Image.new("RGBA", (W, H), CLEAR)
    gpx = glow.load()
    _rect(gpx, Z_BLUE, BLUE_GLOW)
    _rect(gpx, Z_PURPLE, PURPLE_GLOW)
    _save(glow, "photon_cannon_glow.png")


if __name__ == "__main__":
    main()
