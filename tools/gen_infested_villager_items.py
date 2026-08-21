#!/usr/bin/env python3
"""
Generate the Infested Villager's two spawn-egg icons.

Same recolour trick as `gen_lurker_items.py`, and for the same reason: the spawn eggs are a *set*.
One egg shape and one speckle mask are shared across the whole mod; what varies is the base colour
(per unit) and the spot colour (per faction — blue for an ally, dark red for an enemy). So this
remaps the Zergling's egg onto this unit's ramp, preserving shading, outline and every faction spot
exactly where it was, rather than drawing a new egg that would be the odd one out in the creative tab.

The Zergling's is the right one to start from — it is the other plain Zerg ground unit, so the two
eggs sit side by side in the tab. The ramp is this unit's own: rust-orange flesh at the bottom rising
to the bone yellow of its spikes, the same two colours
`tools/gen_infested_villager_texture.py` paints the model in, so the egg reads as the thing it hatches.

No GUI production icon is generated, and for a stronger reason than the other Zerg units: this one is
never trained *at all*, by either side. `combat/InfestationHandler` is its only producer.

Usage:
    python tools/gen_infested_villager_items.py
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

# The egg's base ramp: the model's own carapace red-brown rising to the bone yellow of its spikes.
EGG_DARK = (64, 29, 23)
EGG_LIGHT = (214, 186, 104)


def luminance(px):
    return (px[0] * 299 + px[1] * 587 + px[2] * 114) / 255000.0


def recolour_egg(source: Path, out: Path):
    """Remap the egg's base ramp onto this unit's, preserving shading, outline and speckles."""
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
    recolour_egg(items / "zergling_spawn_egg_ally.png",
                 items / "infested_villager_spawn_egg_ally.png")
    recolour_egg(items / "zergling_spawn_egg_enemy.png",
                 items / "infested_villager_spawn_egg_enemy.png")


if __name__ == "__main__":
    main()
