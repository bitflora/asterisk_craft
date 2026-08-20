#!/usr/bin/env python3
"""
Give every cube in a unit model its own UV island and emit a Blockbench project for hand-painting.

The models here are Java-defined geometry (`createBodyLayer()`). They were originally textured by
procedural generator scripts that painted *flat colour zones*: a dozen differently-sized cubes all
declaring the same `texOffs(0, 0)` and sampling one solid-colour rectangle. That is invisible while the
rectangle stays one colour, but it makes hand-painting impossible — painting an eye smears it onto every
cube sharing that corner. Those generators have been retired in favour of hand-painted textures.

This tool performs the one-time migration, per unit:

  1. Reads `build/model-export/<unit>.json` (written by ModelGeometryDumpTest — run `./gradlew test`).
  2. Shelf-packs every cube's box-UV footprint into non-overlapping islands with a 1-texel gutter,
     growing the texture height to the next power of two if needed.
  3. Remaps the existing PNGs (base *and* `_glow`) onto the new layout, so the unit renders
     pixel-identically in game. Nothing looks different until you actually paint.
  4. Rewrites the `texOffs(...)` calls (and `LayerDefinition.create(mesh, W, H)`) in the Java model.
  5. Writes `tools/blockbench/<unit>.bbmodel`, linked to the real PNG paths so Blockbench saves
     textures straight back into `src/main/resources/`.

It is idempotent: a model whose islands already don't overlap is left alone (only the .bbmodel is
re-emitted), so re-running can never shuffle artwork you have already painted.

Usage:
    python tools/blockbench_export.py            # all units
    python tools/blockbench_export.py zergling   # one unit

All eight units have been migrated, so in normal use this only re-emits .bbmodel files. The migration
path still runs for any model added later that starts out sharing offsets.

Per-cube islands need one editable `texOffs(int, int)` literal per cube, so a model class must not build
several cubes through one call site (no UV constants, no multiply-called helper, no loop over parts, no
shared CubeListBuilder). The tool refuses — without touching any file — and names the cause. See
docs/texturing.md.

--------------------------------------------------------------------------------------------------
Coordinate conventions
--------------------------------------------------------------------------------------------------
Verified against Blockbench's own `js/formats/java/modded_entity.js` (the codec that exports Java from
a .bbmodel), so that opening one of these files and running Blockbench's *Export Java Entity* round-
trips back to the numbers we started from. With the format defaults (`modded_entity_version: "1.17"`,
`modded_entity_flip_y: true`) that codec writes:

    rx = degToRad(-rotation[0]);  ry = degToRad(-rotation[1]);  rz = degToRad(rotation[2])
    origin = group.origin - parent.origin;  origin[0] *= -1;  origin[1] *= -1  (+24 at the root)
    addBox x = group.origin[0] - cube.to[0]
    addBox y = -cube.from[1] - size_y + group.origin[1]
    addBox z = cube.from[2] - group.origin[2]

Note that **X is negated as well as Y** — Blockbench's Java-entity space is the Minecraft one rotated
180 degrees about the vertical axis, not merely mirrored in Y. Inverting the above gives what this
script emits (see `bb_group_origin` / `bb_cube_box`). Group origins are absolute in a .bbmodel and the
codec subtracts the parent's, which confirms that only *translations* accumulate down the tree:
rotations stay on their own group, exactly as `ModelPart` applies them at render time.

The box-UV footprint of a cube is `2 * (width + depth)` by `depth + height`, verified against
`ModelPart.Cube`'s constructor in the decompiled 26.1.2 source (its rightmost UV column is
`xTexOffs + depth + width + depth + width`). `grow`/inflate does not affect UVs.
"""
import json
import math
import re
import sys
import uuid
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
DUMP_DIR = ROOT / "build" / "model-export"
BB_DIR = ROOT / "tools" / "blockbench"
TEX_DIR = ROOT / "src/main/resources/assets/asteriskcraft/textures/entity"
JAVA_DIR = ROOT / "src/main/java/net/bitflora/asteriskcraft/client"

# unit -> (race subpackage, model class name)
UNITS = {
    "probe": ("protoss", "ProbeModel"),
    "zealot": ("protoss", "ZealotModel"),
    "dragoon": ("protoss", "DragoonModel"),
    "scout": ("protoss", "ScoutModel"),
    "dark_templar": ("protoss", "DarkTemplarModel"),
    "photon_cannon": ("protoss", "PhotonCannonModel"),
    "zergling": ("zerg", "ZerglingModel"),
    "hydralisk": ("zerg", "HydraliskModel"),
    "mutalisk": ("zerg", "MutaliskModel"),
    "drone": ("zerg", "DroneModel"),
    "sunken_colony": ("zerg", "SunkenColonyModel"),
}

GUTTER = 1  # texels between islands, so painting can't bleed across and fractional sizes stay clear

# Blockbench's Java-entity space puts ground level at y=24 and faces the model the opposite way in X.
BB_GROUND = 24.0


# --- geometry ------------------------------------------------------------------------------------

def footprint(cube):
    """Box-UV footprint in whole texels: 2*(w+d) wide, d+h tall, rounded up for fractional cubes."""
    w, h, d = cube["dimensions"]
    return math.ceil(2.0 * (w + d)), math.ceil(d + h)


def iter_cubes(model):
    """Yield ((part_path, cube_index), part, cube) in the dump's stable order."""
    for part in model["parts"]:
        for cube in part["cubes"]:
            yield (part["path"], cube["index"]), part, cube


def absolute_origins(model):
    """Accumulate PartPose translations down the tree — rotations stay on their own part."""
    origins = {}
    for part in model["parts"]:
        pose = part["pose"]
        parent = part["path"].rsplit("/", 1)[0] if "/" in part["path"] else None
        base = origins[parent] if parent else (0.0, 0.0, 0.0)
        origins[part["path"]] = (base[0] + pose["x"], base[1] + pose["y"], base[2] + pose["z"])
    return origins


# --- UV packing ----------------------------------------------------------------------------------

def has_overlap(model):
    """True if any two cubes share a texel, i.e. the model still needs migrating."""
    boxes = []
    for _, _, cube in iter_cubes(model):
        fw, fh = footprint(cube)
        if fw <= 0 or fh <= 0:
            continue
        u, v = cube["texOffs"]
        boxes.append((int(u), int(v), int(u) + fw, int(v) + fh))
    for i, a in enumerate(boxes):
        for b in boxes[i + 1:]:
            if a[0] < b[2] and b[0] < a[2] and a[1] < b[3] and b[1] < a[3]:
                return True
    return False


def pack(model):
    """Shelf-pack every cube into its own island. Returns (mapping, tex_width, tex_height)."""
    tex_w = model["texWidth"]
    items = []
    for key, _, cube in iter_cubes(model):
        fw, fh = footprint(cube)
        if fw <= 0 or fh <= 0:
            continue  # a zero-volume cube occupies no texels
        items.append((fw, fh, key))

    widest = max((it[0] for it in items), default=0)
    if widest > tex_w:
        raise SystemExit(
            f"{model['unit']}: a cube needs {widest} texels of width but the texture is only {tex_w} "
            f"wide. Widen the texture in the model's LayerDefinition.create(...) first."
        )

    # Tallest-first shelves pack tightly and deterministically; the key breaks ties stably.
    items.sort(key=lambda it: (-it[1], -it[0], it[2]))

    mapping = {}
    x = y = shelf_h = 0
    for fw, fh, key in items:
        if x + fw > tex_w:
            x = 0
            y += shelf_h + GUTTER
            shelf_h = 0
        mapping[key] = (x, y)
        x += fw + GUTTER
        shelf_h = max(shelf_h, fh)

    used = y + shelf_h
    tex_h = max(model["texHeight"], 1 << (used - 1).bit_length() if used > 0 else 1)
    return mapping, tex_w, tex_h


# --- texture remapping ---------------------------------------------------------------------------

def remap_texture(path, model, mapping, tex_w, tex_h):
    """Block-copy each cube's footprint from its old island to its new one, onto a blank canvas."""
    if not path.exists():
        print(f"  ! {path.name} missing, skipped")
        return
    src = Image.open(path).convert("RGBA")
    dst = Image.new("RGBA", (tex_w, tex_h), (0, 0, 0, 0))
    for key, _, cube in iter_cubes(model):
        if key not in mapping:
            continue
        fw, fh = footprint(cube)
        ou, ov = int(cube["texOffs"][0]), int(cube["texOffs"][1])
        nu, nv = mapping[key]
        # Clamp to the source bounds: the old flat-zone layout let footprints run past their zone.
        box = (ou, ov, min(ou + fw, src.width), min(ov + fh, src.height))
        if box[2] <= box[0] or box[3] <= box[1]:
            continue
        dst.paste(src.crop(box), (nu, nv))
    dst.save(path)
    print(f"  wrote {path.relative_to(ROOT)} ({tex_w}x{tex_h})")


# --- Java rewriting ------------------------------------------------------------------------------

def mask_comments(line, in_block):
    """
    Blank out commented regions, preserving length so match offsets still index into the real line.

    Comments must be excluded from matching because these model classes describe their UV zones in
    prose — a javadoc line reading "CARAPACE = texOffs(0,0)" would otherwise be counted as a cube and
    desynchronise every subsequent match. Deliberately simple: these files contain no string literals
    holding comment markers, and the only quotes are the part names in addOrReplaceChild.

    Returns (masked_line, still_inside_block_comment).
    """
    out = []
    i = 0
    n = len(line)
    while i < n:
        if in_block:
            end = line.find("*/", i)
            if end == -1:
                out.append(" " * (n - i))
                i = n
            else:
                out.append(" " * (end + 2 - i))
                i = end + 2
                in_block = False
        else:
            start = line.find("/*", i)
            slash = line.find("//", i)
            if slash != -1 and (start == -1 or slash < start):
                out.append(line[i:slash])
                out.append(" " * (n - slash))
                i = n
            elif start != -1:
                out.append(line[i:start])
                i = start
                in_block = True
            else:
                out.append(line[i:])
                i = n
    return "".join(out), in_block


CHILD_RE = re.compile(r'(?:PartDefinition\s+(\w+)\s*=\s*)?(\w+)\.addOrReplaceChild\(\s*"([^"]+)"')
TEXOFFS_RE = re.compile(r"texOffs\(\s*(-?\d+)\s*,\s*(-?\d+)\s*\)")
LAYER_RE = re.compile(r"(LayerDefinition\.create\(\s*mesh\s*,\s*)(\d+)(\s*,\s*)(\d+)(\s*\))")


def plan_java_rewrite(path, model, mapping, tex_w, tex_h):
    """
    Produce the rewritten Java source with each cube's texOffs(u, v) replaced by its new island.

    Returns the new file text without writing it, so the caller can validate before touching anything
    on disk — a half-applied migration (textures remapped, Java not) would silently corrupt the unit.

    Cubes are matched to the dump by (part_path, index-within-part). The file is scanned linearly:
    an `addOrReplaceChild("name", ...)` opens a part, and the `texOffs(...)` calls that follow belong
    to it in order. Local variable names are tracked so a child's full path can be resolved from the
    receiver it was added to (`body.addOrReplaceChild("head", ...)` -> `body/head`).
    """
    lines = path.read_text(encoding="utf-8").splitlines(keepends=True)
    var_paths = {"root": ""}
    current = None
    cube_index = 0
    replaced = 0
    in_block_comment = False

    for i, line in enumerate(lines):
        masked, in_block_comment = mask_comments(line, in_block_comment)

        child = CHILD_RE.search(masked)
        if child:
            new_var, parent_var, name = child.groups()
            if parent_var not in var_paths:
                raise SystemExit(f"{path.name}:{i + 1}: unknown receiver '{parent_var}'")
            parent_path = var_paths[parent_var]
            current = name if parent_path == "" else f"{parent_path}/{name}"
            if new_var:
                var_paths[new_var] = current
            cube_index = 0

        if "texOffs(" not in masked:
            continue

        # Assign cubes left-to-right (source order), then splice right-to-left so earlier spans keep
        # their offsets as replacement text changes length.
        edits = []
        for match in TEXOFFS_RE.finditer(masked):
            key = (current, cube_index)
            cube_index += 1
            if key not in mapping:
                raise SystemExit(f"{path.name}:{i + 1}: no packed island for cube {key}")
            replaced += 1
            u, v = mapping[key]
            edits.append((match.start(), match.end(), f"texOffs({u}, {v})"))

        for start, end, text in reversed(edits):
            line = line[:start] + text + line[end:]
        lines[i] = line

    if replaced != len(mapping):
        raise SystemExit(
            f"{path.name}: rewrote {replaced} texOffs literals but the model has {len(mapping)} cubes.\n"
            f"  Per-cube UV islands need one editable texOffs literal per cube, so this file must not\n"
            f"  build cubes in a way that shares a call site between them. Look for:\n"
            f"    - named UV constants, e.g. texOffs(GOLD_U, GOLD_V) — inline them as literals;\n"
            f"    - a helper method called more than once — give it u/v parameters per call site;\n"
            f"    - a loop building several parts — index a per-part UV table;\n"
            f"    - one CubeListBuilder variable reused by several parts — build each part its own.\n"
            f"  Fix the file by hand, re-run `./gradlew test`, then re-run this tool."
        )

    text = "".join(lines)
    text, n = LAYER_RE.subn(lambda m: f"{m.group(1)}{tex_w}{m.group(3)}{tex_h}{m.group(5)}", text)
    if n != 1:
        raise SystemExit(f"{path.name}: expected exactly one LayerDefinition.create(mesh, W, H), found {n}")

    return text, replaced


# --- .bbmodel emission ---------------------------------------------------------------------------

def det_uuid(*parts):
    """Deterministic UUIDs so re-running doesn't churn the file in git."""
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "asteriskcraft:" + ":".join(str(p) for p in parts)))


def bb_group_origin(abs_origin):
    """Minecraft part pivot -> absolute Blockbench origin (X negated, Y flipped about ground)."""
    return [-abs_origin[0], BB_GROUND - abs_origin[1], abs_origin[2]]


def bb_cube_box(abs_origin, cube):
    """Minecraft addBox(origin, dimensions) -> Blockbench absolute from/to."""
    gx, gy, gz = bb_group_origin(abs_origin)
    min_x, min_y, min_z = cube["origin"]
    w, h, d = cube["dimensions"]
    frm = [gx - min_x - w, gy - min_y - h, gz + min_z]
    to = [gx - min_x, gy - min_y, gz + min_z + d]
    return frm, to


def build_bbmodel(model, mapping, tex_w, tex_h, textures):
    origins = absolute_origins(model)
    elements = []
    groups = {}

    for part in model["parts"]:
        origin = bb_group_origin(origins[part["path"]])
        pose = part["pose"]
        groups[part["path"]] = {
            "name": part["path"].rsplit("/", 1)[-1],
            "origin": origin,
            # Inverse of the codec's rx=-rot[0], ry=-rot[1], rz=+rot[2].
            "rotation": [
                -math.degrees(pose["xRot"]),
                -math.degrees(pose["yRot"]),
                math.degrees(pose["zRot"]),
            ],
            "uuid": det_uuid(model["unit"], "group", part["path"]),
            "export": True,
            "isOpen": True,
            "visibility": True,
            "children": [],
        }

    for key, part, cube in iter_cubes(model):
        frm, to = bb_cube_box(origins[part["path"]], cube)
        u, v = mapping.get(key, (int(cube["texOffs"][0]), int(cube["texOffs"][1])))
        elem_uuid = det_uuid(model["unit"], "cube", key[0], key[1])
        grow = cube["grow"]
        elements.append({
            "name": f"{part['path'].rsplit('/', 1)[-1]}_{key[1]}",
            "box_uv": True,
            "rescale": False,
            "locked": False,
            "type": "cube",
            "from": frm,
            "to": to,
            "autouv": 0,
            "color": 0,
            # CubeDeformation is per-axis in Minecraft but a single scalar in Blockbench; every model
            # here uses a uniform grow, so X is representative.
            "inflate": grow[0],
            "mirror_uv": cube["mirror"],
            "origin": bb_group_origin(origins[part["path"]]),
            "rotation": [0, 0, 0],
            "uv_offset": [u, v],
            "uuid": elem_uuid,
        })
        groups[part["path"]]["children"].append(elem_uuid)

    # Nest groups: a part whose path has a parent becomes that parent's child.
    outliner = []
    for path in sorted(groups, key=lambda p: p.count("/")):
        if "/" in path:
            groups[path.rsplit("/", 1)[0]]["children"].append(groups[path])
        else:
            outliner.append(groups[path])

    return {
        "meta": {
            "format_version": "4.5",
            "model_format": "modded_entity",
            "box_uv": True,
        },
        "name": model["unit"],
        "model_identifier": model["unit"],
        "modded_entity_version": "1.17",
        "modded_entity_flip_y": True,
        "modded_entity_entity_class": "Entity",
        "visible_box": [2, 2, 0],
        "variable_placeholders": "",
        "resolution": {"width": tex_w, "height": tex_h},
        "elements": elements,
        "outliner": outliner,
        "textures": textures,
    }


def verify_roundtrip(model, bbmodel):
    """
    Re-implement Blockbench's *export* path verbatim and assert it reproduces the Java we started from.

    This is the guarantee that the coordinate conventions above are right: if opening the emitted
    .bbmodel and running Blockbench's "Export Java Entity" would not give back the same
    `PartPose`/`addBox`/`texOffs` numbers, this raises instead of silently shipping a skewed model.
    The formulas mirror `modded_entity.js` with `modded_entity_flip_y: true`.
    """
    elements = {e["uuid"]: e for e in bbmodel["elements"]}
    groups = {}

    def walk(nodes, parent):
        for node in nodes:
            if isinstance(node, dict):
                groups[node["name"]] = (node, parent)
                walk(node["children"], node)

    walk(bbmodel["outliner"], None)

    problems = []
    for part in model["parts"]:
        group, parent = groups[part["path"].rsplit("/", 1)[-1]]

        origin = list(group["origin"])
        if parent is not None:
            origin = [origin[i] - parent["origin"][i] for i in range(3)]
        origin[0] *= -1
        origin[1] *= -1
        if parent is None:
            origin[1] += BB_GROUND
        rot = group["rotation"]
        pose = origin + [math.radians(-rot[0]), math.radians(-rot[1]), math.radians(rot[2])]
        want = [part["pose"][k] for k in ("x", "y", "z", "xRot", "yRot", "zRot")]
        if any(abs(a - b) > 1e-4 for a, b in zip(pose, want)):
            problems.append(f"pose {part['path']}: {pose} != {want}")

        cube_uuids = [u for u in group["children"] if isinstance(u, str)]
        for i, cube in enumerate(part["cubes"]):
            elem = elements[cube_uuids[i]]
            size = [elem["to"][j] - elem["from"][j] for j in range(3)]
            box = [
                group["origin"][0] - elem["to"][0],
                -elem["from"][1] - size[1] + group["origin"][1],
                elem["from"][2] - group["origin"][2],
            ]
            if any(abs(a - b) > 1e-4 for a, b in zip(box, cube["origin"])):
                problems.append(f"box {part['path']}#{i}: {box} != {cube['origin']}")
            if any(abs(a - b) > 1e-4 for a, b in zip(size, cube["dimensions"])):
                problems.append(f"size {part['path']}#{i}: {size} != {cube['dimensions']}")

    if problems:
        raise SystemExit(
            f"{model['unit']}: emitted .bbmodel would not round-trip back to the same Java:\n  "
            + "\n  ".join(problems)
        )


def texture_entries(unit, tex_w, tex_h):
    """Link (not embed) the real PNGs so Blockbench saves edits back into src/main/resources."""
    entries = []
    for index, name in enumerate([f"{unit}.png", f"{unit}_glow.png"]):
        path = TEX_DIR / name
        if not path.exists():
            continue
        entries.append({
            "path": str(path),
            "name": name,
            "folder": "entity",
            "namespace": "asteriskcraft",
            "id": str(index),
            "particle": False,
            "render_mode": "default",
            "visible": True,
            "mode": "link",
            "saved": True,
            "uuid": det_uuid(unit, "texture", name),
            "width": tex_w,
            "height": tex_h,
            "uv_width": tex_w,
            "uv_height": tex_h,
        })
    return entries


# --- driver --------------------------------------------------------------------------------------

def process(unit):
    dump = DUMP_DIR / f"{unit}.json"
    if not dump.exists():
        raise SystemExit(f"Missing {dump.relative_to(ROOT)} — run `./gradlew test` first.")
    model = json.loads(dump.read_text(encoding="utf-8"))
    race, cls = UNITS[unit]
    java_path = JAVA_DIR / race / f"{cls}.java"

    print(f"{unit}:")
    if has_overlap(model):
        mapping, tex_w, tex_h = pack(model)
        # Plan the Java rewrite first: if this file's structure can't express per-cube UVs, bail out
        # before any texture is touched, so a failed run leaves the unit exactly as it was.
        java_text, replaced = plan_java_rewrite(java_path, model, mapping, tex_w, tex_h)
        remap_texture(TEX_DIR / f"{unit}.png", model, mapping, tex_w, tex_h)
        remap_texture(TEX_DIR / f"{unit}_glow.png", model, mapping, tex_w, tex_h)
        java_path.write_text(java_text, encoding="utf-8")
        print(f"  wrote {java_path.relative_to(ROOT)} ({replaced} texOffs)")
    else:
        # Already migrated — leave the UVs and any hand-painted pixels exactly as they are.
        mapping = {key: (int(c["texOffs"][0]), int(c["texOffs"][1])) for key, _, c in iter_cubes(model)}
        tex_w, tex_h = model["texWidth"], model["texHeight"]
        print("  already unwrapped, textures and Java left untouched")

    BB_DIR.mkdir(parents=True, exist_ok=True)
    out = BB_DIR / f"{unit}.bbmodel"
    bbmodel = build_bbmodel(model, mapping, tex_w, tex_h, texture_entries(unit, tex_w, tex_h))
    verify_roundtrip(model, bbmodel)
    out.write_text(json.dumps(bbmodel, indent=2), encoding="utf-8")
    print(f"  wrote {out.relative_to(ROOT)} (round-trip verified)")


def main():
    units = sys.argv[1:] or list(UNITS)
    for unit in units:
        if unit not in UNITS:
            raise SystemExit(f"Unknown unit '{unit}'. Known: {', '.join(UNITS)}")
        process(unit)
    print("\nNote: re-run `./gradlew test` to refresh the dump after the Java was rewritten.")


if __name__ == "__main__":
    main()
