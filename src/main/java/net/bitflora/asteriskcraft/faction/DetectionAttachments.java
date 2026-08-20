package net.bitflora.asteriskcraft.faction;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Which factions currently see through a {@link Cloaked} unit's cloak, and for how much longer.
 *
 * <p>Split in two on purpose, because the two halves have opposite traffic profiles:
 *
 * <ul>
 *   <li>{@link #DETECTED_BY} is a <b>bitmask over {@link Faction} ordinals</b> and is
 *       <b>synced</b> — the client needs it to decide whether to draw a cloaked unit (see
 *       {@code client.CloakRenderStateModifier}). It is written only on the edges (a faction starts
 *       or stops detecting), so a whole reveal window costs two packets rather than one per tick.
 *       A mask rather than a single {@code Faction} so that a second detecting faction can't
 *       silently overwrite the first — a three-way game must not quietly un-reveal a unit.</li>
 *   <li>{@link #REVEAL_TICKS} is the per-faction countdown behind that mask, server-side only and
 *       deliberately <b>not synced and not serialized</b>: it changes every tick, the client never
 *       needs it, and a reveal that a detector is still standing next to is re-armed within a
 *       second of world load anyway.</li>
 * </ul>
 */
public final class DetectionAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, AsteriskCraft.MODID);

    /**
     * Bitmask of {@link Faction} ordinals currently detecting this unit; 0 = undetected.
     *
     * <p>Synced but <b>not serialized</b>, on purpose: it must agree with {@link #REVEAL_TICKS},
     * which is transient. Persisting the mask alone would restore a unit from disk permanently
     * revealed, with no countdown left to ever clear it. A detector still standing over the unit
     * re-arms it within one sweep of world load anyway.
     */
    public static final Supplier<AttachmentType<Byte>> DETECTED_BY = ATTACHMENT_TYPES.register(
            "detected_by", () -> AttachmentType.builder(() -> (byte) 0)
                    .sync(ByteBufCodecs.BYTE)
                    .build());

    /**
     * Ticks of reveal remaining, indexed by {@link Faction#ordinal()}. Transient: no
     * {@code serialize}, no {@code sync}. The array is the same cached mutable instance on every
     * {@code getData} call, so it is written in place (the {@link net.bitflora.asteriskcraft.building.CoreCensus}
     * pattern) with no re-set needed.
     */
    public static final Supplier<AttachmentType<int[]>> REVEAL_TICKS = ATTACHMENT_TYPES.register(
            "reveal_ticks", () -> AttachmentType.<int[]>builder(
                    () -> new int[Faction.values().length]).build());

    private DetectionAttachments() {
    }

    public static byte detectedBy(Entity entity) {
        return entity.getData(DETECTED_BY);
    }

    public static int[] revealTicks(Entity entity) {
        return entity.getData(REVEAL_TICKS);
    }
}
