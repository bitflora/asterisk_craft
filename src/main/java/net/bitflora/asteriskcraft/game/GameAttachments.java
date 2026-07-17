package net.bitflora.asteriskcraft.game;

import com.mojang.serialization.Codec;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;
import java.util.function.Supplier;

/**
 * Persistent match state, attached to the overworld ServerLevel so it saves with it.
 */
public final class GameAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, AsteriskCraft.MODID);

    /** True once the Nexus and the Zerg Hives have been placed in this world. */
    public static final Supplier<AttachmentType<Boolean>> BOOTSTRAPPED = ATTACHMENT_TYPES.register(
            "bootstrapped", () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL.fieldOf("value")).build());

    /** Where the player's Nexus core block stands. */
    public static final Supplier<AttachmentType<BlockPos>> NEXUS_POS = ATTACHMENT_TYPES.register(
            "nexus_pos", () -> AttachmentType.builder(() -> BlockPos.ZERO).serialize(BlockPos.CODEC.fieldOf("pos")).build());

    /** Where the surviving Zerg Hive core blocks stand; emptied as they are razed. */
    public static final Supplier<AttachmentType<List<BlockPos>>> HIVE_POSITIONS = ATTACHMENT_TYPES.register(
            "hive_positions", () -> AttachmentType.<List<BlockPos>>builder(() -> List.of())
                    .serialize(BlockPos.CODEC.listOf().fieldOf("positions")).build());

    /** Set once the match has been decided (victory or defeat) so the outcome fires exactly once. */
    public static final Supplier<AttachmentType<Boolean>> GAME_OVER = ATTACHMENT_TYPES.register(
            "game_over", () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL.fieldOf("value")).build());

    /** How many attack waves the Zerg director has launched (drives wave escalation). */
    public static final Supplier<AttachmentType<Integer>> WAVE_NUMBER = ATTACHMENT_TYPES.register(
            "wave_number", () -> AttachmentType.builder(() -> 0).serialize(Codec.INT.fieldOf("value")).build());

    /** Ticks remaining until the next attack wave; -1 before the director has initialised it. */
    public static final Supplier<AttachmentType<Integer>> WAVE_COUNTDOWN = ATTACHMENT_TYPES.register(
            "wave_countdown", () -> AttachmentType.builder(() -> -1).serialize(Codec.INT.fieldOf("value")).build());

    private GameAttachments() {
    }
}
