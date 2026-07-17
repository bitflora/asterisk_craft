#!/usr/bin/env python3
"""
Assemble a full modern EntityModel class from an old 1.12 Model*.java.

Wraps model_convert.py's createBodyLayer() body with:
  - EntityModel<LivingEntityRenderState> boilerplate (mirrors ProbeModel.java)
  - constructor that resolves named animated parts (head + up to two leg/arm pairs)
  - setupAnim() doing head look + a simple limb-swing walk cycle

Usage:
  python tools/make_model_class.py <old ModelFoo.java> <ClassName> <head> <legL> <legR> <armL> <armR>
Any of the part-name args may be "-" to skip. Head-nested lookups use '/' (e.g. neck/head).
Prints the full Java source to stdout.
"""
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).parent


def body_for(src):
    out = subprocess.run(
        [sys.executable, str(HERE / "model_convert.py"), src],
        capture_output=True, text=True, check=True,
    )
    return out.stdout.rstrip("\n")


def lookup_expr(path):
    """head 'neck/head' -> root.getChild(\"neck\").getChild(\"head\")"""
    segs = path.split("/")
    expr = "root"
    for s in segs:
        expr += f'.getChild("{s}")'
    return expr


def main():
    src, cls = sys.argv[1], sys.argv[2]
    head, legL, legR, armL, armR = (sys.argv[3:8] + ["-"] * 5)[:5]
    body = body_for(src)

    animated = {}
    for field, path in [("head", head), ("legL", legL), ("legR", legR), ("armL", armL), ("armR", armR)]:
        if path and path != "-":
            animated[field] = path

    fields = "\n".join(f"    private final ModelPart {f};" for f in animated)
    ctor_lookups = "\n".join(
        f'        this.{f} = {lookup_expr(p)};' for f, p in animated.items()
    )

    # capture initial x-rotations so setupAnim adds swing on top of the baked pose
    init_fields = "\n".join(f"    private final float {f}X0;" for f in animated)
    init_assign = "\n".join(f"        this.{f}X0 = this.{f}.xRot;" for f in animated)

    setup = []
    if "head" in animated:
        setup.append("        this.head.yRot = state.yRot * ((float) Math.PI / 180f);")
        setup.append("        this.head.xRot = this.headX0 + state.xRot * ((float) Math.PI / 180f);")
    swing = "        float swing = Mth.cos(state.walkAnimationPos * 0.6662f) * 1.4f * state.walkAnimationSpeed;"
    if any(k in animated for k in ("legL", "legR", "armL", "armR")):
        setup.append(swing)
    if "legL" in animated:
        setup.append("        this.legL.xRot = this.legLX0 + swing;")
    if "legR" in animated:
        setup.append("        this.legR.xRot = this.legRX0 - swing;")
    if "armL" in animated:
        setup.append("        this.armL.xRot = this.armLX0 - swing;")
    if "armR" in animated:
        setup.append("        this.armR.xRot = this.armRX0 + swing;")
    setup_body = "\n".join(setup) if setup else "        // no animated parts"

    tpl = f"""package net.bitflora.asteriskcraft.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/**
 * Ported from the 1.12 StarCraft mod's {Path(src).stem} (mechanical box translation; see
 * tools/model_convert.py). Head-look + a simple limb-swing walk are re-authored here; the old
 * per-render tweaks (neck scale, blade-sheath toggle) are intentionally not reproduced.
 */
public class {cls} extends EntityModel<LivingEntityRenderState> {{
{fields}
{init_fields}

    public {cls}(ModelPart root) {{
        super(root);
{ctor_lookups}
{init_assign}
    }}

    @Override
    public void setupAnim(LivingEntityRenderState state) {{
        super.setupAnim(state);
{setup_body}
    }}

{body}
}}
"""
    print(tpl)


if __name__ == "__main__":
    main()
