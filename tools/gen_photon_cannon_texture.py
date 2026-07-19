#!/usr/bin/env python3
"""
Generate the Photon Cannon entity texture: a flat, single-colour-per-zone skin whose regions match
the cube UV footprints declared in client/PhotonCannonModel.java. Because each zone is one solid
colour, exact UV alignment within a zone is forgiving and cubes freely share texture offsets.

The disc base is 48px (3 blocks) across, so its cube unwraps are large — hence a 256x256 sheet.

Zones (256x256):
    disc  gold        (0,0)-(144,54)    all base cubes -> texOffs(0,0)
    globe bright cyan (0,60)-(88,88)    all globe cubes -> texOffs(0,60)

Usage:
    python tools/gen_photon_cannon_texture.py
Writes src/main/resources/assets/asteriskcraft/textures/entity/photon_cannon.png.
"""
from pathlib import Path

from PIL import Image

W = H = 256

# Protoss palette.
CLEAR = (0, 0, 0, 0)
GOLD = (198, 158, 78, 255)  # disc base
CYAN = (120, 235, 240, 255) # hovering energy globe


def main() -> None:
    img = Image.new("RGBA", (W, H), CLEAR)
    px = img.load()

    def rect(x0: int, y0: int, x1: int, y1: int, color) -> None:
        for y in range(y0, y1):
            for x in range(x0, x1):
                px[x, y] = color

    rect(0, 0, 144, 54, GOLD)    # disc base
    rect(0, 60, 88, 88, CYAN)    # hovering globe

    out = Path(__file__).resolve().parent.parent \
        / "src/main/resources/assets/asteriskcraft/textures/entity/photon_cannon.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out)
    print(f"wrote {out}")


if __name__ == "__main__":
    main()
