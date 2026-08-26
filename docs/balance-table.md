# The balance table

Every unit's combat stats and production cost are one CSV:
[`src/main/resources/asteriskcraft/balance/unit_stats.csv`](../src/main/resources/asteriskcraft/balance/unit_stats.csv).
One row per unit, one column per stat. This document is what each column means and what it is
measured in.

`stats/UnitStats` is still the named constant every call site reaches for (`UnitStats.GHOST`) and
still carries the *rationale* for why a number is what it is; the CSV carries the number.

## The tweak loop

Open the CSV in a spreadsheet, sort by whatever you are comparing, save, relaunch. Nothing
recompiles.

The file is read once, at `UnitStats` class-init, by `stats/UnitStatTable` — **not** on a datapack
reload, and `/reload` will not pick up an edit. That is a constraint rather than a preference:
attributes are consumed once at `EntityAttributeCreationEvent` during mod construction, command
cards bake their cost text at class-init, `race/UnitRoster` memoises cost and build time on first
resolve, and a dozen entity and goal classes hold a `static final UnitStat`. A table that changed
underneath all of that would only *appear* to have changed.

An installed build can be tuned without being rebuilt: a copy at
`<gamedir>/config/asteriskcraft/unit_stats.csv` overrides the shipped one. It replaces the table **as
a whole** — it must define every unit id the shipped copy does, or it is logged and ignored, because
half a table silently answering with jar values is the worse failure during a balance pass.

A malformed file fails at startup naming the exact cell:

```
unit_stats.csv:16: detect_radius/detect_sweep/detect_reveal: detection needs every one of its columns or none of them; missing [detect_sweep]
```

## Units at a glance

| Written in | Columns |
| --- | --- |
| **Ticks** (20 ticks = 1 second) | `attack_anim_ticks`, `cooldown`, `detect_sweep`, `detect_reveal`, `blast_fuse`, `build_ticks` |
| **Blocks** | `step_height`, `follow_range`, `range`, `hover_height`, `path_length`, `detect_radius`, `bounce_radius`, `blast_radius` |
| **Health points** (2 per heart) | `health`, `shield`, `attack_damage`, `anti_air_bonus` |
| **Item counts** | the amounts inside `cost` |
| **Vanilla attribute values** (unitless) | `armor`, `speed`, `knockback_resistance` |
| **Plain counts / multipliers** | `bounce_hits`, `bounce_falloff` |

There are no seconds anywhere in the file. `build_ticks` of `1000` is 50 seconds; a `cooldown` of
`30` is 1.5 seconds.

## Identity

### `race`
`protoss`, `zerg` or `terran`. Groups the row into `UnitStats.PROTOSS_ROSTER` / `ZERG_ROSTER` /
`TERRAN_ROSTER`, which is all it does — it is deliberately **not** part of a `UnitStat`, since side
and race are separate ideas and a stat carries neither (see CLAUDE.md). Also decides which cost
convention the row is held to: Protoss and Terran costs must name a real resource, Zerg costs must
be `any`.

### `id`
The unit's stable lower-case key. It is the name a build script spells, the id a command card names,
and the row `UnitStats.GHOST` looks up. Renaming one means editing the build scripts under
`data/asteriskcraft/build_scripts/` and the `UnitStats` field that fetches it.

## Base stats

Every one of these is required on every row — there are no blanks in this block. Each becomes a
vanilla attribute via `stats/UnitAttributes.apply`, and each is set **explicitly** rather than left
to a vanilla default, because the defaults are wrong for RTS units (`MOVEMENT_SPEED` defaults to
0.7, and `Mob.createMobAttributes()` pins `FOLLOW_RANGE` at 16).

### `health` — health points
`Attributes.MAX_HEALTH`. Two points per heart, the same scale as a player's 20. Vanilla clamps to
1–1024. Range in this table: 10 (a worker) to 200 (a Spore Colony).

### `armor` — armour points
`Attributes.ARMOR`. **Not flat damage reduction**, despite how small the numbers look. Vanilla's
`CombatRules.getDamageAfterAbsorb` reduces damage by `armour / 25`, so one point is worth *at most*
4% — and a big hit erodes it further through the `armour − damage/2` term, which floors at 20% of
the stated armour. That is why armour is worth most against many small hits and nearly nothing
against a single large one. Vanilla's attribute range is 0–30, but the formula caps the effective
value at 20. Range in this table: 0 to 1.

### `speed` — vanilla movement speed
`Attributes.MOVEMENT_SPEED`. Unitless; there is no clean blocks-per-second conversion, so use
anchors instead. A vanilla zombie is `0.23` and a player `0.1` (players move under different
mechanics, so they are not comparable). In this table: `0.15` is the Overlord, the slowest thing
that moves at all; `0.25` is the standard combat-unit walk; `0.35` is a worker, the fastest.

**`0.0` means rooted, and must be paired with `knockback_resistance` of `1.0`.** There is no
separate "is a structure" flag — a static defence *is* a row with those two values, and
`UnitStatsTest.onlyStructuresAreRooted` fails the build if one appears without the other, or if
anything that should walk has either.

### `knockback_resistance` — fraction, 0.0 to 1.0
`Attributes.KNOCKBACK_RESISTANCE`. `0.0` takes full knockback, `1.0` cannot be shoved at all. Only
the four rooted structures use anything but `0.0`.

### `step_height` — blocks
`Attributes.STEP_HEIGHT`. How high a rise the unit walks up without jumping. `0.6` is vanilla's
default and right for a normal-sized unit; `1.1` lets a two-node-wide unit (Dragoon, Ultralisk) step
over a full block instead of stalling against every rise, because `MoveControl` only fires a jump
within `sqrt(width)` of the waypoint and a wide unit stops short of that.

**Keep it under 2.0.** `WalkNodeEvaluator.getNeighbors` derives its climb allowance as
`floor(max(1, stepHeight))`, so 2.0 would let the pathfinder plan routes up sheer two-block walls.
The builder rejects anything at or above 2.0. Everything in `[1.0, 2.0)` changes how a unit
*executes* a path without changing which paths exist.

### `follow_range` — blocks
`Attributes.FOLLOW_RANGE`: how far the unit will acquire a target from. `32` is the standard;
workers use `48` so they notice things across a mining camp; Terran combat units use `48`.

**A rooted attacker's follow range must equal its `range`** — it cannot close a gap, so acquiring
something it can never shoot leaves it holding a useless target. `UnitStatsTest` enforces that for
the three armed static defences.

### `shield` — health points, absorbed before HP
The Protoss shield pool, on the same scale as `health`. Damage lands on shields first and spills
over into HP only when they are gone; shields recharge after 7 seconds without being hit, at 1 per
second (`combat/ShieldEventHandler.REGEN_DELAY_TICKS` / `REGEN_PER_TICK` — those two constants are
not in this table).

**The pool is gated by race at runtime**, not by this column: `combat/ShieldAttachments.maxShieldFor`
returns 0 for any unit whose army is not a shielded race. A non-Protoss row may state a pool and
simply never get it — the Drone does, as a leftover of the pass that transcribed it from the Probe.

## Attack

### `attack_damage` — health points per hit
`Attributes.ATTACK_DAMAGE`, before the target's armour and shields. Applied per swing for a melee
unit, per shot for a ranged one, and once for the whole detonation of a suicide unit.

**Blank means "no attack at all", which is not the same as `0`.** A blank leaves the attribute
untuned by this mod; the two unarmed workers are `PathfinderMob`s that never had it, and a blank on
a `Monster` would leave it at vanilla's 2.0 default rather than removing it. Exactly four rows are
blank — the Probe, the Drone, the Overlord and the Bunker — and
`UnitStatsTest.onlyNonCombatUnitsLackAttackDamage` pins that list.

### `anti_air_bonus` — extra health points against an air target
Added flat on top of `attack_damage` when the target is an `entity/Flyer`. Only the Scout has one:
11 to anything on the ground, 21 to a Mutalisk. `0.0` means the unit hits air and ground alike.

It is a bonus rather than a second damage number so that one attribute still describes the attack —
nothing about targeting or acquisition changes with it. The builder rejects a non-zero bonus on a row
with no `attack_damage` to add itself to.

Note this keys off the unit-type marker `entity/Flyer.isAir`, **not** off `entity/Altitude` — a
grounded Mutalisk still takes the bonus. The two notions of "air" are not interchangeable; see
CLAUDE.md.

### `attack_anim_ticks` — ticks
Length of the client-side strike animation. `0` means the unit has none. Purely visual — it drives
the renderer's swing interpolation and nothing about damage.

**It must fit inside the unit's `cooldown`** for a ranged unit, or the animation is still playing
when the next shot fires; `UnitStatsTest.strikeAnimationsFitInsideTheirCooldown` enforces it.

## The optional groups

The remaining columns come in five groups, each mapping to one of `UnitStat`'s `Optional`
sub-records. **Each group is all-or-nothing**: fill every column of a group to give the unit that
capability, or leave every one blank. A half-filled group is rejected at startup, because the missing
half would otherwise be read as a default nobody designed.

### `range`, `cooldown` — a ranged attack

- **`range`** — blocks. How far the unit holds at and fires from. Leaving this group blank makes the
  unit **melee**, driven by vanilla's `MeleeAttackGoal` at its fixed 20-tick cadence — which is why
  a melee row also has no `cooldown` to state.
- **`cooldown`** — ticks between shots, fixed. `20` is one shot a second, `40` is one every two.

DPS is `attack_damage * 20 / cooldown`. A Marine's 3 damage on `20` is 3 DPS; a Sunken Colony's 20 on
`32` is 12.5.

A two-block `range` is not a misnomer for the Firebat: this group is "how far it holds at, and how
often it fires", which is exactly true of a flamethrower, and stating it is what lets the unit reuse
the ranged attack goal and pick up the Bunker firing-slit bonus.

### `fly_speed`, `hover_height`, `path_length` — flight

- **`fly_speed`** — `Attributes.FLYING_SPEED`, unitless, vanilla default 0.4. Separate from `speed`,
  which a flyer still needs stated.
- **`hover_height`** — blocks of clearance above the terrain the unit cruises at. Every flyer must
  use the same value, `UnitStats.HOVER_HEIGHT` (4), so a player learns one altitude rather than one
  per unit; `UnitStatsTest.everyFlyerSharesTheOneHoverHeight` enforces it. The altitude is baked into
  the path by `entity/ai/HoverFlyingNavigation` before it is computed, which is why no movement goal
  knows a flyer from a walker.
- **`path_length`** — blocks. The navigation's `requiredPathLength`. **It must be at least the unit's
  `follow_range`**, or the flyer gives up on distant targets mid-approach; `UnitStatsTest` enforces
  it. All three flyers use 64 against a follow range of 32.

An *armed* flyer must also out-range its own cruising altitude with room to spare — a unit hovering
4 up with a range of 4 would have to be directly overhead to shoot. `UnitStatsTest` requires at least
6 blocks of horizontal stand-off after the altitude is subtracted, and skips the unarmed Overlord.

### `detect_radius`, `detect_sweep`, `detect_reveal` — detection

Makes the unit a detector: it periodically lights up cloaked enemies around it, for its own side.

- **`detect_radius`** — blocks from the detector, measured centre to centre.
- **`detect_sweep`** — ticks between sweeps. `20` is once a second.
- **`detect_reveal`** — ticks a single sweep keeps a unit revealed.

**`detect_reveal` must exceed `detect_sweep`**, or consecutive sweeps leave a gap and a detected unit
strobes in and out of view. The builder rejects it. The excess is also the tactical tail: a unit that
has just slipped out of range stays lit for the difference. All three detectors share one envelope
(`16 / 20 / 60` — 16 blocks, once a second, three seconds of reveal), deliberately, so a player
learns one radius rather than one per building.

### `bounce_hits`, `bounce_falloff`, `bounce_radius` — a chaining attack

- **`bounce_hits`** — total enemies one shot may hit, *including* the primary target. `3` is one
  primary plus two hops.
- **`bounce_falloff`** — multiplier per hop, **compounding**: hit *n* takes
  `attack_damage * falloff^n`. The Mutalisk's `0.5` means 4.5 → 2.25 → 1.125. Must be greater than 0
  and at most 1.
- **`bounce_radius`** — blocks, searched **from the enemy just hit**, not from the attacker. That is
  what lets a chain walk down a line of enemies past the shooter's own `range`.

A bounce needs a `range` group to chain from and an `attack_damage` to fall off; `UnitStatsTest`
enforces both.

### `blast_radius`, `blast_fuse` — a suicide detonation

- **`blast_radius`** — blocks. Damage is **flat inside the radius and zero outside it** — there is no
  distance falloff, deliberately, so the whole point of reaching a target is that everything inside
  the radius dies. The value also goes to `Level.explode` for vanilla's own terrain and knockback
  effects.
- **`blast_fuse`** — ticks from the unit arming itself to detonation, and the only window a victim
  has to walk out of the blast. Must be positive: a zero fuse would go off on the arming tick.

The damage is the unit's ordinary `attack_damage` — a bomber's blast *is* its attack, so it is not a
second damage number. The builder requires one. Note the Infested Villager's 250 is not a per-swing
figure but the one detonation it ever gets.

A blast is also the mod's **one** damage path that does not resolve hostility: it catches friendly
units and the player too. That is a statement that a blast is not targeting, not a hole in the
targeting choke point.

## Production

### `build_ticks` — ticks to produce
Counted down by whichever producer is building the unit — a base's queue, the Gateway's queue, or the
AI director's training cadence — so the number means one thing whoever is building it. 20 ticks per
second: `200` is 10 seconds, `480` is 24, `1200` is a minute.

**It must be positive for a unit with a cost and `0` for one with none**, and the builder rejects any
other pairing: a purchasable unit with no build time would pop out on the tick it was ordered, and a
pre-placed unit carrying one would state a time nothing ever counts down.

### `cost` — one cell, item counts

The whole `stats/UnitCost` in a single cell. Neither separator is a comma, so the file never needs
CSV quoting.

| Written | Means |
| --- | --- |
| `none` or empty | Never trained. Pair with `build_ticks` of `0`. |
| `any 100` | 100 of any item at all. |
| `wood 100 + stone 50` | 100 wood **and** 50 cobblestone, paid together, all or nothing. |
| `wood 50 \| stone 50` | 50 wood **or** 50 cobblestone — two alternatives, either will do. |

`+` binds tighter than `|`, so `a 1 + b 2 | c 3` is two alternatives, the first of which is a bundle.

Amounts are **item counts** in the army's shared bank, extracted atomically by
`building/ResourceBank.extractAll`. Resource names are `wood` (anything in `#minecraft:logs`),
`stone` (cobblestone), `iron` (iron ingots) and `any` (any item). `stone` may also be written
`cobblestone` and `any` as `resources` — those are the same resources under their player-facing
labels.

**Alternative order is load-bearing.** It is the order of the buttons on a base's command card: the
Probe's `wood 50 | stone 50` is what makes option 0 the Wood button and option 1 the Stone button.
`UnitStatsTest.probeCostIsWoodThenStoneInThatOrder` pins it.

**Protoss and Terran costs must name a real resource; Zerg costs must be `any`.** That is the
economic difference between the races, and `UnitStatsTest.protossAndTerranStayPickyAndZergPaysInAnything`
fails the build if a row breaks it.

## What is *not* in this table

Numbers a balance pass often goes looking for and will not find here:

| Number | Where it lives |
| --- | --- |
| Building kit costs (base, Pylon, Photon Cannon, Bunker) | `building/BaseBlockEntity.BASE_KIT_COST` / `PYLON_COST` / `BUILDING_COST` / `BUNKER_COST` — a kit is not a unit and carries no `UnitCost` |
| A base's siege HP, shield buffer, warp-in duration, bank size | `race/RaceProfile`, per race |
| Shield regen delay and rate | `combat/ShieldEventHandler.REGEN_DELAY_TICKS` (140) / `REGEN_PER_TICK` (0.05) — shared with building shields so there is one feel, not two |
| Photon Cannon warp-in, Bunker construction | `PhotonCannonEntity.WARP_TICKS` (200) / `BunkerEntity.BUILD_TICKS` (600) — these are structures standing up, not units being trained |
| Lurker burrow/surface transition | `entity/zerg/BurrowClock.TRANSITION_TICKS` (60) |
| Ghost cloak duration and cooldown | `entity/terran/CloakClock.CLOAK_TICKS` / `COOLDOWN_TICKS` |
| Bunker firing-slit range bonus | `BunkerEntity.rangeBonus()` — a property of the vehicle, added to a passenger's `range` at runtime |
| Wave composition and timing | the per-race build scripts in `data/asteriskcraft/build_scripts/` |
| Which race has shields, HP regen, infestation, and what it hunts in the wild | `faction/Race` — cheap traits, per race rather than per unit |

## Adding a unit

Add the row, and the table half is done — the roster it belongs to is derived from the `race` column,
so there is no second list to remember. Then add a `public static final UnitStat` to
`stats/UnitStats` fetching it by id (with the rationale for its numbers in the javadoc), and update
the count in `UnitStatsTest.rosterIsCompleteAndUnique`. Everything outside `stats/` that a new unit
touches — the entity class, renderer, roster entry, command card — is
[docs/terran-humanoids.md](terran-humanoids.md)'s checklist, not this one.

## The rules the file must obey

Collected from `UnitStat.Builder.build()` (which fails at startup, naming the row) and
`stats/UnitStatsTest` (which fails the build):

- `health`, `speed` and `cost` are always stated. Every base-stat column is filled on every row.
- `speed` of `0.0` and `knockback_resistance` of `1.0` go together, and only on the four rooted
  structures.
- `step_height` stays under 2.0.
- `build_ticks` is positive exactly when `cost` is not `none`.
- A rooted attacker's `follow_range` equals its `range`.
- `attack_anim_ticks` is less than `cooldown` whenever both are present.
- `anti_air_bonus` and the blast group need an `attack_damage`; the bounce group needs a `range` too.
- `detect_reveal` > `detect_sweep`, and all three detection values are positive.
- Every flyer's `hover_height` equals `UnitStats.HOVER_HEIGHT`; its `path_length` is at least its
  `follow_range`; and an armed one keeps 6 blocks of horizontal stand-off after its altitude.
- Ids are unique and lower-case; every column group is all-or-nothing.
- Protoss and Terran costs never use `any`; Zerg costs always do.
