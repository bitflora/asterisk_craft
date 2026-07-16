package com.timja.asteriskcraft.game;

import com.mojang.serialization.Codec;
import com.timja.asteriskcraft.AsteriskCraft;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Persistent match state, attached to the overworld ServerLevel so it saves with it.
 */
public final class GameAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, AsteriskCraft.MODID);

    /** True once the Nexus (and later the Hives) have been placed in this world. */
    public static final Supplier<AttachmentType<Boolean>> BOOTSTRAPPED = ATTACHMENT_TYPES.register(
            "bootstrapped", () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL.fieldOf("value")).build());

    /** Where the player's Nexus core block stands. */
    public static final Supplier<AttachmentType<BlockPos>> NEXUS_POS = ATTACHMENT_TYPES.register(
            "nexus_pos", () -> AttachmentType.builder(() -> BlockPos.ZERO).serialize(BlockPos.CODEC.fieldOf("pos")).build());

    private GameAttachments() {
    }
}
