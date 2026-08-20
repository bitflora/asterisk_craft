#!/usr/bin/env python3
"""
Generate the Spore Colony's two spawn-egg icons.

Same recolour trick as `gen_dark_templar_items.py`, and for the same reason: the spawn eggs are a
*set*. One egg shape and one speckle mask are shared across the whole mod; what varies is the base
colour (per unit) and the spot colour (per faction — blue for an ally, dark red for an enemy). So this
remaps the Sunken Colony's egg onto the Spore's ramp, preserving shading, outline and every faction
spot exactly where it was, rather than drawing a new egg that would be the odd one out in the
creative tab.

No GUI production icon is generated: unlike a Gateway unit, a Spore Colony is never trained from a
queue — one is pre-placed beside each Hive by `game/GameBootstrap`.

Usage:
    python tools/gen_spore_colony_items.py
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
TEX = ROOT / "src/main/resources/assets/asteriskcraft/textures"

# Faction speckle colours, shared by every spawn egg in the mod. Left untouched by the recolour.
SPOTS = {
    (30, 75, 186, 255), (26, 63, 157, 255),      # ally  (Protoss blue)
    (124, 14, 43, 255), (95, 11, 33, 255),       # enemy (Zerg red)
}

# The Spore Colony's egg base: the body's purple in shadow rising to the chimney's orange, so the
# egg carries the creature's two-colour read at a glance in the creative tab.
EGG_DARK = (48, 26, 60)
EGG_LIGHT = (206, 108, 58)


def luminance(px):
    return (px[0] * 299 + px[1] * 587 + px[2] * 114) / 255000.0


def recolour_egg(source: Path, out: Path):
    """Remap the egg's base ramp onto the Spore's, preserving shading, outline and speckles."""
    im = Image.open(source).convert("RGBA")
    px = im.load()
    w, h = im.size
    for y in range(h):
        for x in range(w):
            c = px[x, y]
            if c[3] == 0 or c in SPOTS:
                continue  # transparent, or a faction spot that must survive verbatim
            # Map the original's brightness onto the new ramp, so highlights stay highlights and the
            # dark outline stays an outline — the egg keeps its shape, only its hue changes.
            t = luminance(c)
            px[x, y] = (
                int(EGG_DARK[0] + (EGG_LIGHT[0] - EGG_DARK[0]) * t),
                int(EGG_DARK[1] + (EGG_LIGHT[1] - EGG_DARK[1]) * t),
                int(EGG_DARK[2] + (EGG_LIGHT[2] - EGG_DARK[2]) * t),
                c[3],
            )
    im.save(out)
    print(f"wrote {out}")


def main():
    items = TEX / "item"
    recolour_egg(items / "sunken_colony_spawn_egg_ally.png", items / "spore_colony_spawn_egg_ally.png")
    recolour_egg(items / "sunken_colony_spawn_egg_enemy.png", items / "spore_colony_spawn_egg_enemy.png")


if __name__ == "__main__":
    main()
