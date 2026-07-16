# MC 26.1.2 / NeoForge 26.1.2.80 API notes

Project targets Minecraft 26.1.2 + NeoForge 26.1.2.80 (see [gradle.properties](../gradle.properties)). This version is new enough that a lot of general MC-modding knowledge (including model training data) is stale for it. Verify against decompiled source before guessing, using the paths below.

## Where to find real source for this exact version

- Full decompiled + mapped Minecraft source (matches this project's mappings): `~/.gradle/caches/neoformruntime/intermediate_results/sourcesAndCompiledWithNeoForge_*_output.jar` — `unzip -l` it, then extract specific `net/minecraft/...` paths to read real method signatures instead of guessing.
- NeoForge-specific classes (`DeferredRegister`, `EventHooks`, attachment API, etc.): `~/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/26.1.2.80/*-sources.jar`.
- Vanilla data (tags, recipes) for cross-checking format: `~/.gradle/caches/neoformruntime/artifacts/minecraft_26.1.2_client.jar` under `data/minecraft/...` and `assets/minecraft/...`.

## Concrete API facts (verified while building V2a — Gateway/Zealot/Dragoon)

- **Sun-burn is tag-gated, not class-gated.** `Mob.aiStep()` only calls `burnUndead()` if `this.getType().is(EntityTypeTags.BURN_IN_DAYLIGHT)`. That tag (`data/minecraft/tags/entity_type/burn_in_daylight.json`) is an explicit list of vanilla entity type IDs (`minecraft:zombie`, `minecraft:skeleton`, etc.). A **custom** `EntityType` that extends `Zombie`/`Skeleton` (like this mod's `ZealotEntity`/`DragoonEntity`) is automatically immune to sun damage — no override needed — because its own registry id was never added to that tag.
- **`Mob.finalizeSpawn(...)` is `@Deprecated`/override-only** in this version. External callers (anything spawning a mob programmatically, not overriding the method) must call `net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(mob, level, difficulty, spawnType, spawnData)` instead of `mob.finalizeSpawn(...)` directly — it fires `FinalizeSpawnEvent` then delegates.
- **`ValueOutput`/`ValueInput`** (block entity / entity NBT-replacement save API) use `store(String, Codec<T>, T)` and `read(String, Codec<T>) -> Optional<T>` — always Codec-based, no raw-Class overloads. For a custom enum, implement `StringRepresentable` and build `Codec<T> = StringRepresentable.fromEnum(T::values)` (see `Faction`); for a list, `codec.listOf()`.
- **`DyedItemColor` is a single-field record**: `DyedItemColor(int rgb)` — the old boolean "show in tooltip" param is gone. Apply via `stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgbInt))`.
- **`DeferredRegister.Items.registerItem(String, Function<Item.Properties, ? extends I>)`** is the way to register a plain custom `Item` subclass; `registerSimpleBlockItem` is only for `BlockItem`.
- **`EntityRenderersEvent.RegisterRenderers#registerEntityRenderer`** signature is `<T extends Entity> void registerEntityRenderer(EntityType<? extends T> type, EntityRendererProvider<T> provider)`. This means a custom `EntityType<MySubclass>` (e.g. `EntityType<ZealotEntity>` where `ZealotEntity extends Zombie`) can directly reuse a vanilla renderer constructor reference like `ZombieRenderer::new`/`SkeletonRenderer::new` — no custom renderer/model needed for a reskinned vanilla-mob-alike. Combine with a dyed leather chestplate (`DyedItemColor`) for cheap team-color visual identity.
- **`NearestAttackableTargetGoal<T>`** has a constructor overload taking a `TargetingConditions.Selector` (`(LivingEntity target, ServerLevel level) -> boolean`). Prefer subclassing/parameterizing this with `LivingEntity.class` + a custom selector over writing a whole new `TargetGoal` from scratch for custom targeting rules (e.g. faction-based).
- **Recipes moved to singular path**: `data/<namespace>/recipe/*.json` (not `recipes/`), and shaped-recipe `result` is `{"id": "...", "count": n}` (the old `"item"` key is gone).
- **`UseOnContext`** (right-click-on-block item handling) exposes `getLevel()`, `getClickedPos()`, `getClickedFace()`, `getPlayer()`, `getItemInHand()` — enough to implement placement-style items without needing `BlockItem`.

See [BuildingKitItem.java](../src/main/java/net/bitflora/asteriskcraft/building/BuildingKitItem.java), [GatewayBlockEntity.java](../src/main/java/net/bitflora/asteriskcraft/building/GatewayBlockEntity.java), [ZealotEntity.java](../src/main/java/net/bitflora/asteriskcraft/entity/ZealotEntity.java), and [entity/ai/FactionTargetGoal.java](../src/main/java/net/bitflora/asteriskcraft/entity/ai/FactionTargetGoal.java) for these applied in practice.
