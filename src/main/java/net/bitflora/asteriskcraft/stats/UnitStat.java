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
        double followRange,
        /** Protoss shield pool; 0 = none. The Protoss-only gate stays in {@code combat.ShieldAttachments}. */
        int shield,
        /** Absent = this unit registers no ATTACK_DAMAGE attribute of its own (the two workers). */
        OptionalDouble attackDamage,
        /** Absent = melee or non-attacking. */
        Optional<Ranged> ranged,
        /** Absent = ground unit. */
        Optional<Flight> flight,
        /** Absent = a shot hits exactly one target. */
        Optional<Bounce> bounce,
        /** Length of the client strike animation, in ticks; 0 = none. */
        int attackAnimTicks,
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
     * A chaining attack: how many enemies one shot may hit in total, the damage multiplier applied
     * on each additional hop (compounding — hit n takes {@code base * damageFalloff^n}), and how far
     * from the enemy just hit the next one may be found.
     */
    public record Bounce(int maxHits, float damageFalloff, float searchRadius) {
    }

    public Ranged rangedOrThrow() {
        return this.ranged.orElseThrow(() -> new IllegalStateException(this.id + " has no ranged attack"));
    }

    public Flight flightOrThrow() {
        return this.flight.orElseThrow(() -> new IllegalStateException(this.id + " does not fly"));
    }

    public Bounce bounceOrThrow() {
        return this.bounce.orElseThrow(() -> new IllegalStateException(this.id + " does not bounce"));
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
        private double followRange = 32.0;     // Mob's base pins FOLLOW_RANGE at 16, so this is always load-bearing
        private int shield;
        private OptionalDouble attackDamage = OptionalDouble.empty();
        private Optional<Ranged> ranged = Optional.empty();
        private Optional<Flight> flight = Optional.empty();
        private Optional<Bounce> bounce = Optional.empty();
        private int attackAnimTicks;
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

        public Builder bounce(int maxHits, float damageFalloff, float searchRadius) {
            this.bounce = Optional.of(new Bounce(maxHits, damageFalloff, searchRadius));
            return this;
        }

        public Builder attackAnimTicks(int v) {
            this.attackAnimTicks = v;
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
            return new UnitStat(this.id, this.maxHealth, this.armor, this.movementSpeed,
                    this.knockbackResistance, this.followRange, this.shield, this.attackDamage,
                    this.ranged, this.flight, this.bounce, this.attackAnimTicks, this.cost);
        }
    }
}
