#!/usr/bin/env python3
"""Emit a minimal 1x1x1 structure template holding nothing but a building's core block.

Every building in AsteriskCraft is stamped from a hand-authored `.nbt` in
`src/main/resources/data/asteriskcraft/structure/` (see building/BuildingTemplates), designed
in-game with a structure block and re-exported. A building that is *only* its core block has no
shape to design, and authoring one still means launching the client — so this writes it directly.

The output is byte-compatible with an in-game export: same tag layout, same DataVersion. Once a
real building is designed for one of these, re-export over the file and delete nothing here; the
declared Footprint in BuildingTemplates is what has to be updated to match (BuildingTemplatesTest
fails the build if it drifts).

No building uses this today — the Command Center it was written for has since been designed and
re-exported over its `.nbt`, which is exactly the path described above. Running it against a
building that now has a real template would overwrite that design, so name a new building.

Usage:
    python tools/make_core_template.py <name> asteriskcraft:<name>_core
"""

import gzip
import struct
import sys
from pathlib import Path

# Matches the DataVersion the existing templates (nexus/hive/gateway/pylon) were exported with.
DATA_VERSION = 4790

STRUCTURE_DIR = Path(__file__).resolve().parent.parent / "src/main/resources/data/asteriskcraft/structure"

TAG_END, TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 3, 8, 9, 10


def name(text):
    raw = text.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def tag(kind, key, payload):
    return struct.pack(">B", kind) + name(key) + payload


def ints(values):
    """A coordinate triple. Structure exports write these as a LIST of INT, not an INT_ARRAY —
    verified against the existing templates, and StructureTemplate.load reads them that way."""
    return lst(TAG_INT, [struct.pack(">i", v) for v in values])


def compound(*members):
    return b"".join(members) + struct.pack(">B", TAG_END)


def lst(kind, items):
    return struct.pack(">Bi", kind, len(items)) + b"".join(items)


def single_block_template(block_id):
    return compound(
        tag(TAG_LIST, "size", ints([1, 1, 1])),
        tag(TAG_LIST, "entities", lst(TAG_END, [])),
        tag(TAG_LIST, "blocks", lst(TAG_COMPOUND, [compound(
            tag(TAG_LIST, "pos", ints([0, 0, 0])),
            tag(TAG_INT, "state", struct.pack(">i", 0)),
        )])),
        tag(TAG_LIST, "palette", lst(TAG_COMPOUND, [compound(
            tag(TAG_STRING, "Name", name(block_id)),
        )])),
        tag(TAG_INT, "DataVersion", struct.pack(">i", DATA_VERSION)),
    )


def main(argv):
    if len(argv) != 3:
        print(__doc__)
        return 1
    template, block_id = argv[1], argv[2]
    root = struct.pack(">B", TAG_COMPOUND) + name("") + single_block_template(block_id)
    out = STRUCTURE_DIR / (template + ".nbt")
    with gzip.open(out, "wb") as handle:
        handle.write(root)
    print("wrote {} ({} bytes, {})".format(out, out.stat().st_size, block_id))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
