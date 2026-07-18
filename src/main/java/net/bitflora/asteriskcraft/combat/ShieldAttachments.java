package net.bitflora.asteriskcraft.combat;

import com.mojang.serialization.Codec;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Protoss shields: a regenerating damage buffer that sits in front of HP. Only Protoss-faction
 * units carry a nonzero max shield ({@link #maxShieldFor}); HP itself never regenerates. Damage
 * absorption happens in {@link ShieldEventHandler}.
 */
public final class ShieldAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, AsteriskCraft.MODID);

    public static final float ZEALOT_MAX_SHIELD = 20.0f;
    public static final float DRAGOON_MAX_SHIELD = 10.0f;
    public static final float PROBE_MAX_SHIELD = 10.0f;

    /**
     * Current shield value. Defaults to this entity's max shield (so Protoss units spawn at full
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

    /** Zero for anything that isn't a Protoss unit we've defined a shield pool for. */
    public static float maxShieldFor(IAttachmentHolder holder) {
        if (!(holder instanceof Entity entity) || FactionAttachments.get(entity) != Faction.PROTOSS) {
            return 0.0f;
        }
        EntityType<?> type = entity.getType();
        if (type == AsteriskCraft.ZEALOT.get()) {
            return ZEALOT_MAX_SHIELD;
        }
        if (type == AsteriskCraft.DRAGOON.get()) {
            return DRAGOON_MAX_SHIELD;
        }
        if (type == AsteriskCraft.PROBE.get()) {
            return PROBE_MAX_SHIELD;
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
