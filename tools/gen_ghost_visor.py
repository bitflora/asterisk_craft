#!/usr/bin/env python3
"""
Generate `ghost_visor.png` — the Ghost's three glowing green eyeholes.

Unlike every other texture generator in this directory, this one paints onto **vanilla's** UV
layout, not the mod's. `client/terran/GhostHeadLayer` borrows vanilla's villager head and paints
vanilla's armorer mask and cartographer eyepiece onto it; this file is a fourth draw on that same
baked part, so its 64x64 canvas is vanilla's villager texture layout and every coordinate below is a
texel of `minecraft:textures/entity/villager/villager.png`.

That layer draws it twice — once cut out, so the pixels are solid green over the eyepiece's opaque
blue lens, then once through `RenderTypes.eyes`, so they ignore world light. One file, because they
are the same six pixels.

Where the numbers come from (verified against the 26.1.2 client jar, and against
`VillagerModel.createBodyModel` for the geometry):

    hat cube      texOffs(32, 0), 8x10x8  ->  front face occupies u 40..47, v 8..17
    mask slits    armorer.png is TRANSPARENT at (41,10) (42,10) and (45,10) (46,10) — the two
                  holes a welder looks through, sitting above the villager's own eyes because
                  the mask is a raised visor
    eyepiece lens cartographer.png is opaque 97caf6 / 6a91db at (45,14) (46,14), inside a gold
                  frame, landing squarely on the villager's right eye

Nothing enforces those coordinates, so if vanilla ever retouches either profession texture, re-check
them by dumping the alpha of `profession/armorer.png` and `profession/cartographer.png` over
u 40..47, v 8..17 rather than by eye in-game.

Usage:
    python tools/gen_ghost_visor.py
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
TEX_DIR = ROOT / "src/main/resources/assets/asteriskcraft/textures/entity"

# Vanilla's villager texture size — this file is laid out on vanilla's UVs, not the mod's.
SIZE = (64, 64)

# Saturated and slightly cold: it has to still read green through the `eyes` pass, which brightens
# everything it draws, and a warmer green comes out yellow there.
GREEN = (43, 255, 106, 255)

# The three holes, as (u, v) texels on the hat cube's front face.
EYEHOLES = [
    (41, 10), (42, 10),   # welding mask, left slit
    (45, 10), (46, 10),   # welding mask, right slit
    (45, 14), (46, 14),   # cartographer lens
]


def main():
    visor = Image.new("RGBA", SIZE, (0, 0, 0, 0))
    px = visor.load()
    for x, y in EYEHOLES:
        px[x, y] = GREEN

    TEX_DIR.mkdir(parents=True, exist_ok=True)
    out = TEX_DIR / "ghost_visor.png"
    visor.save(out)
    print(f"wrote {out} ({len(EYEHOLES)} lit texels)")


if __name__ == "__main__":
    main()
