#!/usr/bin/env python3
"""
Generate the Zealot entity textures: a flat, single-colour-per-zone skin whose regions match the cube
UV footprints declared in client/protoss/ZealotModel.java. Because each zone is one solid colour, exact
UV alignment within a zone is forgiving and every cube of a given material simply points its texOffs at
that material's top-left corner.

The silhouette is the StarCraft Protoss Zealot: a broad gold-armoured biped with a helmeted head, two
swept-back head tendrils, a waist tasset, and the signature twin forearm psi-blades. Protoss team colour
is applied separately (dyed chestplate); this skin is the gold/blue Protoss palette shared with the
Dragoon and Photon Cannon.

Two files are written from one run:
    zealot.png       full skin (all zones painted)
    zealot_glow.png  emissive pass: transparent EVERYWHERE except the blade/visor zone, so only the
                     psi-blades and eye visor glow at full brightness via UnitGlowLayer

Zones (x0,y0)-(x1,y1) and their model texOffs (atlas is 128x128):
    GOLD   (0,0)-(60,44)     gold armour  -> texOffs(0,0)    head, body, arms, tendrils
    BLADE  (70,0)-(110,20)   psi-energy   -> texOffs(70,0)   forearm blades, eye visor         (GLOWS)
    DARK   (0,50)-(50,90)    dark metal   -> texOffs(0,50)   legs, waist tasset

Usage:
    python tools/gen_zealot_texture.py
Writes both PNGs under src/main/resources/assets/asteriskcraft/textures/entity/.
"""
from pathlib import Path

from PIL import Image

W = H = 128

CLEAR = (0, 0, 0, 0)
GOLD = (198, 158, 78, 255)     # armour: head, body, arms, tendrils
DARK = (48, 46, 60, 255)       # legs, waist tasset (near-black gunmetal)
BLADE = (40, 110, 140, 255)    # psi-blade / visor base (dim; the glow pass makes it light up)

# Emissive (glow-pass) colour — bright cyan so the psi-blades and eyes pop at full brightness.
BLADE_GLOW = (120, 235, 255, 255)

Z_GOLD = (0, 0, 60, 44)
Z_BLADE = (70, 0, 110, 20)
Z_DARK = (0, 50, 50, 90)


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
    _rect(px, Z_BLADE, BLADE)
    _save(skin, "zealot.png")

    # --- Emissive glow pass: only the blade/visor zone -------------------
    glow = Image.new("RGBA", (W, H), CLEAR)
    gpx = glow.load()
    _rect(gpx, Z_BLADE, BLADE_GLOW)
    _save(glow, "zealot_glow.png")


if __name__ == "__main__":
    main()
