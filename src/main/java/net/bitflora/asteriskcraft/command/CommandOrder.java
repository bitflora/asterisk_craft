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
 * read it decide which kinds they honor (combat units: MOVE/ATTACK; Probe: MOVE/MINE).
 * {@link Kind#NONE} is the "no active order" default so the attachment never holds null.
 */
public record CommandOrder(Kind kind, Optional<BlockPos> pos, Optional<UUID> target) {
    public enum Kind implements StringRepresentable {
        NONE("none"), MOVE("move"), ATTACK("attack"), MINE("mine");

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);
        private final String name;

        Kind(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
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

    public static CommandOrder attack(UUID target) {
        return new CommandOrder(Kind.ATTACK, Optional.empty(), Optional.of(target));
    }

    public boolean isNone() {
        return this.kind == Kind.NONE;
    }
}
