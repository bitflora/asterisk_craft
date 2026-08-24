package net.bitflora.asteriskcraft.command;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.StringRepresentable;

import java.util.Optional;
import java.util.UUID;

/**
 * A standing order attached to a commandable unit (see {@link CommandAttachments#ORDER}).
 * Faction-generic: any {@link net.minecraft.world.entity.Mob} can carry one; the goals that
 * read it decide which kinds they honor (combat units: MOVE/ATTACK/LOAD; Probe: MOVE/MINE).
 * {@link Kind#NONE} is the "no active order" default so the attachment never holds null.
 */
public record CommandOrder(Kind kind, Optional<BlockPos> pos, Optional<UUID> target) {
    public enum Kind implements StringRepresentable {
        NONE("none"), MOVE("move"), ATTACK("attack"), MINE("mine"), GUARD("guard"), LOAD("load");

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);
        private final String name;

        Kind(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        /**
         * Whether this is an order the unit has to <em>walk somewhere</em> to carry out, and so one
         * that should override combat for its focus window (see
         * {@link CommandAttachments#MOVE_FOCUS_TICKS}).
         *
         * <p>MOVE is the obvious one. LOAD is the other, for exactly the same reason: a squad told to
         * get into cover has to be able to disengage in order to reach it, and a Marine that stops to
         * trade shots halfway to a Bunker is not doing what it was told. GUARD is deliberately not a
         * march — it is a station to hold, and holding it means fighting.
         */
        public boolean isMarch() {
            return this == MOVE || this == LOAD;
        }
    }

    public static final CommandOrder NONE = new CommandOrder(Kind.NONE, Optional.empty(), Optional.empty());

    public static final Codec<CommandOrder> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Kind.CODEC.fieldOf("kind").forGetter(CommandOrder::kind),
            BlockPos.CODEC.optionalFieldOf("pos").forGetter(CommandOrder::pos),
            UUIDUtil.CODEC.optionalFieldOf("target").forGetter(CommandOrder::target)
    ).apply(inst, CommandOrder::new));

    public static CommandOrder move(BlockPos pos) {
        return new CommandOrder(Kind.MOVE, Optional.of(pos.immutable()), Optional.empty());
    }

    public static CommandOrder mine(BlockPos pos) {
        return new CommandOrder(Kind.MINE, Optional.of(pos.immutable()), Optional.empty());
    }

    /**
     * A persistent hold order: the unit patrols near {@code home} and defends it, engaging enemies
     * that wander in (via its faction-targeting goals) and returning afterwards. Unlike
     * {@link #move(BlockPos)}, a guard order never self-clears — it keeps the unit stationed until
     * a new order overrides it (e.g. a wave's {@code move}).
     */
    public static CommandOrder guard(BlockPos home) {
        return new CommandOrder(Kind.GUARD, Optional.of(home.immutable()), Optional.empty());
    }

    /**
     * Get inside {@code transport}: walk to it and climb in. Named by UUID rather than by position
     * for the same reason {@link #attack(UUID)} is — the thing being aimed at is an entity, and one
     * that could in principle be gone by the time the unit arrives.
     *
     * <p>Which units may actually board is not this order's business: it is decided at the door, by
     * {@code entity.terran.BunkerEntity#boardable}. An order handed to a unit that turns out not to
     * fit simply clears itself.
     */
    public static CommandOrder load(UUID transport) {
        return new CommandOrder(Kind.LOAD, Optional.empty(), Optional.of(transport));
    }

    public static CommandOrder attack(UUID target) {
        return new CommandOrder(Kind.ATTACK, Optional.empty(), Optional.of(target));
    }

    public boolean isNone() {
        return this.kind == Kind.NONE;
    }
}
