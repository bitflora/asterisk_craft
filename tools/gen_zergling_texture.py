#!/usr/bin/env python3
"""
Generate the Zergling entity textures: a flat, single-colour-per-zone skin whose regions match the cube
UV footprints declared in client/zerg/ZerglingModel.java. Because each zone is one solid colour, exact
UV alignment within a zone is forgiving and every cube of a given material simply points its texOffs at
that material's top-left corner.

The silhouette is the StarCraft Zerg Zergling: a low, crouched carapace bug with a fanged head, glowing
eyes, clawed limbs and raised back scythes. Zerg team colour is applied separately (dyed chestplate);
this skin is the purple-brown carapace / bone palette shared across the Zerg units.

Two files are written from one run:
    zergling.png       full skin (all zones painted)
    zergling_glow.png  emissive pass: transparent EVERYWHERE except the eye zone, so only the eyes glow
                       at full brightness via UnitGlowLayer

Zones (x0,y0)-(x1,y1) and their model texOffs (atlas is 128x64):
    CARAPACE (0,0)-(40,20)    purple-brown -> texOffs(0,0)    body, head, tail
    CLAW     (44,0)-(64,14)   bone         -> texOffs(44,0)   mandibles, front legs, back scythes
    DARK     (0,24)-(16,38)   dark flesh   -> texOffs(0,24)   hind legs
    EYE      (68,0)-(76,6)    orange       -> texOffs(68,0)   eyes                            (GLOWS)

Usage:
    python tools/gen_zergling_texture.py
Writes both PNGs under src/main/resources/assets/asteriskcraft/textures/entity/.
"""
from pathlib import Path

from PIL import Image

W, H = 128, 64

CLEAR = (0, 0, 0, 0)
CARAPACE = (128, 84, 96, 255)   # body, head, tail
CLAW = (208, 194, 168, 255)     # bone: mandibles, front legs, scythes
DARK = (66, 46, 60, 255)        # hind legs (dark flesh)
EYE = (150, 70, 20, 255)        # eye base (dim; the glow pass makes it light up)

# Emissive (glow-pass) colour — bright orange so the eyes pop at full brightness.
EYE_GLOW = (255, 140, 40, 255)

Z_CARAPACE = (0, 0, 40, 20)
Z_CLAW = (44, 0, 64, 14)
Z_DARK = (0, 24, 16, 38)
Z_EYE = (68, 0, 76, 6)


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
    _rect(px, Z_CARAPACE, CARAPACE)
    _rect(px, Z_CLAW, CLAW)
    _rect(px, Z_DARK, DARK)
    _rect(px, Z_EYE, EYE)
    _save(skin, "zergling.png")

    # --- Emissive glow pass: only the eye zone ---------------------------
    glow = Image.new("RGBA", (W, H), CLEAR)
    gpx = glow.load()
    _rect(gpx, Z_EYE, EYE_GLOW)
    _save(glow, "zergling_glow.png")


if __name__ == "__main__":
    main()
