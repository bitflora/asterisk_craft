# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

AsteriskCraft is a NeoForge mod that brings a StarCraft-style RTS loop (gather → build → produce units → destroy the enemy base) into Minecraft. Mod id `asteriskcraft`, package root `net.bitflora.asteriskcraft`. Targets **Minecraft 26.1.2 + NeoForge 26.1.2.80** (Java 25 toolchain) — a version new enough that a lot of general MC-modding knowledge is stale for it.

**Read [docs/shaping.md](docs/shaping.md) first** for any feature work: it's the living requirements/design doc, listing the shipped/in-progress slices (V1–V5) and the specific decisions behind the architecture (e.g. why faction hostility must never hardcode which side is the enemy). Check its "Slices" table to see what's already `[DONE]` before assuming a feature doesn't exist.

**Read [docs/neoforge-api-notes.md](docs/neoforge-api-notes.md) before calling an unfamiliar NeoForge/MC API.** It documents specific API shapes verified against this exact version's decompiled source (e.g. `Mob.finalizeSpawn` being deprecated in favor of `EventHooks.finalizeMobSpawn`, the Codec-based `ValueOutput`/`ValueInput` save API, sun-burn being tag-gated not class-gated) and where to find the decompiled source jars to verify anything not already listed there. Add to it when you discover another non-obvious API behavior by digging through jars.

## Commands

- `./gradlew test` — run the JUnit test suite. Tests run inside a bootstrapped NeoForge environment (`neoForge.unitTest.enable()` in build.gradle), so real registries/blocks/items/entity types resolve in-test.
  - Single test class: `./gradlew test --tests "net.bitflora.asteriskcraft.building.GatewayEconomyTest"`
  - Single test method: `./gradlew test --tests "net.bitflora.asteriskcraft.building.GatewayEconomyTest.zealotCostMatchesDesign"`
- `./gradlew compileJava` — fast compile-only check.
- `./gradlew runData` — run data generators (see `data` run config in build.gradle; outputs to `src/generated/resources/`).
- Don't try to launch the game as a test, I will do that myself

**Important test-bootstrap limitation:** block/item **tags** are not bound in the JUnit bootstrap environment, so tag-dependent behavior (e.g. which block a Probe's tag-based harvest yields) can't be unit-tested — it's verified via `runClient` instead. See `ProbeEconomyTest`'s javadoc for the pattern: keep unit tests to pure logic/constants, and note in the test class why tag-dependent behavior is excluded.

## Architecture

Faction hostility is the single mechanism all targeting/combat logic must go through, kept deliberately generic so later versions can add race selection and PvP without touching targeting code:
- `faction/Faction` — enum (`NEUTRAL`, `PROTOSS`, `ZERG`) with `isEnemy(other)`: strictly cross-faction, NEUTRAL fights no one.
- `faction/FactionAttachments` — a NeoForge data attachment tagging any entity with its faction; `areEnemies(a, b)` is the one function combat code should call. No targeting code should ever check entity class/type directly.
- `entity/ai/FactionTargetGoal` — a `NearestAttackableTargetGoal<LivingEntity>` parameterized with a `TargetingConditions.Selector` that delegates to `FactionAttachments.areEnemies`. Every faction-aware combat unit uses this instead of vanilla player-targeting goals.

Production buildings (Nexus, Gateway, and future ones) share a common shape, factored into `building/`:
- `building/ArmyBank` — the shared per-faction resource pool (a level attachment, not owned by any one building): Nexus + Gateway (Protoss) and all three Hives (Zerg) act as "linked chests" onto one `Container` per faction via `building/ArmyLinkedContainer`, instead of each holding its own independent inventory. Sized 27 slots (Protoss) / 81 slots (Zerg).
- `building/ResourceBank` — atomic multi-item cost extraction from a `Container` (e.g. "50 wood AND 50 cobblestone, all or nothing"); operates generically whether that container is a shared `ArmyBank` view or a plain container.
- `building/SpawnSpots` — finds an open, safely-footed spot near a building to place a freshly produced unit.
- `building/BuildingLayouts` — code-defined multiblock structures (a `Map<BlockPos, BlockState>` per building) plus `place(...)` to stamp one into the world, used both by world-bootstrap placement and by warp-in kits.
- `building/BuildingKitItem` — generic "right-click the ground to warp in a building" item; reusable across building types via a layout supplier + core-block offset.
- Each building is a `BaseEntityBlock` + `BlockEntity` pair (`NexusBlock`/`NexusBlockEntity`, `GatewayBlock`/`GatewayBlockEntity`) with its own production queue, ticking via `createTickerHelper`. A building's `preRemoveSideEffects` deliberately skips `super` (see `ArmyLinkedContainer`) so destroying one building never drops/clears the whole army's shared bank.

Units are plain hostile mobs, not repurposed Zombies/Skeletons: `entity/protoss/ZealotEntity` and `entity/zerg/ZerglingEntity` extend `Monster` directly (melee, via `MeleeAttackGoal`), and `entity/protoss/DragoonEntity`/`entity/zerg/HydraliskEntity` extend `Monster implements RangedAttackMob` (ranged hitscan, via `RangedAttackGoal`) — not `Zombie`/`Skeleton`, since those vanilla classes carry unwanted baggage (Hard-mode reinforcement spawning and water→Drowned conversion on `Zombie`; a silently-equipped bow and Stray freeze-conversion on `Skeleton`) that was never neutralized in code. See docs/neoforge-api-notes.md for the verified findings. Combat units live in `entity/protoss`/`entity/zerg` subpackages (workers and structures too — `ProbeEntity`/`PhotonCannonEntity` under `protoss`, `DroneEntity` under `zerg`); shared faction-generic contracts (`Shielded`, `TeamColors`, `FactionSpawnEggItem`) and the goal library (`entity/ai`) stay at the neutral top level. Each unit's client renderer/model lives in the matching `client/protoss`/`client/zerg` subpackage — every unit has its own bespoke `MobRenderer`/model (not a reused vanilla `ZombieRenderer`/`SkeletonRenderer`). `entity/TeamColors` dyes a leather chestplate per faction for cheap visual team identity.

Registration is centralized in `AsteriskCraft.java` (blocks, items, block entities, entity types, creative tab, entity attributes — all via `DeferredRegister`) with a separate client-only companion `client/AsteriskCraftClient.java` (`@Mod(dist = Dist.CLIENT)`) for renderers/model layers, since client-only classes must never load on a dedicated server.

`game/GameBootstrap` places the player's starting base (Nexus + resource chest) on first world join rather than at server start, because the player's chunk is guaranteed loaded with settled terrain height by then; `game/GameAttachments` holds the resulting level-scoped saved state (has-bootstrapped flag, Nexus position).
