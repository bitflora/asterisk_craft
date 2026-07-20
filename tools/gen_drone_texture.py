#!/usr/bin/env python3
"""
Generate the Drone entity textures: a flat, single-colour-per-zone skin whose regions match the cube
UV footprints declared in client/zerg/DroneModel.java. Because each zone is one solid colour, exact UV
alignment within a zone is forgiving and every cube of a given material simply points its texOffs at that
material's top-left corner.

The silhouette is the StarCraft Zerg Drone: a small hovering worker with a hunched segmented carapace,
a dorsal hump with orange spikes, a fanged head and grasping fore-claws. Zerg team colour is applied
separately (dyed chestplate); this skin is the purple-brown carapace / bone palette shared across the
Zerg units.

Two files are written from one run:
    drone.png       full skin (all zones painted)
    drone_glow.png  emissive pass: transparent EVERYWHERE except the spike/eye zone, so only the back
                    spikes and eye glow at full brightness via UnitGlowLayer

Zones (x0,y0)-(x1,y1) and their model texOffs (atlas is 128x64):
    CARAPACE (0,0)-(36,18)    purple-brown -> texOffs(0,0)    body, hump, head, tail
    CLAW     (44,0)-(60,12)   bone         -> texOffs(44,0)   mandibles, fore-claws
    SPINE    (68,0)-(78,10)   orange       -> texOffs(68,0)   back spikes, eye              (GLOWS)

Usage:
    python tools/gen_drone_texture.py
Writes both PNGs under src/main/resources/assets/asteriskcraft/textures/entity/.
"""
from pathlib import Path

from PIL import Image

W, H = 128, 64

CLEAR = (0, 0, 0, 0)
CARAPACE = (128, 84, 96, 255)   # body, hump, head, tail
CLAW = (208, 194, 168, 255)     # bone: mandibles, fore-claws
SPINE = (150, 70, 20, 255)      # spike/eye base (dim; the glow pass makes it light up)

# Emissive (glow-pass) colour — bright orange so the spikes and eye pop at full brightness.
SPINE_GLOW = (255, 140, 40, 255)

Z_CARAPACE = (0, 0, 36, 18)
Z_CLAW = (44, 0, 60, 12)
Z_SPINE = (68, 0, 78, 10)


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
    _rect(px, Z_SPINE, SPINE)
    _save(skin, "drone.png")

    # --- Emissive glow pass: only the spike/eye zone ---------------------
    glow = Image.new("RGBA", (W, H), CLEAR)
    gpx = glow.load()
    _rect(gpx, Z_SPINE, SPINE_GLOW)
    _save(glow, "drone_glow.png")


if __name__ == "__main__":
    main()
