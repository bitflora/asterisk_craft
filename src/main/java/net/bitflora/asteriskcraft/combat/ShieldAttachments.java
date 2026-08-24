package net.bitflora.asteriskcraft.combat;

import com.mojang.serialization.Codec;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.bitflora.asteriskcraft.faction.Race;
import net.bitflora.asteriskcraft.entity.Shielded;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Shields: a regenerating damage buffer that sits in front of HP. Only units of a race whose
 * {@link Race#shields()} is set carry a nonzero max shield
 * ({@link #maxShieldFor}); HP itself never regenerates for them. Damage absorption happens in
 * {@link ShieldEventHandler}.
 */
public final class ShieldAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, AsteriskCraft.MODID);

    /**
     * Current shield value. Defaults to this entity's max shield (so shielded units spawn at full
     * shields) rather than a flat zero, since the default is only ever consulted before the first
     * explicit set (i.e. before the unit has taken damage or regenerated).
     */
    public static final Supplier<AttachmentType<Float>> CURRENT_SHIELD = ATTACHMENT_TYPES.register(
            "shield", () -> AttachmentType.builder(ShieldAttachments::maxShieldFor)
                    .serialize(Codec.FLOAT.fieldOf("shield"))
                    .sync(ByteBufCodecs.FLOAT)
                    .build());

    /** Ticks remaining before shield regen resumes; reset whenever the unit takes damage. */
    public static final Supplier<AttachmentType<Integer>> REGEN_DELAY = ATTACHMENT_TYPES.register(
            "shield_regen_delay", () -> AttachmentType.builder(() -> 0).build());

    private ShieldAttachments() {
    }

    public static float get(Entity entity) {
        return entity.getData(CURRENT_SHIELD);
    }

    public static void set(Entity entity, float value) {
        entity.setData(CURRENT_SHIELD, Math.clamp(value, 0.0f, maxShieldFor(entity)));
    }

    /**
     * Zero for anything that isn't a unit of a shielded race with a shield pool defined for it.
     * Whether a race has shields at all is one flag on {@link Race}, read off the unit's own army
     * rather than from its side — a match where the human plays Zerg must not hand the swarm
     * Protoss shields, and in a mirror both sides answer the same way.
     *
     * <p>Called on the client too (the Jade tooltip needs a max to show), so whatever a
     * {@link Shielded#getShield()} varies with has to be synced — see the Photon Cannon's warp-in
     * state, which is entity data rather than a plain field for exactly this reason.
     */
    public static float maxShieldFor(IAttachmentHolder holder) {
        if (!(holder instanceof Entity entity)) {
            return 0.0f;
        }
        Race race = FactionAttachments.raceOf(entity);
        if (race == null || !race.shields()) {
            return 0.0f;
        }
        if (entity instanceof Shielded shielded) {
            return shielded.getShield();
        }
        return 0.0f;
    }

    /**
     * How much of {@code incomingDamage} a shield pool of size {@code shield} soaks up, and what's
     * left over to apply to HP. Pure so it can be unit-tested without a live entity; used by
     * {@link ShieldEventHandler#onIncomingDamage}.
     */
    public static DamageResult resolveDamage(float shield, float incomingDamage) {
        float absorbed = Math.min(shield, incomingDamage);
        return new DamageResult(shield - absorbed, incomingDamage - absorbed);
    }

    public record DamageResult(float remainingShield, float remainingDamage) {
    }
}
