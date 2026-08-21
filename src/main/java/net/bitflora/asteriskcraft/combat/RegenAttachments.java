package net.bitflora.asteriskcraft.combat;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Backing attachment for the health-regen mechanic (see {@link RegenEventHandler}): a race with
 * {@code faction.Race.regen()} set regenerates HP itself, slowly, once it has gone a few seconds
 * without taking damage — the swarm's answer to the shield buffer a shielded race carries instead.
 */
public final class RegenAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, AsteriskCraft.MODID);

    /** Ticks remaining before HP regen resumes; reset whenever the unit takes damage. */
    public static final Supplier<AttachmentType<Integer>> REGEN_DELAY = ATTACHMENT_TYPES.register(
            "zerg_regen_delay", () -> AttachmentType.builder(() -> 0).build());

    private RegenAttachments() {
    }
}
