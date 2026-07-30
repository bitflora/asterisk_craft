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
| R2 | Nexus produces Probes that gather vanilla items non-destructively and deliver them straight into the Nexus | Must-have |
| R3 | Gateway produces ground attack units (MVP: repurposed skeletons/zombies) that fight the enemy faction | Must-have |
| R4 | Photon Cannon auto-attacks all enemy-faction targets in range | Must-have |
| R5 | Group select + orders: attack target, attack-move to point, rally | Must-have |
| R6 | AI Zerg: 3 Hives, literal Drones mine resources into Hives, director spends them on units and attack waves; all Hives destroyed = victory | Must-have |
| R7 | Every unit and building has a vanilla-item cost/recipe | Must-have |
| R8 | Faction-generic architecture (race selection + PvP later) | Must-have |
| R9 | Zerg static defence: a Sunken Colony rooted beside each Hive auto-attacks enemy-faction targets in range | Must-have |
| R10 | Air units: a flyer cruises a fixed height above the terrain, engages ground targets from altitude, and is unreachable by melee ground units | Must-have |
| — | Out of scope: RTS camera, full tech tree, multiplayer balance | Out |

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
| **A2** | **Warp-in kits** (chosen: A2-B) — each building is a craftable kit item; right-click ground → validates footprint → structure template materializes over ~10s with particles/sound. One generic `WarpInHandler` + per-building structure template `.nbt`. **Shipped as:** `BuildingKitItem` + `BuildingTemplates`, stamping `data/asteriskcraft/structure/{nexus,gateway,hive}.nbt` — every building is authored in-game with a structure block and re-exported, not written out in Java. The layout is stamped instantly with the core block entity running its own countdown, rather than a materializing animation — **2 minutes** for a Nexus, **1 minute** for a Gateway, 10s for a Photon Cannon. Throughout that countdown the building is inert (no production, no fire) and stands at **half its HP and shields**; finishing the warp scales what survived back up (`building/WarpInVulnerability`), so a building caught mid-warp comes out having sustained twice the damage — and one warped in under fire can be killed before it ever opens. The starting Nexus skips the warp entirely, since the world simply begins with it standing (R1) |
| **A3** | **Nexus** — controller block entity + GUI with Probe production queue; defeat trigger when core block is destroyed; gates crafting of other kits (recipes unlock via advancement granted at game start). The toughest thing on the map, as the building you lose the game with should be: **325 HP behind 325 shields** (`building/BuildingDefense`) |
| **A4** | **Probe** — small custom entity; goal chain: find nearest block in `#asteriskcraft:harvestable` tag (preferring the same resource type it last mined) → mine-beam N seconds → block enters depleted/cooldown state (never removed) → carry drops straight into the home Nexus's inventory |
| **A5** | **Vanilla-item economy** (chosen: user variant of A5-B) — resources are real items in building inventories; kits are crafted normally; production buildings consume items from a shared per-army inventory (`ArmyBank`), which workers deliver into directly. Every building in an army — Nexus + Gateway for Protoss, all three Hives for Zerg — acts as a "linked chest" (`ArmyLinkedContainer`) onto one pooled `Container` per faction rather than each holding its own independent stock |
| **A6** | **Gateway** — production block entity; spawns **Zealots** (repurposed Zombie, melee) and **Dragoons** (repurposed Skeleton, ranged) with goal selectors replaced (faction targeting, no sunburn, no player aggression) + colored leather armor for team identity; rally point support. **250 HP behind 250 shields**, and a `building/SiegeTarget` but deliberately not a `FactionCore` — an enemy army can raze it, which costs the player their unit production but not the match |
| **A7** | **Photon Cannon** — a stationary `PhotonCannonEntity` (a `Mob`, not a block entity) rather than blocks: being a `LivingEntity` it reuses the whole unit-combat stack instead of duplicating it — HP as an attribute, Protoss shields for free (`implements Protoss`), and **automatic retaliation** (units target/hit it back via the normal `RetaliateGoal`/`FactionTargetGoal`, so no building-aggro special case). It never moves (no move goals, zero speed, unpushable), warps in over 200t — inert but **not** invulnerable: a warping structure stands at half its HP and shields (`building/WarpInVulnerability`), and finishing the warp scales both pools back up, so whatever fraction an attacker shot off the half pool is missing from the full one — i.e. damage landed mid-warp is sustained twice over, and a cannon warping in under fire can be killed before it ever fires. Once warped in, a `CannonFireGoal` auto-fires an instant bolt at the nearest enemy-faction unit — or any vanilla monster — in range, reusing the pure `PhotonCannonTargeting` rule. Warped in by the crafted `photon_cannon_kit` (a `FactionSpawnEggItem`). No power system in MVP |
| **A8** | **Command Crystal + StarCraft click semantics** (chosen; revised from the earlier "command wand" sketch — see R5 detail + [R5-command-plan.md](R5-command-plan.md)) — a held marker item enables command mode; **left-click = select** (plain / Shift-toggle / Ctrl-all-of-type-in-radius / Ctrl+Shift-toggle-all-of-type), **right-click = order** (enemy = attack, block = move, air = move toward look). All input captured client-side (Ctrl is not server-known) and sent as one `CommandInputPacket`; selection is a per-player attachment; orders are a `CommandOrder` attachment read by commanded goals on each unit. Probes are commandable too: they honor MOVE and a **MINE** order (right-click a harvestable block) that reuses their existing harvest logic |
| **A9** | **Zerg director with literal Drones** (chosen: A9-B) — per-Hive brain (`HiveBlockEntity.serverTick`): maintains Drone count, Drones mine `#asteriskcraft:harvestable` blocks into the shared Zerg `ArmyBank` (all three Hives are linked chests onto the same pool, exposed as an item capability so the existing Probe/Drone delivery reuses verbatim), a global `ZergDirector` (`ServerTickEvent.Post`, state in level attachments) spends the pooled resources on escalating mixed **Zergling + Hydralisk** waves and issues attack-move at the Nexus; killing Drones starves it, killing all three Hives removes production. **Building destruction:** every building is a `building/SiegeTarget` holding its HP (and, for a Protoss one, shields) in a shared `building/BuildingDefense`; all combat units carry a faction-generic `SiegeBlockGoal` that batters down whichever enemy building it reaches (and digs through blocks that stall their path). `FactionCore` is the narrower marker for the buildings whose fall decides the match — Nexus + Hives, but not a Gateway — and the removal fires the win/lose via the core BE's `preRemoveSideEffects` (which deliberately skips the vanilla Container-drop, since that would dump the whole army's shared bank just because one building died). So both outcomes are reached through real combat. **Spoils:** a razed core instead spills `1 / (that faction's still-standing cores + 2)` of the shared `ArmyBank` onto the ground where it stood (`building/CoreSpoils`) — a quarter, then a third, then a half across the three Hives, i.e. an equal quarter of the original pool each time, with the last quarter left to the survivors. The count comes from `building/CoreCensus`, a level attachment every core enrols itself in from its own ticker, so an expansion Nexus really does cushion the loss of the first one. |

## Architecture sketch

Package root `net.bitflora.asteriskcraft`:

- `faction/` — Faction, FactionSavedData, FactionAttachment, FactionRelations
- `building/` — BuildingKitItem, BuildingTemplates, NexusBlock(Entity), GatewayBlock(Entity), HiveBlock(Entity), `PhotonCannonTargeting` (pure targeting rule), structure templates in `data/asteriskcraft/structure/`
- `entity/` — ProbeEntity, DroneEntity, PhotonCannonEntity; `ai/` goals: HarvestBlockGoal, DeliverToContainerGoal, FactionTargetGoal, CommandedMoveGoal, CannonFireGoal
- `command/` — CursorItem (held marker), CommandInputPacket + client input handler, CommandInputResolver (server), PlayerSelection attachment, CommandOrder attachment + `ai/CommandedMoveGoal`/`ai/CommandedAttackGoal`
- `director/` — ZergDirector (server tick handler, wave scheduler)
- `game/` — GameState saved data (initialized/won/lost) + world bootstrap. Implemented as first-player-join placement rather than server start (see Status note below) — no slash commands anywhere in the MVP.

Key technical notes:
- Units marching across the map need **chunk tickets** attached while they hold an active order — otherwise they freeze in unloaded chunks. **V3 sidesteps this** by placing the Zerg base ~110 blocks away (inside typical simulation distance) so wave units and Drones path through already-loaded chunks; chunk tickets (for R5.10 and a more distant base) remain an open spike — see [R5-command-plan.md](R5-command-plan.md).
- All game logic server-side; wand selection UI feedback via glowing outline effect (vanilla `Glowing` works for MVP).
- "Non-destructive mining": harvested blocks switch to a depleted blockstate (visual dim) with a regen cooldown, never removed from the world.

## Costs (wood/cobble/iron economy; amounts get a balance pass in V4)

The whole economy runs on vanilla resources Probes/Drones can harvest non-destructively: **wood** (logs, `#minecraft:logs`), **cobblestone** (from stone), and every ore in the ground — **iron, coal, copper, gold, redstone, lapis, diamond, emerald** (via the matching `#minecraft:*_ores` tags), each yielding its usual smelted/refined form (e.g. iron ore → iron ingot, coal ore → coal) directly, with no separate smelting step. Zerg costs no longer mirror Protoss exactly: Protoss stays picky about which resource pays for what, while every Zerg unit accepts **any item** in the shared Hive bank toward its flat cost (whatever mix of resources the Drones happen to have delivered).

| Protoss | Cost | Zerg equivalent | Cost |
|---------|------|------------------|------|
| Probe | 50 wood **or** 50 cobblestone | Drone | 50 of any resource |
| Zealot (Zombie, melee) | 50 wood **and** 50 cobblestone | Zergling | 25 of any resource |
| Dragoon (Skeleton, ranged) | 100 wood **and** 50 cobblestone | Hydralisk | 100 of any resource |
| Scout (air) | 150 cobblestone **and** 20 iron | Mutalisk (air) | 100 of any resource |
| Gateway kit | 150 wood **or** 150 cobblestone | Spawning Pool (baked into Hive for MVP) | — |
| Photon Cannon kit | 150 wood **or** 150 cobblestone | Sunken Colony | Not buildable — one is pre-placed per Hive |
| Nexus kit (expansion) | 400 cobblestone only (no wood alternative) | Hive | Not buildable — pre-placed only |

## Slices (each ends demo-able)

**V1 — Mod skeleton + Nexus + Probe economy. `[DONE]`** MDK setup for NeoForge 26.1; faction core (A1); world bootstrap places the Nexus, seeded with starting resources, on first player join (no slash commands — moved off server-start after testing showed the heightmap isn't settled that early); Nexus block entity + GUI queue; Probe entity that non-destructively harvests wood/stone/iron ore (preferring to keep mining the same resource type) and delivers a flat 3 per trip straight into the Nexus; Probe costs 50 wood or 50 cobble from the Nexus's own inventory. Unit tests cover faction rules, the building site-prep geometry, and economy constants. *Demo: create a new world, find the Nexus standing near you, queue a Probe, watch it mine and deposit into the Nexus.*

**V2a — Gateway + Zealots/Dragoons. `[DONE]`** Warp-in kit framework (A2); Gateway production (A6) of Zealots (zombies, 50 wood + 50 cobble) and Dragoons (skeletons, 100 wood + 50 cobble); rally points. *Demo: craft Gateway kit, warp it in, produce a mixed squad.*

**V2b — Command Crystal: select + orders (R5).** The A8 command scheme — held Command Crystal enables command mode; left-click select (plain / Shift-toggle / Ctrl-type-in-radius / Ctrl+Shift-toggle-type), right-click order (enemy=attack, block=move, air=move-toward-look). Client input capture → `CommandInputPacket` → per-player selection attachment + `CommandOrder` attachment read by commanded goals on units; selection glow; chunk-load ordered units. Detailed plan: [R5-command-plan.md](R5-command-plan.md). *Demo: hold the Crystal, click-select a mixed squad, right-click a Hive to send them attacking; Ctrl-click one Zealot to grab the whole group.*

**V3 — Zerg AI + win/lose. `[DONE]`** 3 Hives placed at first join ~110 blocks east of the Nexus (inside simulation distance — no chunk tickets needed, see the technical note below), each seeded with resources + a surface resource garden + starter Drones. Per-Hive Drone mining loop (`HiveBlockEntity`); `ZergDirector` (`ServerTickEvent.Post`) runs an escalating **mixed Zergling + Hydralisk** wave schedule, spending pooled Hive resources and attack-moving the wave at the Nexus with an "under attack!" ping. Win/lose is reached through combat: all combat units (Zealot/Dragoon/Zergling/Hydralisk) carry a `SiegeBlockGoal` that batters down enemy `FactionCore` blocks (Nexus + Hives, which have siege HP) and digs through obstructing blocks; a core's removal fires the outcome via `preRemoveSideEffects` — victory on the last Hive razed, defeat on the Nexus razed. Unit tests cover the Zerg cost mirror, wave-escalation curves, and the win/lose decision. *Demo: full playable game loop, both outcomes reachable.*

**V4 — Photon Cannon + real costs.** Cannon kit + auto-targeting (A7); wire all production/kit costs to actual item consumption (R7); first balance pass on wave scaling. *Demo: cannons repel a wave; production halts when the chest is empty.*

**V4b — Sunken Colony (R9). `[DONE]`** The Zerg answer to the Photon Cannon: a rooted `SunkenColonyEntity` (150 HP, no movement goals, `FactionTargetGoal` + `RetaliateGoal`, deliberately not commandable) planted beside every Hive by `GameBootstrap`. Its `SunkenSpikeGoal` whips the tentacle every 32 ticks and drives a single `SunkenSpikeEntity` — an `EvokerFangs` subclass — out of the ground under a target up to 11 blocks away, for 20 damage. Vanilla fangs hardcode 6 damage and only spare vanilla-team allies, so both the damage and the faction filter are applied in `combat/SunkenSpikeDamageHandler` (see docs/neoforge-api-notes.md); the trade for hitting that hard is that the strike lands ~8 ticks later at a fixed spot, so a moving target can walk out of it. Ally/enemy spawn eggs included for testing. *Demo: walk into a Hive and get impaled; the spikes leave the Hive's own Drones untouched.*

**V4c — Mutalisk, the first air unit (R10). `[DONE]`** A `MutaliskEntity` (60 HP, 4.5 damage, 9-block range, 0 armour, 100 of any resource) that cruises 6 blocks above the terrain. Flight is two pieces neither of which any combat goal knows about: a vanilla `FlyingMoveControl` plus an `entity/ai/HoverFlyingNavigation` that lifts every destination to `ground + 6` **before** pathing — the altitude has to be baked into the path itself, because `PathNavigation.followThePath` only advances a node when the mob is within 1 block of it on Y, so a flyer over a ground-hugging path would thrash forever (see docs/neoforge-api-notes.md). Because every movement goal in the mod reaches the world through `getNavigation().moveTo(...)`, the existing `RangedAttackGoal`/`CommandedMoveGoal`/`GuardGoal`/`SiegeBlockGoal` all fly at altitude unchanged; `SiegeBlockGoal` only gained a per-unit reach so an air unit can batter a core from where it hovers, and an `entity/ai/HoverGoal` holds altitude in the gaps between orders. Melee ground units (Zealot, Zergling) genuinely cannot answer it — only ranged units and the Photon Cannon can, which is the intended counter-play. The enemy build script fields one from the fifth wave on. *Demo: a Mutalisk swings over the tree line, your Zealots mill uselessly beneath it, a Dragoon brings it down.*

**V4d — Scout, the Protoss air unit (R10). `[DONE]`** The answer to the Mutalisk: a `ScoutEntity` (75 HP behind 50 shields, 11 damage, 9-block range, 30-tick cadence, 0.5 armour) trained at the Gateway for 150 cobblestone **and** 20 iron — the first cost in the mod paid in refined metal, which works with no new economy plumbing because Probes already deliver iron ore as ingots into the shared `ArmyBank`. It reuses V4c's flight stack **verbatim** — `FlyingMoveControl` + `HoverFlyingNavigation` at the same `HOVER_HEIGHT` of 6, `HoverGoal` holding altitude between orders, `SiegeBlockGoal` given its attack radius as reach — so not one line of the goal library changed to make a second flyer. It deliberately shares the Mutalisk's whole envelope (altitude, reach, cadence) so the two meet on even terms and the duel is decided by staying power and punch, both of which the Scout has; a Photon Cannon still out-ranges it at 12, keeping static defence the counter to air on both sides. Shields come from the `Shielded` marker alone. Ambient barks are the four ported "what" lines, Dragoon-style (no hurt/death clips exist). Ally/enemy spawn eggs included. *Demo: a Mutalisk comes over the tree line, you queue a Scout, and it goes up and takes the sky back.*

**V5 — Polish + extensibility groundwork.** Control groups (number-key bindings) on the Command Crystal; per-player client-only selection glow (replacing V2b's shared server glow); unit team-color visuals, sounds, particles; document the faction/race registry for future Terran/player-Zerg + PvP; optional start on tactical map GUI. *Demo: comfortable command UX in a full match.*

## Verification

- `./gradlew test` for deterministic logic (faction rules, site-prep geometry, economy constants) — tags and item components aren't bound in the JUnit bootstrap, and structure templates need a running server's resource manager, so tag-dependent behavior (e.g. block→item yield mapping) and template placement aren't unit-testable and need the checks below instead. `BuildingTemplatesTest` covers what it can by reading the `.nbt` files as raw NBT.
- `./gradlew runClient` per slice; execute each slice's demo script manually.
- `./gradlew runServer` at V3+ — all logic must be server-side safe (dedicated-server crash is the classic modding failure).
- V3 end-to-end: play one full match to victory (raze 3 Hives) and one to defeat (let Zerg kill the Nexus).
- Chunk test: order units to attack-move from outside simulation distance of the Hives and confirm they arrive.
