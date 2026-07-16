---
shaping: true
---

# AsteriskCraft — StarCraft RTS gameplay in Minecraft (NeoForge)

## Context

Mod in `C:\Users\timja\code\asterisk-craft` (formerly `star-mine`). Goal: bring the StarCraft loop — gather → build → produce units → destroy the enemy base — into Minecraft. The player (Protoss) starts with a Nexus; an AI Zerg starts with 3 Hives a few hundred blocks away. Both gather resources and produce units. Destroy all Hives to win; lose the Nexus and you lose. Later versions add race selection and PvP, so nothing may hardcode "player = Protoss".

**Target:** Minecraft 26.1 + NeoForge 26.1 (current stable modding line, successor to 1.21.1). Mod id: `asteriskcraft`. Package root: `com.timja.asteriskcraft`.

## Requirements (R)

| ID | Requirement | Status |
|----|-------------|--------|
| R0 | RTS core loop: gather resources → construct buildings → produce units → destroy enemy base | Core goal |
| R1 | Buildings are multiblock structures; Nexus pre-placed at game start; its destruction = defeat | Must-have |
| R2 | Nexus produces Probes that gather vanilla items non-destructively and deliver them to the nearest chest | Must-have |
| R3 | Gateway produces ground attack units (MVP: repurposed skeletons/zombies) that fight the enemy faction | Must-have |
| R4 | Photon Cannon auto-attacks all enemy-faction targets in range | Must-have |
| R5 | Group select + orders: attack target, attack-move to point, rally | Must-have |
| R6 | AI Zerg: 3 Hives, literal Drones mine resources into Hives, director spends them on units and attack waves; all Hives destroyed = victory | Must-have |
| R7 | Every unit and building has a vanilla-item cost/recipe | Must-have |
| R8 | Faction-generic architecture (race selection + PvP later) | Must-have |
| — | Out of scope: air units, RTS camera, full tech tree, multiplayer balance | Out |

## Selected shape (A, with decided alternatives)

| Part | Mechanism |
|------|-----------|
| **A1** | **Faction system** — `SavedData` faction registry (`PLAYER_PROTOSS`, `AI_ZERG`); NeoForge data attachments tag entities + block entities with a faction id; single `FactionRelations.isEnemy(a,b)` used by all targeting |
| **A2** | **Warp-in kits** (chosen: A2-B) — each building is a craftable kit item; right-click ground → validates footprint → structure template materializes over ~10s with particles/sound. One generic `WarpInHandler` + per-building structure template `.nbt` |
| **A3** | **Nexus** — controller block entity + GUI with Probe production queue; defeat trigger when core block is destroyed; gates crafting of other kits (recipes unlock via advancement granted at game start) |
| **A4** | **Probe** — small custom entity; goal chain: find nearest block in `#asteriskcraft:harvestable` tag → mine-beam N seconds → block enters depleted/cooldown state (never removed) → carry drops to nearest chest near the Nexus |
| **A5** | **Vanilla-item economy** (chosen: user variant of A5-B) — resources are real items in chests/building inventories; kits are crafted normally; production buildings consume items from their internal inventory or an adjacent chest |
| **A6** | **Gateway** — production block entity; spawns **Zealots** (repurposed Zombie, melee) and **Dragoons** (repurposed Skeleton, ranged) with goal selectors replaced (faction targeting, no sunburn, no player aggression) + colored leather armor for team identity; rally point support |
| **A7** | **Photon Cannon** — block entity ticker scans radius for nearest enemy-faction entity → fires a projectile; simple, no power system in MVP |
| **A8** | **Command wand** (chosen: A8-A now, map GUI later) — shift-right-click unit or sweep radius = select; right-click ground = attack-move; right-click enemy entity/building = focus target; selection stored per-player |
| **A9** | **Zerg director with literal Drones** (chosen: A9-B) — per-Hive brain in `SavedData`: maintains Drone count, Drones mine `#asteriskcraft:harvestable` blocks into Hive inventory, director spends items on Zerglings (repurposed zombies), escalating wave timer issues attack-move at the Nexus; killing Drones starves it, killing Hives removes production |

## Architecture sketch

Package root `com.timja.asteriskcraft`:

- `faction/` — Faction, FactionSavedData, FactionAttachment, FactionRelations
- `building/` — WarpInHandler, BuildingKitItem, NexusBlock(Entity), GatewayBlock(Entity), PhotonCannonBlock(Entity), HiveBlock(Entity), structure templates in `data/asteriskcraft/structure/`
- `entity/` — ProbeEntity, DroneEntity; `ai/` goals: HarvestBlockGoal, DeliverToContainerGoal, FactionTargetGoal, CommandedMoveGoal
- `command/` — CommandWandItem, PlayerSelection (per-player), order networking packets
- `director/` — ZergDirector (server tick handler, wave scheduler)
- `game/` — GameState saved data (initialized/won/lost) + world bootstrap. Implemented as first-player-join placement rather than server start (see Status note below) — no slash commands anywhere in the MVP.

Key technical notes:
- Units marching across the map need **chunk tickets** attached while they hold an active order — otherwise they freeze in unloaded chunks. Keep Hives within ~300–400 blocks.
- All game logic server-side; wand selection UI feedback via glowing outline effect (vanilla `Glowing` works for MVP).
- "Non-destructive mining": harvested blocks switch to a depleted blockstate (visual dim) with a regen cooldown, never removed from the world.

## Costs (wood/cobble/iron economy; amounts get a balance pass in V4)

The whole economy runs on three vanilla resources Probes/Drones can harvest non-destructively: **wood** (logs, `#minecraft:logs`), **cobblestone** (from stone), and **iron** (from iron ore). Zerg costs mirror Protoss exactly.

| Protoss | Cost | Zerg equivalent |
|---------|------|-----------------|
| Probe | 50 wood **or** 50 cobblestone | Drone (same) |
| Zealot (Zombie, melee) | 50 wood **and** 50 cobblestone | Zergling (same) |
| Dragoon (Skeleton, ranged) | 10 iron ingots | Hydralisk (same) |
| Gateway kit | 200 wood + 200 cobblestone | Spawning Pool (baked into Hive for MVP) |
| Photon Cannon kit | 100 wood + 100 cobblestone + 20 iron | Sunken Colony (post-MVP) |
| Nexus / Hive | Not craftable in MVP (pre-placed) | — |

## Slices (each ends demo-able)

**V1 — Mod skeleton + Nexus + Probe economy. `[DONE]`** MDK setup for NeoForge 26.1; faction core (A1); world bootstrap places the Nexus + starting chest on first player join (no slash commands — moved off server-start after testing showed the heightmap isn't settled that early); Nexus block entity + GUI queue; Probe entity that non-destructively harvests wood/stone/iron ore and delivers to the nearest chest; Probe costs 50 wood or 50 cobble from the Nexus's adjacent chest. Unit tests cover faction rules, the Nexus multiblock layout, and economy constants. *Demo: create a new world, find the Nexus standing near you, queue a Probe, watch it mine and fill a chest.*

**V2 — Gateway + Zealots/Dragoons + command wand.** Warp-in kit framework (A2); Gateway production (A6) of Zealots (zombies, 50 wood + 50 cobble) and Dragoons (skeletons, iron); command wand select + attack-move/focus orders (A8); rally points. *Demo: craft Gateway kit, warp it in, produce a mixed squad, order it as a group to destroy a target.*

**V3 — Zerg AI + win/lose.** 3 Hive structures + creep placed at game start; ZergDirector: Drone mining loop, Zergling production, escalating attack waves at the Nexus (A9); victory on last Hive destroyed, defeat on Nexus core destroyed; "You are under attack!" ping. *Demo: full playable game loop, both outcomes reachable.*

**V4 — Photon Cannon + real costs.** Cannon kit + auto-targeting (A7); wire all production/kit costs to actual item consumption (R7); first balance pass on wave scaling. *Demo: cannons repel a wave; production halts when the chest is empty.*

**V5 — Polish + extensibility groundwork.** Control groups on the wand; unit team-color visuals, sounds, particles; document the faction/race registry for future Terran/player-Zerg + PvP; optional start on tactical map GUI. *Demo: comfortable command UX in a full match.*

## Verification

- `./gradlew test` for deterministic logic (faction rules, multiblock layout, economy constants) — tags and item components aren't bound in the JUnit bootstrap, so tag-dependent behavior (e.g. block→item yield mapping) isn't unit-testable and needs the checks below instead.
- `./gradlew runClient` per slice; execute each slice's demo script manually.
- `./gradlew runServer` at V3+ — all logic must be server-side safe (dedicated-server crash is the classic modding failure).
- V3 end-to-end: play one full match to victory (raze 3 Hives) and one to defeat (let Zerg kill the Nexus).
- Chunk test: order units to attack-move from outside simulation distance of the Hives and confirm they arrive.
