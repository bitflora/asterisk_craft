#!/usr/bin/env python3
"""
Convert an old 1.12 Forge ModelBase/ModelRenderer Java model (from ../Starcraft-Mod)
into a modern Minecraft 26.1.2 data-driven EntityModel `createBodyLayer()` body.

The old API declares each part as a `ModelRenderer` field with:
    this.foo = new ModelRenderer(this, u, v);   // texture offset
    this.foo.mirror = true;                      // optional
    this.foo.setRotationPoint(x, y, z);          // pivot (relative to parent)
    this.foo.addBox(x, y, z, w, h, d, inflate);  // one or more cubes
    this.setRotation(foo, rx, ry, rz);           // optional euler rotation
and wires the hierarchy separately:
    this.parent.addChild(this.child);

Modern equivalent (see ProbeModel.java):
    parent.addOrReplaceChild("child",
        CubeListBuilder.create().texOffs(u, v)[.mirror()].addBox(x,y,z,w,h,d[, new CubeDeformation(i)]),
        PartPose.offset(x,y,z) | PartPose.offsetAndRotation(x,y,z,rx,ry,rz));

Pivot semantics match: a 1.12 child rotationPoint is relative to its parent, exactly like a
modern child PartPose offset. (Verify visually in runClient — see plan.)

Usage:
    python tools/model_convert.py <path-to-old-ModelFoo.java>
Prints the generated createBodyLayer() method to stdout.
"""
import re
import sys
from pathlib import Path


def fnum(tok: str) -> str:
    """Normalize a Java numeric literal to a float literal like `-5.0f`."""
    tok = tok.strip().rstrip("Ff").rstrip("Dd")
    if tok == "":
        return "0.0f"
    val = float(tok)
    if val == int(val):
        return f"{int(val)}.0f"
    # keep full precision the old file carried
    return f"{tok}f"


class Part:
    def __init__(self, name):
        self.name = name
        self.u = 0
        self.v = 0
        self.mirror = False
        self.pivot = ("0.0f", "0.0f", "0.0f")
        self.rot = ["0.0f", "0.0f", "0.0f"]  # per-axis; used only if has_rot
        self.has_rot = False
        self.boxes = []  # list of (x,y,z,w,h,d,inflate)
        self.children = []
        self.parent = None


FLOAT = r"(-?\d+(?:\.\d+)?(?:[Ee]-?\d+)?[FfDd]?)"


def parse(text):
    parts = {}

    def get(name):
        if name not in parts:
            parts[name] = Part(name)
        return parts[name]

    tex_w = tex_h = None
    m = re.search(r"textureWidth\s*=\s*(\d+)", text)
    if m:
        tex_w = int(m.group(1))
    m = re.search(r"textureHeight\s*=\s*(\d+)", text)
    if m:
        tex_h = int(m.group(1))

    # Only parse the constructor (geometry). Truncate at the first @Override so the render()/
    # setRotationAngles() methods' animation `rotateAngle` assignments are not baked into the mesh.
    ctor = text.split("@Override", 1)[0]

    T = r"(?:this\.)?"  # some old models omit the `this.` prefix

    # constructor + texOffs
    for m in re.finditer(rf"{T}(\w+)\s*=\s*new ModelRenderer\(this,\s*(\d+),\s*(\d+)\)", ctor):
        p = get(m.group(1))
        p.u = int(m.group(2))
        p.v = int(m.group(3))

    for m in re.finditer(rf"{T}(\w+)\.mirror\s*=\s*true", ctor):
        get(m.group(1)).mirror = True

    for m in re.finditer(rf"{T}(\w+)\.setRotationPoint\(\s*{FLOAT},\s*{FLOAT},\s*{FLOAT}\s*\)", ctor):
        p = get(m.group(1))
        p.pivot = (fnum(m.group(2)), fnum(m.group(3)), fnum(m.group(4)))

    # addBox with optional inflate arg
    box_re = re.compile(
        rf"{T}(\w+)\.addBox\(\s*{FLOAT},\s*{FLOAT},\s*{FLOAT},\s*{FLOAT},\s*{FLOAT},\s*{FLOAT}(?:,\s*{FLOAT})?\s*\)"
    )
    for m in box_re.finditer(ctor):
        p = get(m.group(1))
        g = m.groups()
        inflate = g[7]
        p.boxes.append((g[1], g[2], g[3], g[4], g[5], g[6], inflate))

    # setRotation(part, rx, ry, rz) -- sets all three axes
    for m in re.finditer(rf"{T}setRotation\(\s*(\w+),\s*{FLOAT},\s*{FLOAT},\s*{FLOAT}\s*\)", ctor):
        p = get(m.group(1))
        p.rot = [fnum(m.group(2)), fnum(m.group(3)), fnum(m.group(4))]
        p.has_rot = True

    # direct static pose: part.rotateAngleX = val; (a few models use this instead of setRotation)
    axis = {"X": 0, "Y": 1, "Z": 2}
    for m in re.finditer(rf"{T}(\w+)\.rotateAngle([XYZ])\s*=\s*{FLOAT}", ctor):
        p = get(m.group(1))
        p.rot[axis[m.group(2)]] = fnum(m.group(3))
        p.has_rot = True

    for m in re.finditer(rf"{T}(\w+)\.addChild\(\s*{T}(\w+)\s*\)", ctor):
        parent = get(m.group(1))
        child = get(m.group(2))
        parent.children.append(child.name)
        child.parent = parent.name

    return parts, tex_w, tex_h


def emit_builder(p: Part) -> str:
    parts = ["CubeListBuilder.create()"]
    first = True
    for (x, y, z, w, h, d, inflate) in p.boxes:
        seg = f".texOffs({p.u}, {p.v})"
        if first and p.mirror:
            seg += ".mirror()"
        box = f".addBox({fnum(x)}, {fnum(y)}, {fnum(z)}, {fnum(w)}, {fnum(h)}, {fnum(d)}"
        if inflate is not None and float(inflate.rstrip('FfDd')) != 0.0:
            box += f", new CubeDeformation({fnum(inflate)})"
        box += ")"
        parts.append(seg + box)
        first = False
    if not p.boxes:
        # part with no cubes (pure pivot group)
        parts.append(".texOffs(0, 0)")
    return "".join(parts)


def emit_pose(p: Part) -> str:
    px, py, pz = p.pivot
    if not p.has_rot:
        return f"PartPose.offset({px}, {py}, {pz})"
    rx, ry, rz = p.rot
    return f"PartPose.offsetAndRotation({px}, {py}, {pz}, {rx}, {ry}, {rz})"


def emit_tree(parts, out):
    roots = [p for p in parts.values() if p.parent is None]

    def var(name):
        return name  # PartDefinition local var name

    lines = []

    def recurse(p: Part, parent_var: str):
        has_kids = bool(p.children)
        pose = emit_pose(p)
        builder = emit_builder(p)
        if has_kids:
            lines.append(
                f'        PartDefinition {var(p.name)} = {parent_var}.addOrReplaceChild("{p.name}",'
            )
            lines.append(f"                {builder},")
            lines.append(f"                {pose});")
        else:
            lines.append(f'        {parent_var}.addOrReplaceChild("{p.name}",')
            lines.append(f"                {builder},")
            lines.append(f"                {pose});")
        for c in p.children:
            recurse(parts[c], var(p.name))

    for r in roots:
        recurse(r, "root")
    out.extend(lines)


def main():
    src = Path(sys.argv[1])
    text = src.read_text(encoding="utf-8", errors="replace")
    parts, tex_w, tex_h = parse(text)
    out = []
    out.append("    public static LayerDefinition createBodyLayer() {")
    out.append("        MeshDefinition mesh = new MeshDefinition();")
    out.append("        PartDefinition root = mesh.getRoot();")
    emit_tree(parts, out)
    out.append(f"        return LayerDefinition.create(mesh, {tex_w or 64}, {tex_h or 32});")
    out.append("    }")
    print("\n".join(out))
    # summary to stderr
    roots = [p.name for p in parts.values() if p.parent is None]
    print(f"// parts={len(parts)} roots={roots} tex={tex_w}x{tex_h}", file=sys.stderr)


if __name__ == "__main__":
    main()
