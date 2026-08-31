# Building a humanoid Terran unit

Read this before adding a Terran infantry unit. It is the operational form of the two Terran rules in
[CLAUDE.md](../CLAUDE.md) — *Terrans are villagers* and *borrowing a vanilla model means borrowing its
texture* — plus the checklist of everything outside the model that a new unit touches.

Worked examples, in the order they are useful:

| | |
|---|---|
| `client/terran/MarineModel` + `MarineHeadLayer` | the reference humanoid — adult villager head under a helmet |
| `client/terran/FirebatModel` + `FirebatHeadLayer` | the second one — pillager head, bare, red plate |
| `client/terran/GhostModel` + `GhostHeadLayer` | the third — villager head wearing **two** vanilla profession overlays |
| `client/terran/ScvModel` + `ScvPilotLayer` | the **counter-example**: a mech, deliberately not this pose |
| `client/terran/WraithModel` | the other **counter-example**: an aircraft. No pose, no borrowed head, nothing on this page applies — model it against `client/protoss/ScoutModel` instead |
| `client/terran/GoliathModel` + `GoliathGolemLayer` + `GoliathPilotLayer` | the third **counter-example**: a mech with a rider. Nothing on this page applies either — but it is the reference for borrowing a vanilla model that **animates**, and for a borrowed *body* rather than a head. Its pilot is the second user of the profession-overlay trick in §2 |
| `client/terran/ScienceVesselModel` | the fourth **counter-example**: the second aircraft, and the first with no weapon at all. Nothing on this page applies — model it against `client/protoss/ObserverModel`, which is the other unarmed flyer, rather than against the Wraith |

`entity/terran/MarineEntity` and `entity/terran/FirebatEntity` are the matching entity classes, and the
Firebat's git history is the cleanest end-to-end record of what adding one costs.

---

## 1. The pose is not a choice

Every Terran person starts from vanilla's villager, dimension for dimension. Copy this skeleton
verbatim and then move what your unit actually needs moved — do not start from a humanoid model, a
zombie, or your own proportions.

The `texOffs` values below are throwaway — write anything non-overlapping and let
`tools/blockbench_export.py` assign the real islands (§6). What matters is the boxes and the pivots.

```java
// Torso: vanilla's villager 8x12x6, with the inflated 20-tall robe that falls over the hips.
// The robe is what turns two bare legs into a villager. It is not optional decoration.
PartDefinition body = root.addOrReplaceChild("body",
        CubeListBuilder.create().texOffs(0, 0).addBox(-4.0f, 0.0f, -3.0f, 8.0f, 12.0f, 6.0f),
        PartPose.ZERO);
body.addOrReplaceChild("robe",
        CubeListBuilder.create().texOffs(0, 20).addBox(-4.0f, 0.0f, -3.0f, 8.0f, 20.0f, 6.0f,
                new CubeDeformation(0.5f)),
        PartPose.ZERO);

// Arms folded across the belly — the Terran default pose. An empty container so the three cubes
// move as one limb-group while each keeps its own texOffs literal (see §5).
PartDefinition arms = root.addOrReplaceChild("arms",
        CubeListBuilder.create(),
        PartPose.offsetAndRotation(0.0f, 3.0f, -1.0f, -0.75f, 0.0f, 0.0f));
// arm_left  : addBox( 4, -2, -2, 4, 8, 4)
// arm_right : addBox(-8, -2, -2, 4, 8, 4)
// arms_folded: addBox(-4,  2, -2, 8, 4, 4)

// Legs on the root, NOT on the body — so the torso's idle breath does not drag planted feet.
// 12 (hips) + 12 (leg) lands the soles on y=24.
// leg_left  : addBox(-2, 0, -2, 4, 12, 4) at offset( 2, 12, 0)
// leg_right : addBox(-2, 0, -2, 4, 12, 4) at offset(-2, 12, 0)
```

Authored in true pixel space (16px = 1 block): **up is negative y, ground is y=24, forward is -z**, and
the neck is y=0.

### The weapon is cradled, not aimed

A villager's arms do not come up, so whatever the unit carries lies **across the crossed forearms**,
hung off the `arms` container rather than off one arm. Give the weapon group a `+0.75` xRot that
cancels the container's `-0.75`: the weapon then sits level in the world while the arms keep vanilla's
pose. Mount it around `(0, 4.31, -2.28)` relative to the container, which is just clear of the forearms.

The Marine rakes its rifle across the body with a yaw (`-0.30`) so it does not read as a pole; the
Firebat holds its flamethrower square because twin barrels pointing straight ahead is what makes the
spread legible. Either is fine — the pitch cancellation is the part that is not negotiable.

### Animation

Three deltas on top of `super.setupAnim(state)`, which restores every part to its baked pose:

```java
this.head.yRot += state.yRot * DEG;                 // DEG = (float)(Math.PI / 180.0)
this.head.xRot += state.xRot * DEG;
this.body.y   -= Mth.sin(state.ageInTicks * 0.09f) * 0.25f;   // idle breath

// Vanilla's villager gait: legs opposed, HALF the amplitude a zombie's use (the 0.5 factor).
// Anything livelier stops reading as a villager and starts reading as a soldier marching.
float pos = state.walkAnimationPos * 0.6662f;
this.legLeft.xRot  += Mth.cos(pos)            * 1.4f * state.walkAnimationSpeed * 0.5f;
this.legRight.xRot += Mth.cos(pos + Mth.PI)   * 1.4f * state.walkAnimationSpeed * 0.5f;
```

The attack pulse is `Mth.sin(clamp(attackProgress, 0, 1) * Mth.PI)` — 0 at idle, peaking at the
midpoint of the strike — applied to the `arms` container and a little to `body`. Keep it **small**:
vanilla villager arms never move at all, so a little goes a long way before the pose stops reading as
folded. Direction is the unit's own statement — the Marine recoils *back* (`arms.z += kick`), the
Firebat thrusts *forward* (`arms.z -= thrust`).

`attackProgress` comes off a synced `EntityDataAccessor<Integer>` on the entity counting *down*, which
the renderer's `extractRenderState` converts to 0→1 counting *up* with partial ticks. Synced rather
than an entity event because an int carries progress, not just a start, and cannot collide with a
vanilla `LivingEntity` event byte. A unit that never swings cannot use `getAttackAnim`.

---

## 2. The head is borrowed, and it is what distinguishes the unit

**`head` is an empty container part with no cubes.** A `RenderLayer` draws vanilla's own head into it,
off vanilla's own texture. The mod's own headgear (if any) hangs off the same container, so borrowed
face and mod helmet turn together as one head.

The reason is the texture, not the geometry: a model part can only be painted from its own model's
texture, so owning the head would mean hand-copying vanilla pixels into your PNG and keeping them in
sync by hand forever.

The model exposes the part chain for the layer:

```java
public void translateToHead(PoseStack poseStack) {
    this.root.translateAndRotate(poseStack);
    this.head.translateAndRotate(poseStack);
}
```

and the layer clears vanilla's own neck offset once, in its constructor, rather than cancelling it on
the PoseStack every frame — safe because that `ModelPart` instance is baked for this layer and nothing
else touches it:

```java
this.head = context.bakeLayer(ModelLayers.PILLAGER).getChild("head");
this.head.setPos(0.0f, 0.0f, 0.0f);
this.head.getChild("hat").visible = false;
```

Always `return` early on `state.isInvisible` in `submit` — otherwise a cloaked or invisible unit is a
floating face.

### Which vanilla head, and what is inside it

| `ModelLayerLocation` | Texture | Head box | Children — keep / hide |
|---|---|---|---|
| `VILLAGER` | `textures/entity/villager/villager.png` | 8x10x8 | hide `hat` (takes its nested 16x16 `hat_rim` with it) |
| `VILLAGER_BABY` | `textures/entity/villager/villager_baby.png` | 8x8x7 | keeps hat + 14x1x12 straw brim — a genuinely different head, not a scaled adult |
| `PILLAGER` | `textures/entity/illager/pillager.png` | 8x10x8 | **keep `nose`** (2x4x2 to z=-6); hide `hat` (8x12x8 inflated shell) |

**Headgear is another texture, not another mesh.** `VillagerProfessionLayer` adds no geometry for a
profession — it re-submits the *same* villager model with `profession/<name>.png` — so any vanilla
villager profession's headgear is available as one extra `entityCutout` draw of the head you already
baked, and several can be stacked. `GhostHeadLayer` draws three (`villager.png`, then
`profession/armorer.png` for the welding mask, then `profession/cartographer.png` for the eyepiece),
which is a face no real villager could wear. Two things to know before doing it again:

- Most of that headgear lives on the **`hat`** cube, so it needs `hat.visible = true` — the opposite
  of what the Marine does. That is safe because `villager.png` is fully transparent across the hat
  cube's whole UV region (u32–64, v0–18). Hide only the nested `hat_rim` (the 16x16 straw brim).
- To light part of a borrowed head, author a mod PNG **on vanilla's UV layout** and submit it as a
  further draw — cutout first if it must cover opaque vanilla pixels, then `RenderTypes.eyes`.
  `ghost_visor.png` is six texels doing exactly that; `tools/gen_ghost_visor.py` records where those
  coordinates were measured from, since nothing enforces them.

Two traps, both verified against the decompiled 26.1.2 source and recorded in
[neoforge-api-notes.md](neoforge-api-notes.md):

- **`VILLAGER_NO_HAT` is not how you drop a villager's hat.** `createNoHatModel` clears the whole head
  recursively and leaves no face at all. Bake the ordinary layer and set `hat.visible = false`.
- **Hiding the pillager's `hat` is not a mod-specific correction** — vanilla's own `IllagerModel`
  constructor does exactly that. Its `nose`, on the other hand, is half of what makes the face read as
  a pillager, so keeping it is the whole reason to borrow that layer instead of the villager one.

### Distinguishing two humanoids in a crowd

They share a silhouette, so pick **one head treatment** and **one thing outside the villager outline**:

- Marine — adult villager face under a **helmet with no faceplate** (four separate plates: crown, back,
  two ear covers). A single enclosing cube would be the cheaper build and would throw away the only
  thing on the model that says who these soldiers are.
- Firebat — **pillager** head, deliberately **bare**. Plating it would bury the one feature that says it
  is not a Marine. Its outside-the-outline element is the fuel tank on the back.

Team colour is **not** a model question. `entity/TeamColors.dyeArmor` is a no-op for units with bespoke
models (they render no armour slot), so a unit's colour lives in its own PNG.

---

## 3. Glow

Every unit ships two textures: `<unit>.png` and `<unit>_glow.png`, the emissive pass that
`client/UnitGlowLayer` re-submits the whole model with. **Everything not glowing must be transparent in
the glow PNG.** Keep the glowing cubes few and name them in the model's class doc, since nothing
enforces it. The borrowed head is a separate draw and so is never glowed — correct, a face does not
emit light.

---

## 4. Entity-side shape

- `extends Monster implements RangedAttackMob` — never `Zombie`/`Skeleton` (see
  [neoforge-api-notes.md](neoforge-api-notes.md) for the baggage those carry).
- **`implements Organic`** if the unit should be able to board a Bunker. That is the entire cost; the
  rule lives on `entity/terran/BunkerEntity.boardable` and names no unit.
- Goal ladder: copy `MarineEntity`'s verbatim. `StuckWanderGoal` at -1, `FloatGoal` + `SiegeBlockGoal`
  at 0, the attack goal at 4, look goals at 6; `RetaliateGoal` at -1 and `FactionTargetGoal` at 1 in the
  target selector; then `CommandableGoals.install(...)`.
- Use **`entity/ai/UnitRangedAttackGoal`**, not vanilla's, for anything `Organic` — the radius has to be
  live so a garrisoned unit picks up `Garrison.rangeBonusFor(this)`. Note it fires on cadence at *any*
  range once it has line of sight; a short-range attack needs its own distance guard in
  `performRangedAttack` (the Firebat has one).
- `setPersistenceRequired()` in the constructor, and `getAmbientSoundInterval()` returning 1200.
- Deal damage through the four helpers in `entity/ai/` + `combat/SuicideBlast`, never `hurtServer`
  directly. Each takes the attacker's own damage type alongside its particle and sound.
- Entity type: `.sized(0.6f, 1.95f)` — vanilla's villager footprint, because the model is one. 1.95
  keeps the pathfinder's `floor(height + 1)` at two nodes; a weapon overhanging the front is fine and
  is left overhanging.

---

## 5. Model-source constraints

`tools/blockbench_export.py` rewrites `texOffs` literals in place, so the class must give it **one
editable `texOffs(int, int)` literal per cube**. That forbids: named UV constants, a helper method
called more than once, a loop building several parts, and one `CubeListBuilder` variable shared by
several parts. No `.mirror()`, and part names must be globally unique within the model.
`ModelUvLayoutTest` fails the build if two islands ever overlap. Full detail in
[texturing.md](texturing.md).

---

## 6. Adding one: the checklist

Java and tables:

- `stats/UnitStats` — the `UnitStat` entry, plus add it to `TERRAN_ROSTER`
- `entity/terran/<Unit>Entity`
- `client/terran/<Unit>Model`, `<Unit>RenderState`, `<Unit>Renderer`, `<Unit>HeadLayer`
- `combat/AsteriskCraftDamageTypes` — a key, and add it to `ALL`
- `AsteriskCraft.java` — entity type, 2 spawn eggs, 3 sound events, 2 creative-tab lines, 1 attribute line
- `client/AsteriskCraftClient.java` — `ModelLayerLocation`, layer definition, renderer
- `race/Races.TERRAN` — one `.unit(...)` line on the roster builder
- `building/ProductionKind.TERRAN_BASE` — a command-card button
- `command/UnitLabels` — a one-letter code (unique **within** the race; S/M/F/G/B/T are taken)

Data and assets:

- `data/asteriskcraft/damage_type/<type>.json`, and the `no_knockback` / `panic_causes` tag files
- `data/asteriskcraft/build_scripts/terran.txt` — the AI has to be able to build it, and its header
  lists the roster
- `assets/.../lang/en_us.json` — 2 spawn-egg names, the entity name, 3 subtitles, and **both**
  `death.attack.<type>` and `death.attack.<type>.player`
- `assets/.../sounds.json` + `sounds/mob/<unit>/*.ogg`. The `import/*.oga` files are byte-identical Ogg —
  copy and rename, no transcode
- `items/` and `models/item/` JSON for both spawn eggs (4 files, copy the Marine's and sed the name)
- `tools/gen_command_icons.py` — add the unit to `TRAINED` and `EGGS`, then re-run it
- `tools/blockbench_export.py` — add it to `UNITS`
- `tools/gen_<unit>_texture.py` — a new generator, modelled on `gen_marine_texture.py`
- `command/UnitLabels` letters and command-card **columns** are both per-race scarce: a card column
  holds two buttons (`ProductionCardLayoutTest`), so a third combat unit opens a new one

Tests (each fails the build if missed, which is the point):

- `stats/UnitStatsTest` — bump the roster count
- `combat/DamageTypeResourceTest` — bump the damage-type count
- `tools/ModelGeometry.MODELS`, `client/protoss/ModelBakeTest`, `entity/UnitFootprintTest` — one line each

Docs: a slice entry in [shaping.md](shaping.md); [CLAUDE.md](../CLAUDE.md) only if the unit establishes a
new convention.

### Build order

The model/texture pipeline is circular the first time, so:

1. Write everything above, with hand-guessed `texOffs` values and `LayerDefinition.create(mesh, 128, 128)`.
2. `./gradlew test` — **`ModelUvLayoutTest` is expected to fail** (a hand-written layout overlaps), but
   `ModelGeometryDumpTest` writes `build/model-export/<unit>.json` anyway.
3. `python tools/blockbench_export.py <unit>` — packs the islands and rewrites the `texOffs` literals and
   the texture size in your Java. It tolerates the not-yet-existing PNGs.
4. `./gradlew test` — refreshes the dump against the packed layout.
5. `python tools/gen_<unit>_texture.py` and `python tools/gen_command_icons.py`.
6. `python tools/blockbench_export.py <unit>` again, to link the now-existing PNGs into the `.bbmodel`.
7. `./gradlew test` — green.

Then hand-paint in Blockbench per [texturing.md](texturing.md); the generated PNG is a correct layout
with placeholder art on it, not a finished skin.
