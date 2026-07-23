# Hand-painting unit textures in Blockbench

Unit models are Java-defined geometry (`createBodyLayer()` in `client/protoss/*Model.java` and
`client/zerg/*Model.java`). Their textures used to be generated procedurally as flat colour zones; they
are now **hand-painted assets**, and every cube owns its own UV island so it can be painted
independently. `tools/blockbench_export.py` is the bridge between the Java and Blockbench.

## Painting a unit

1. Refresh the geometry dump (only needed if a model's Java changed):
   ```bash
   ./gradlew test
   ```
2. Regenerate the Blockbench project:
   ```bash
   python tools/blockbench_export.py zergling
   ```
3. Open `tools/blockbench/zergling.bbmodel` in Blockbench. The project is a **Modded Entity** (Forge
   1.17+ / Mojmaps) model with the real PNGs linked from
   `src/main/resources/assets/asteriskcraft/textures/entity/`.
4. Paint, then **Save the texture** in Blockbench (not "Save Project"). Because the textures are linked
   rather than embedded, this writes straight back into `src/main/resources/`.
5. In game, `F3+T` reloads resource packs and picks up the new texture with no restart.

Each unit has two textures: the base skin (`<unit>.png`) and the emissive pass (`<unit>_glow.png`) that
`UnitGlowLayer` renders at full brightness. Both are listed in the Blockbench project; switch between
them in the Textures panel. Anything you leave transparent in the glow texture simply doesn't glow.

The `.bbmodel` files under `tools/blockbench/` are **regenerable artefacts** — the Java is the source of
truth for geometry. Don't edit geometry in Blockbench expecting it to flow back; change the model class
and re-export. (Blockbench's own *Export Java Entity* does round-trip correctly if you ever need it —
`blockbench_export.py` verifies that on every run — but the Java here carries animation code that a
Blockbench export would discard.)

## What the tool does

Textures are laid out with **box UV**: each cube occupies a `2*(width+depth)` by `depth+height`
rectangle. Originally every cube of a material pointed its `texOffs` at the same solid-colour rectangle,
which made hand-painting impossible — painting an eye smeared it onto every cube sharing that corner.

On a model that still has overlapping islands, the tool performs a one-time migration: it shelf-packs
every cube into its own island (1-texel gutter), remaps the existing pixels onto the new layout so the
unit renders **pixel-identically**, rewrites the `texOffs(...)` values and
`LayerDefinition.create(mesh, W, H)` in the Java, and emits the `.bbmodel`.

It is **idempotent**: a model whose islands already don't overlap is left completely alone and only its
`.bbmodel` is re-emitted, so re-running can never shuffle artwork you have already painted.

`ModelUvLayoutTest` fails the build if any two cubes' islands ever overlap or fall outside the texture.

## Constraint on model source

Per-cube UV islands need **one editable `texOffs(int, int)` literal per cube**, so a model class must
not build several cubes through one call site. The tool refuses (without touching any file) and names
the cause if it finds:

- named UV constants — `texOffs(GOLD_U, GOLD_V)`; inline them as literals;
- a helper method called more than once — write the parts out instead;
- a loop building several parts — write them out instead;
- one `CubeListBuilder` variable reused by several parts — build each part its own.

`DragoonModel` (a 4×-called leg helper) and `PhotonCannonModel` (an 8-iteration petal loop, a shared
`ring` builder, and four material constants) were restructured for exactly this reason. The verbosity is
the cost of every cube being independently paintable.

One consequence worth knowing: repeated identical parts — the Photon Cannon's eight petals, the
Dragoon's four legs — now each get their own island, so painting all eight petals alike means painting
them eight times. If you'd rather they shared one island (paint once, radial symmetry for free), that's
a deliberate change to make: point those cubes at the same `texOffs` and relax `ModelUvLayoutTest` to
allow exact-duplicate islands.
