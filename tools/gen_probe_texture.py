#!/usr/bin/env python3
"""
Generate the Probe entity textures: a flat, single-colour-per-zone skin whose regions match the cube UV
footprints declared in client/protoss/ProbeModel.java. Because each zone is one solid colour, exact UV
alignment within a zone is forgiving and every cube of a given material simply points its texOffs at that
material's top-left corner.

The silhouette is the StarCraft Protoss Probe: a small hovering worker pod with a domed top, a glowing
cyan sensor eye and three little leg-prongs. Protoss team colour is applied separately (dyed chestplate);
this skin is the gold/blue Protoss palette shared with the Dragoon and Photon Cannon.

Two files are written from one run:
    probe.png       full skin (all zones painted)
    probe_glow.png  emissive pass: transparent EVERYWHERE except the eye zone, so only the sensor eye
                    glows at full brightness via UnitGlowLayer

Zones (x0,y0)-(x1,y1) and their model texOffs (atlas is 64x64):
    GOLD  (0,0)-(32,20)     gold hull  -> texOffs(0,0)    body, dome
    DARK  (0,24)-(24,44)    dark metal -> texOffs(0,24)   under-pod, prongs, fins
    EYE   (40,0)-(52,10)    cyan       -> texOffs(40,0)   sensor eye                     (GLOWS)

Usage:
    python tools/gen_probe_texture.py
Writes both PNGs under src/main/resources/assets/asteriskcraft/textures/entity/.
"""
from pathlib import Path

from PIL import Image

W = H = 64

CLEAR = (0, 0, 0, 0)
GOLD = (198, 158, 78, 255)     # hull: body, dome
DARK = (48, 46, 60, 255)       # under-pod, prongs, fins (near-black gunmetal)
EYE = (40, 110, 140, 255)      # sensor eye base (dim; the glow pass makes it light up)

# Emissive (glow-pass) colour — bright cyan so the sensor eye pops at full brightness.
EYE_GLOW = (120, 235, 255, 255)

Z_GOLD = (0, 0, 32, 20)
Z_DARK = (0, 24, 24, 44)
Z_EYE = (40, 0, 52, 10)


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
    _rect(px, Z_EYE, EYE)
    _save(skin, "probe.png")

    # --- Emissive glow pass: only the eye zone ---------------------------
    glow = Image.new("RGBA", (W, H), CLEAR)
    gpx = glow.load()
    _rect(gpx, Z_EYE, EYE_GLOW)
    _save(glow, "probe_glow.png")


if __name__ == "__main__":
    main()
