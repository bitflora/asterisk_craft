---
shaping: true
---

# AsteriskCraft — StarCraft RTS gameplay in Minecraft (NeoForge)

## Context

Mod in `C:\Users\timja\code\asterisk-craft` (formerly `star-mine`). Goal: bring the StarCraft loop — gather → build → produce units → destroy the enemy base — into Minecraft. The player (Protoss) starts with a Nexus; an AI Zerg starts with 3 Hives a few hundred blocks away. Both gather resources and produce units. Destroy all Hives to win; lose the Nexus and you lose. Later versions add race selection and PvP, so nothing may hardcode "player = Protoss".

**Target:** Minecraft 26.1 + NeoForge 26.1 (current stable modding line, successor to 1.21.1). Mod id: `asteriskcraft`. Package root: `net.bitflora.asteriskcraft`.

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

### R5 detail — select + orders

Expanded from the top-level R5 to pin down the exact control scheme (see shape A8, plan in [R5-command-plan.md](R5-command-plan.md)). "Friendly" means a unit whose `Faction` attachment equals the commanding player's controlled faction (PROTOSS for the human in MVP) — resolved generically, never by player/entity class.

| ID | Requirement | Status |
|----|-------------|--------|
| R5.1 | Left-click a friendly unit → selection becomes exactly that unit | Must-have |
| R5.2 | Shift+left-click a friendly unit → toggle it in/out of the current selection | Must-have |
| R5.3 | Ctrl+left-click a friendly unit → select all friendly units of the same type within radius of the player | Must-have |
| R5.4 | Ctrl+Shift+left-click a friendly unit → toggle all friendly units of that type within radius in/out of the selection | Must-have |
| R5.5 | Right-click an enemy unit → all selected units attack (focus) that target | Must-have |
| R5.6 | Right-click a block → all selected units move to that block | Must-have |
| R5.7 | Right-click empty air → all selected units move toward the look direction, to the farthest point in view | Must-have |
| R5.8 | Selection is per-player; only the commanding player's own-faction units are selectable | Must-have |
| R5.9 | Selected units are visually distinguished (glow) | Must-have |
| R5.10 | Ordered units path across chunk boundaries without freezing in unloaded chunks | Must-have |
| R5.11 | Command inputs only fire while the Command Crystal is held, so normal mining/placing/attacking is untouched | Must-have |
| R5.12 | Right-click a harvestable block with a Probe selected → the Probe mines that block | Must-have |

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
| **A8** | **Command Crystal + StarCraft click semantics** (chosen; revised from the earlier "command wand" sketch — see R5 detail + [R5-command-plan.md](R5-command-plan.md)) — a held marker item enables command mode; **left-click = select** (plain / Shift-toggle / Ctrl-all-of-type-in-radius / Ctrl+Shift-toggle-all-of-type), **right-click = order** (enemy = attack, block = move, air = move toward look). All input captured client-side (Ctrl is not server-known) and sent as one `CommandInputPacket`; selection is a per-player attachment; orders are a `CommandOrder` attachment read by commanded goals on each unit. Probes are commandable too: they honor MOVE and a **MINE** order (right-click a harvestable block) that reuses their existing harvest logic |
| **A9** | **Zerg director with literal Drones** (chosen: A9-B) — per-Hive brain (`HiveBlockEntity.serverTick`): maintains Drone count, Drones mine `#asteriskcraft:harvestable` blocks into the Hive's own inventory (exposed as an item capability so the existing Probe delivery reuses verbatim), a global `ZergDirector` (`ServerTickEvent.Post`, state in level attachments) spends pooled Hive items on escalating mixed **Zergling + Hydralisk** waves and issues attack-move at the Nexus; killing Drones starves it, killing Hives removes production. **Building destruction:** cores (Nexus + Hive) implement a shared `FactionCore` with siege HP; all combat units carry a faction-generic `SiegeBlockGoal` that batters an enemy core down (and digs through blocks that stall their path) — the removal fires the win/lose via the core BE's `preRemoveSideEffects`. So both outcomes are reached through real combat. |

## Architecture sketch

Package root `net.bitflora.asteriskcraft`:

- `faction/` — Faction, FactionSavedData, FactionAttachment, FactionRelations
- `building/` — WarpInHandler, BuildingKitItem, NexusBlock(Entity), GatewayBlock(Entity), PhotonCannonBlock(Entity), HiveBlock(Entity), structure templates in `data/asteriskcraft/structure/`
- `entity/` — ProbeEntity, DroneEntity; `ai/` goals: HarvestBlockGoal, DeliverToContainerGoal, FactionTargetGoal, CommandedMoveGoal
- `command/` — CommandCrystalItem (held marker), CommandInputPacket + client input handler, CommandInputResolver (server), PlayerSelection attachment, CommandOrder attachment + `ai/CommandedMoveGoal`/`ai/CommandedAttackGoal`
- `director/` — ZergDirector (server tick handler, wave scheduler)
- `game/` — GameState saved data (initialized/won/lost) + world bootstrap. Implemented as first-player-join placement rather than server start (see Status note below) — no slash commands anywhere in the MVP.

Key technical notes:
- Units marching across the map need **chunk tickets** attached while they hold an active order — otherwise they freeze in unloaded chunks. **V3 sidesteps this** by placing the Zerg base ~110 blocks away (inside typical simulation distance) so wave units and Drones path through already-loaded chunks; chunk tickets (for R5.10 and a more distant base) remain an open spike — see [R5-command-plan.md](R5-command-plan.md).
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

**V2a — Gateway + Zealots/Dragoons. `[DONE]`** Warp-in kit framework (A2); Gateway production (A6) of Zealots (zombies, 50 wood + 50 cobble) and Dragoons (skeletons, iron); rally points. *Demo: craft Gateway kit, warp it in, produce a mixed squad.*

**V2b — Command Crystal: select + orders (R5).** The A8 command scheme — held Command Crystal enables command mode; left-click select (plain / Shift-toggle / Ctrl-type-in-radius / Ctrl+Shift-toggle-type), right-click order (enemy=attack, block=move, air=move-toward-look). Client input capture → `CommandInputPacket` → per-player selection attachment + `CommandOrder` attachment read by commanded goals on units; selection glow; chunk-load ordered units. Detailed plan: [R5-command-plan.md](R5-command-plan.md). *Demo: hold the Crystal, click-select a mixed squad, right-click a Hive to send them attacking; Ctrl-click one Zealot to grab the whole group.*

**V3 — Zerg AI + win/lose. `[DONE]`** 3 Hives placed at first join ~110 blocks east of the Nexus (inside simulation distance — no chunk tickets needed, see the technical note below), each seeded with resources + a surface resource garden + starter Drones. Per-Hive Drone mining loop (`HiveBlockEntity`); `ZergDirector` (`ServerTickEvent.Post`) runs an escalating **mixed Zergling + Hydralisk** wave schedule, spending pooled Hive resources and attack-moving the wave at the Nexus with an "under attack!" ping. Win/lose is reached through combat: all combat units (Zealot/Dragoon/Zergling/Hydralisk) carry a `SiegeBlockGoal` that batters down enemy `FactionCore` blocks (Nexus + Hives, which have siege HP) and digs through obstructing blocks; a core's removal fires the outcome via `preRemoveSideEffects` — victory on the last Hive razed, defeat on the Nexus razed. Unit tests cover the Zerg cost mirror, wave-escalation curves, and the win/lose decision. *Demo: full playable game loop, both outcomes reachable.*

**V4 — Photon Cannon + real costs.** Cannon kit + auto-targeting (A7); wire all production/kit costs to actual item consumption (R7); first balance pass on wave scaling. *Demo: cannons repel a wave; production halts when the chest is empty.*

**V5 — Polish + extensibility groundwork.** Control groups (number-key bindings) on the Command Crystal; per-player client-only selection glow (replacing V2b's shared server glow); unit team-color visuals, sounds, particles; document the faction/race registry for future Terran/player-Zerg + PvP; optional start on tactical map GUI. *Demo: comfortable command UX in a full match.*

## Verification

- `./gradlew test` for deterministic logic (faction rules, multiblock layout, economy constants) — tags and item components aren't bound in the JUnit bootstrap, so tag-dependent behavior (e.g. block→item yield mapping) isn't unit-testable and needs the checks below instead.
- `./gradlew runClient` per slice; execute each slice's demo script manually.
- `./gradlew runServer` at V3+ — all logic must be server-side safe (dedicated-server crash is the classic modding failure).
- V3 end-to-end: play one full match to victory (raze 3 Hives) and one to defeat (let Zerg kill the Nexus).
- Chunk test: order units to attack-move from outside simulation distance of the Hives and confirm they arrive.
