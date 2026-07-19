package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Tracks the position of the last building (e.g. a Photon Cannon) that hit a given living
 * entity, so combat units can retaliate against ranged structures the same way
 * {@link net.bitflora.asteriskcraft.entity.ai.RetaliateGoal} lets them retaliate against a
 * living attacker. Transient only (not serialized): losing this on reload just means a unit
 * forgets who shot it, which is harmless.
 */
public final class BuildingAggroAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, AsteriskCraft.MODID);

    public static final Supplier<AttachmentType<Optional<BlockPos>>> ATTACKER_BUILDING = ATTACHMENT_TYPES.register(
            "attacker_building", () -> AttachmentType.<Optional<BlockPos>>builder(Optional::empty).build());

    private BuildingAggroAttachments() {
    }

    public static void markAttackedBy(LivingEntity target, BlockPos pos) {
        target.setData(ATTACKER_BUILDING, Optional.of(pos.immutable()));
    }

    public static Optional<BlockPos> getAttackerBuilding(LivingEntity entity) {
        return entity.getData(ATTACKER_BUILDING);
    }

    public static void clear(LivingEntity entity) {
        entity.setData(ATTACKER_BUILDING, Optional.empty());
    }
}
