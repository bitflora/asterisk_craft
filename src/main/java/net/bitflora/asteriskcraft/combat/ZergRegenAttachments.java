package net.bitflora.asteriskcraft.combat;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Backing attachment for the Zerg health-regen mechanic (see {@link ZergRegenEventHandler}):
 * unlike Protoss shields, Zerg regenerate HP itself, slowly, once they've gone a few seconds
 * without taking damage.
 */
public final class ZergRegenAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, AsteriskCraft.MODID);

    /** Ticks remaining before HP regen resumes; reset whenever the unit takes damage. */
    public static final Supplier<AttachmentType<Integer>> REGEN_DELAY = ATTACHMENT_TYPES.register(
            "zerg_regen_delay", () -> AttachmentType.builder(() -> 0).build());

    private ZergRegenAttachments() {
    }
}
