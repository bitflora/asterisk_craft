package net.bitflora.asteriskcraft.stats;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * One unit's complete design profile: everything a balance pass would tweak. The numbers live in
 * {@link UnitStats}; this is only their shape.
 *
 * <p>Nothing here is "absent means zero". {@code movementSpeed} in particular must always be
 * stated, because {@code Attributes.MOVEMENT_SPEED} defaults to <em>0.7</em>, not 0 (see
 * docs/neoforge-api-notes.md) — a rooted defence that forgot to say 0.0 would walk. The builder
 * enforces that: {@link Builder#build()} throws if health, speed or cost were never set, so a
 * malformed entry fails at class-init (mod construction, or the first test that touches
 * {@link UnitStats}) rather than in play.
 *
 * <p>Registry-free by design: no {@code EntityType}, no {@code Holder<Attribute>}, no {@code Item}.
 * That is what lets the whole table load in a plain unit test and keeps {@code stats} free of a
 * cycle back to {@code entity}/{@code AsteriskCraft}. For the same reason there is deliberately no
 * {@code Faction} field — hostility must go through {@code faction.FactionAttachments} alone (see
 * CLAUDE.md); race grouping for balance purposes lives in {@code UnitStats.PROTOSS_ROSTER} /
 * {@code ZERG_ROSTER} instead, not on the stat itself.
 */
public record UnitStat(
        /** Stable lower-case key: the Zerg build-script unit name, and a future datapack id. */
        String id,
        double maxHealth,
        double armor,
        double movementSpeed,
        double knockbackResistance,
        /**
         * How high a rise this unit walks up without jumping. The vanilla default of 0.6 is under a
         * full block, so an ordinary mob has to <em>jump</em> every 1-block step, and
         * {@code MoveControl.tick} only fires that jump once the mob is within
         * {@code sqrt(max(1, width))} of the waypoint — a margin a wide unit stopping half its width
         * short of the ledge can fail to reach. Raising this past 1.0 lets a big unit simply step up.
         *
         * <p>Keep it <b>under 2.0</b>: {@code WalkNodeEvaluator.getNeighbors} derives its climb
         * allowance as {@code floor(max(1, maxUpStep))}, so 2.0 would let the pathfinder plan routes
         * up sheer 2-block walls. Everything in [1.0, 2.0) changes how a unit executes a path without
         * changing which paths exist. Pinned by {@code entity.UnitFootprintTest}.
         */
        double stepHeight,
        double followRange,
        /** Protoss shield pool; 0 = none. The Protoss-only gate stays in {@code combat.ShieldAttachments}. */
        int shield,
        /** Absent = this unit registers no ATTACK_DAMAGE attribute of its own (the two workers). */
        OptionalDouble attackDamage,
        /** Absent = melee or non-attacking. */
        Optional<Ranged> ranged,
        /** Absent = ground unit. */
        Optional<Flight> flight,
        /** Absent = this unit detects nothing; cloaked enemies stay invisible to it. */
        Optional<Detection> detection,
        /** Absent = a shot hits exactly one target. */
        Optional<Bounce> bounce,
        /** Absent = this unit does not detonate; present = it is a suicide bomber. */
        Optional<Blast> blast,
        /**
         * Extra damage this unit's attack deals to an air target (an {@code entity.Flyer}), on top of
         * {@link #attackDamage}; 0 = it hits air and ground alike. It is a flat bonus rather than a
         * second damage number so that one attribute still describes the attack — the anti-air case is
         * applied at the attack site, and nothing about targeting or acquisition changes with it.
         */
        double antiAirBonus,
        /** Length of the client strike animation, in ticks; 0 = none. */
        int attackAnimTicks,
        /**
         * How long this unit takes to produce, in ticks. Read by every producer — the Gateway and
         * Nexus queues and the Zerg director's training cadence — so the number means one thing
         * whichever race is building it. 0 for a unit that is never trained (a {@link UnitCost#NONE}
         * cost); the builder enforces that pairing.
         */
        int buildTicks,
        UnitCost cost) {

    /** A ranged attacker's envelope: how far it holds at, and how often it fires. */
    public record Ranged(float range, int cooldown) {
    }

    /**
     * A flyer's envelope. {@code requiredPathLength} must exceed {@code followRange} or the flyer
     * abandons distant targets mid-approach (see the {@code HoverFlyingNavigation} note in
     * docs/neoforge-api-notes.md).
     */
    public record Flight(double flyingSpeed, int hoverHeight, float requiredPathLength) {
    }

    /**
     * A detector's envelope: how far it sees cloaked enemies, how often it sweeps for them, and how
     * long one sweep keeps a unit revealed.
     *
     * <p>{@code revealTicks} must exceed {@code sweepInterval} so consecutive sweeps overlap — a
     * reveal window shorter than the gap between sweeps would strobe. The excess is also the
     * tactical tail: a unit that has just slipped out of range stays lit for the difference.
     */
    public record Detection(double radius, int sweepInterval, int revealTicks) {
    }

    /**
     * A chaining attack: how many enemies one shot may hit in total, the damage multiplier applied
     * on each additional hop (compounding — hit n takes {@code base * damageFalloff^n}), and how far
     * from the enemy just hit the next one may be found.
     */
    public record Bounce(int maxHits, float damageFalloff, float searchRadius) {
    }

    /**
     * A suicide unit's detonation: how far the blast reaches, and how long the fuse burns once the
     * unit has armed itself. The damage it deals is the unit's ordinary {@link #attackDamage} — a
     * bomber's blast <em>is</em> its attack, so it is not a second damage number.
     *
     * <p>{@code fuseTicks} is the whole windup from arming to detonation, and is the only window in
     * which the victim can walk out of the blast; {@code radius} is fed straight to
     * {@code Level.explode}, so it is a vanilla explosion radius rather than a plain distance.
     */
    public record Blast(float radius, int fuseTicks) {
    }

    public Ranged rangedOrThrow() {
        return this.ranged.orElseThrow(() -> new IllegalStateException(this.id + " has no ranged attack"));
    }

    public Flight flightOrThrow() {
        return this.flight.orElseThrow(() -> new IllegalStateException(this.id + " does not fly"));
    }

    public Detection detectionOrThrow() {
        return this.detection.orElseThrow(() -> new IllegalStateException(this.id + " is not a detector"));
    }

    public Bounce bounceOrThrow() {
        return this.bounce.orElseThrow(() -> new IllegalStateException(this.id + " does not bounce"));
    }

    public Blast blastOrThrow() {
        return this.blast.orElseThrow(() -> new IllegalStateException(this.id + " does not detonate"));
    }

    public double attackDamageOrThrow() {
        if (this.attackDamage.isEmpty()) {
            throw new IllegalStateException(this.id + " has no attack damage");
        }
        return this.attackDamage.getAsDouble();
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final String id;
        private Double maxHealth;              // required
        private double armor;                  // 0.0 == the vanilla ARMOR default
        private Double movementSpeed;          // required — the vanilla default is 0.7, never what we want
        private double knockbackResistance;    // 0.0 == the vanilla default
        private double stepHeight = 0.6;       // the vanilla default, and right for a normal-sized unit
        private double followRange = 32.0;     // Mob's base pins FOLLOW_RANGE at 16, so this is always load-bearing
        private int shield;
        private OptionalDouble attackDamage = OptionalDouble.empty();
        private Optional<Ranged> ranged = Optional.empty();
        private Optional<Flight> flight = Optional.empty();
        private Optional<Detection> detection = Optional.empty();
        private Optional<Bounce> bounce = Optional.empty();
        private Optional<Blast> blast = Optional.empty();
        private double antiAirBonus;           // 0.0 == hits air and ground for the same damage
        private int attackAnimTicks;
        private int buildTicks;                // required iff the cost is purchasable
        private UnitCost cost;                 // required (use UnitCost.NONE for a non-purchasable unit)

        private Builder(String id) {
            this.id = id;
        }

        public Builder health(double v) {
            this.maxHealth = v;
            return this;
        }

        public Builder armor(double v) {
            this.armor = v;
            return this;
        }

        public Builder speed(double v) {
            this.movementSpeed = v;
            return this;
        }

        public Builder knockbackResistance(double v) {
            this.knockbackResistance = v;
            return this;
        }

        public Builder stepHeight(double v) {
            this.stepHeight = v;
            return this;
        }

        /** Static defence: zero movement, full knockback resistance. Names the one dangerous value. */
        public Builder rooted() {
            return this.speed(0.0).knockbackResistance(1.0);
        }

        public Builder followRange(double v) {
            this.followRange = v;
            return this;
        }

        public Builder shield(int v) {
            this.shield = v;
            return this;
        }

        public Builder attackDamage(double v) {
            this.attackDamage = OptionalDouble.of(v);
            return this;
        }

        public Builder ranged(float range, int cooldown) {
            this.ranged = Optional.of(new Ranged(range, cooldown));
            return this;
        }

        public Builder flight(double flyingSpeed, int hoverHeight, float requiredPathLength) {
            this.flight = Optional.of(new Flight(flyingSpeed, hoverHeight, requiredPathLength));
            return this;
        }

        /** Makes this unit a detector: it reveals cloaked enemies inside {@code radius}. */
        public Builder detector(double radius, int sweepInterval, int revealTicks) {
            this.detection = Optional.of(new Detection(radius, sweepInterval, revealTicks));
            return this;
        }

        public Builder bounce(int maxHits, float damageFalloff, float searchRadius) {
            this.bounce = Optional.of(new Bounce(maxHits, damageFalloff, searchRadius));
            return this;
        }

        /** Makes this unit a suicide bomber: it detonates for its {@link #attackDamage} instead of swinging. */
        public Builder blast(float radius, int fuseTicks) {
            this.blast = Optional.of(new Blast(radius, fuseTicks));
            return this;
        }

        /** Flat extra damage against air targets, on top of {@link #attackDamage}. */
        public Builder antiAirBonus(double v) {
            this.antiAirBonus = v;
            return this;
        }

        public Builder attackAnimTicks(int v) {
            this.attackAnimTicks = v;
            return this;
        }

        public Builder buildTicks(int v) {
            this.buildTicks = v;
            return this;
        }

        public Builder cost(UnitCost v) {
            this.cost = v;
            return this;
        }

        public UnitStat build() {
            if (this.maxHealth == null) {
                throw new IllegalStateException(this.id + ": health was never set");
            }
            if (this.movementSpeed == null) {
                throw new IllegalStateException(this.id
                        + ": speed was never set (Attributes.MOVEMENT_SPEED defaults to 0.7, not 0 —"
                        + " a rooted unit must call .rooted() or .speed(0.0) explicitly)");
            }
            if (this.cost == null) {
                throw new IllegalStateException(this.id + ": cost was never set (use UnitCost.NONE if not purchasable)");
            }
            if (this.cost.isPurchasable() != (this.buildTicks > 0)) {
                throw new IllegalStateException(this.id
                        + ": buildTicks must be positive for a purchasable unit and zero for one that is"
                        + " never trained — a producer would otherwise pop it out on the same tick it"
                        + " was ordered");
            }
            if (this.antiAirBonus != 0.0 && this.attackDamage.isEmpty()) {
                throw new IllegalStateException(this.id
                        + ": an anti-air bonus needs an attackDamage to add itself to");
            }
            this.blast.ifPresent(b -> {
                if (this.attackDamage.isEmpty()) {
                    throw new IllegalStateException(this.id
                            + ": a blast needs an attackDamage to detonate for");
                }
                if (b.radius() <= 0.0f || b.fuseTicks() <= 0) {
                    throw new IllegalStateException(this.id
                            + ": a blast needs a positive radius and fuse — a zero fuse would detonate"
                            + " on the tick the unit armed, with no window to escape it");
                }
            });
            this.detection.ifPresent(d -> {
                if (d.radius() <= 0.0 || d.sweepInterval() <= 0 || d.revealTicks() <= 0) {
                    throw new IllegalStateException(this.id
                            + ": a detector needs a positive radius, sweep interval and reveal duration");
                }
                if (d.revealTicks() <= d.sweepInterval()) {
                    throw new IllegalStateException(this.id
                            + ": revealTicks must exceed sweepInterval, or consecutive sweeps leave a"
                            + " gap and a detected unit strobes in and out of view");
                }
            });
            if (this.stepHeight >= 2.0) {
                throw new IllegalStateException(this.id
                        + ": stepHeight must stay under 2.0 — at 2.0 the pathfinder starts planning"
                        + " routes up sheer two-block walls (see UnitStat#stepHeight)");
            }
            return new UnitStat(this.id, this.maxHealth, this.armor, this.movementSpeed,
                    this.knockbackResistance, this.stepHeight, this.followRange, this.shield,
                    this.attackDamage, this.ranged, this.flight, this.detection, this.bounce,
                    this.blast,
                    this.antiAirBonus,
                    this.attackAnimTicks,
                    this.buildTicks, this.cost);
        }
    }
}
