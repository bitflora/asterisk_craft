#!/usr/bin/env python3
"""
Turn StarCraft's extracted command-card icons into the mod's three icon surfaces.

`tools/grp_extract.py` writes `import/command_icons/<name>.png` for the 31 frames of
`import/cmdicons.grp` it can name. This script is the other half: it crops, resizes and recolours
those into the textures the game actually loads, so all three places the mod shows a unit or
building come from one set of art instead of the mixed bag of screenshots, hand-drawn 16x16s and
recoloured egg sprites they used to be:

  * train buttons        textures/gui/icons/<unit>.png              (see building/ProductionKind)
  * building kit items   textures/item/<building>_kit.png
  * spawn eggs           textures/item/<unit>_spawn_egg_{ally,enemy}.png

Icons are left borderless - the art is a cut-out on transparency, which is what an inventory item
wants, and a train button already draws its own raised background underneath.

Geometry
  Every named frame is 32x32 art anchored at the top-left of the archive's shared 36x34 canvas, so
  one uniform crop covers all of them - no per-icon offsets. Lurker is the single frame wider than
  that (33px); its last column, one pixel of outline, is dropped by the crop.

Colour
  The art is one gold ramp plus a black outline (ticon.pcx ramp 0 plus its index 16). An ally icon
  keeps it verbatim. An enemy icon maps each pixel's brightness onto StarCraft's own player-red
  ramp, so highlights stay highlights and the outline stays an outline - the icon keeps its shape
  and only its hue changes, the same trick the spawn-egg recolours used before this.

Resolutions differ by surface on purpose. A train button blits its texture into a 16x16 quad
(ProductionScreen.ICON_SIZE), so a 16x16 source lands 1:1 and stays crisp; an item goes through the
mipmapped item atlas and is drawn at many sizes, so it keeps the native 32x32.

`import/` is gitignored: the committed artifacts are the PNGs written here, and this script is the
record of how they were made.

Usage:
    python tools/gen_command_icons.py
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SOURCE_DIR = ROOT / "import/command_icons"
TEX = ROOT / "src/main/resources/assets/asteriskcraft/textures"

# The art box inside cmdicons' 36x34 frame canvas (see module docs).
ART = (0, 0, 32, 32)

# ProductionScreen.ICON_SIZE - a train button's icon is blitted into a quad this big.
GUI_ICON_SIZE = 16

# StarCraft's player-red ramp: tunit.pcx ramp 0, entries 0-7, brightest to darkest. Read it back
# with `python -c "import sys; sys.path.insert(0, 'tools'); from grp_extract import
# read_pcx_palette; print(read_pcx_palette('import/tunit.pcx')[:8])"`.
RED_RAMP = [
    (244, 4, 4), (168, 8, 8), (168, 8, 8), (132, 4, 4),
    (96, 0, 0), (72, 0, 0), (52, 0, 0), (16, 0, 0),
]

# Icons whose source frame is not named after the mod's own unit: the Infested Villager is the
# mod's take on StarCraft's Infested Terran.
RENAMED = {"infested_villager": "infested_terran"}

# Units with a train button on some command card, and so a GUI icon (building/ProductionKind).
TRAINED = [
    "probe", "zealot", "dragoon", "scout", "dark_templar",
    # The Hive's card (ProductionKind.ZERG_BASE) morphs every Zerg unit itself - the swarm has no
    # factory building - so all of them need a train button once a human can play Zerg.
    "drone", "zergling", "hydralisk", "mutalisk", "lurker", "ultralisk", "overlord",
    "scv", "marine", "firebat", "ghost",
]

# Building kit items -> the building's icon. These double as the base command card's buttons for
# them, which are item renders (ProductionKind.Icon.ofItem) rather than GUI textures.
KITS = {
    "pylon_kit": "pylon",
    "gateway_kit": "gateway",
    "nexus_kit": "nexus",
    "hive_kit": "hive",
    "photon_cannon_kit": "photon_cannon",
    "bunker_kit": "bunker",
    "missile_turret_kit": "missile_turret",
    # The production buildings each race warps in beside its base. The Command Center's is the
    # Terran expansion kit, the sibling of nexus_kit and hive_kit above.
    "command_center_kit": "command_center",
    "barracks_kit": "barracks",
    "stargate_kit": "stargate",
    "spawning_pool_kit": "spawning_pool",
    "spire_kit": "spire",
}

# Every unit with an ally/enemy spawn-egg pair registered in AsteriskCraft.java.
EGGS = [
    "probe", "zealot", "dragoon", "scout", "dark_templar",
    "drone", "zergling", "ultralisk", "hydralisk", "mutalisk", "lurker", "overlord",
    "infested_villager", "sunken_colony", "spore_colony",
    "scv", "marine", "firebat", "ghost", "bunker", "missile_turret",
]


def luminance(px):
    return (px[0] * 299 + px[1] * 587 + px[2] * 114) / 255000.0


def load(name: str) -> Image.Image:
    """The named icon's 32x32 art, cropped out of the archive's shared frame canvas."""
    stem = RENAMED.get(name, name)
    return Image.open(SOURCE_DIR / f"{stem}.png").convert("RGBA").crop(ART)


def tint_red(im: Image.Image) -> Image.Image:
    """Remap the gold ramp onto StarCraft's player red, preserving shading and outline."""
    out = im.copy()
    px = out.load()
    w, h = out.size
    for y in range(h):
        for x in range(w):
            c = px[x, y]
            if c[3] == 0:
                continue
            # Brightest gold -> brightest red, near-black outline -> the ramp's dark end.
            step = round((1.0 - luminance(c)) * (len(RED_RAMP) - 1))
            r, g, b = RED_RAMP[step]
            px[x, y] = (r, g, b, c[3])
    return out


def to_gui_size(im: Image.Image) -> Image.Image:
    """Halve the art for a train button, then snap alpha so the silhouette stays a hard edge."""
    # Resize through premultiplied alpha ("RGBa"), or the transparent pixels' black RGB bleeds
    # into every edge.
    small = im.convert("RGBa").resize((GUI_ICON_SIZE, GUI_ICON_SIZE), Image.BOX).convert("RGBA")
    px = small.load()
    for y in range(GUI_ICON_SIZE):
        for x in range(GUI_ICON_SIZE):
            c = px[x, y]
            px[x, y] = (c[0], c[1], c[2], 255 if c[3] >= 128 else 0)
    return small


def write(im: Image.Image, out: Path):
    im.save(out)
    print(f"wrote {out.relative_to(ROOT)}")


def main():
    icons = TEX / "gui/icons"
    items = TEX / "item"

    for unit in TRAINED:
        write(to_gui_size(load(unit)), icons / f"{unit}.png")

    for kit, building in KITS.items():
        write(load(building), items / f"{kit}.png")

    for unit in EGGS:
        art = load(unit)
        write(art, items / f"{unit}_spawn_egg_ally.png")
        write(tint_red(art), items / f"{unit}_spawn_egg_enemy.png")


if __name__ == "__main__":
    main()
