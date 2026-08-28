package net.bitflora.asteriskcraft.combat;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.entity.Detector;
import net.bitflora.asteriskcraft.faction.Cloaking;
import net.bitflora.asteriskcraft.faction.DetectionAttachments;
import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.bitflora.asteriskcraft.stats.UnitStat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.List;

/**
 * Drives detection: detectors periodically light up the enemies around them, and those reveals time
 * out on their own.
 *
 * <p><b>A reveal is about every enemy, not only a cloaked one.</b> A detector is the eye an army
 * fights through, so what it stamps is "this unit is currently seen" — the commanding player gets a
 * red outline on everything inside a detector's envelope, which is what makes a detector worth
 * building for an army with no cloaked enemy in the match at all. Cloak then rides on top of that
 * one flag without widening it: {@code Cloaking.isVisibleTo} short-circuits on units that are not
 * cloaked, so revealing a Zergling changes what the player <em>sees</em> and nothing at all about
 * what anything may <em>shoot</em>.
 *
 * <p>Both halves ride {@link EntityTickEvent.Post}, mirroring {@link RegenEventHandler}, which
 * keeps the whole mechanic out of every entity class — a unit becomes a detector or becomes cloaked
 * purely by implementing the marker, with no tick override of its own to remember.
 *
 * <p>Cost is bounded by design. The sweep is the only world scan and it runs once per
 * {@code sweepInterval} <em>per detector</em>, of which a match holds a handful; the expiry side is
 * two array reads on units carrying a reveal and nothing at all on units that aren't. Nothing ever
 * iterates the world looking for revealed units, which is what a naive "check every unit against
 * every detector" design would have to do.
 */
@EventBusSubscriber(modid = AsteriskCraft.MODID)
public final class DetectionHandler {
    private DetectionHandler() {
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide() || !entity.isAlive()) {
            return;
        }
        if (entity instanceof Detector detector) {
            sweep(entity, detector);
        }
        // Keyed on the mask and nothing else. That is what a unit walking out of a detector's reach
        // needs, and it is also why a Lurker that climbs out of the ground while revealed still
        // counts down: were this "if cloaked", it would come back up already lit, anywhere on the
        // map, forever.
        if (DetectionAttachments.detectedBy(entity) != 0) {
            expire(entity);
        }
    }

    /**
     * One detector's periodic look around. Staggered for free: {@code tickCount} starts at 0 when an
     * entity spawns, so two detectors built at different times never sweep on the same tick.
     */
    private static void sweep(Entity detectorEntity, Detector detector) {
        UnitStat.Detection envelope = detector.detection();
        if (detectorEntity.tickCount % envelope.sweepInterval() != 0) {
            return;
        }
        Faction faction = FactionAttachments.get(detectorEntity);
        if (faction == Faction.NEUTRAL) {
            return; // an unfactioned detector reveals for nobody
        }
        AABB box = detectorEntity.getBoundingBox().inflate(envelope.radius());
        // Strictly cross-faction, the same test the mask's consumers ask with: the wild carve-out
        // that lets a race pick fights with neutrals is about who may be shot, and a detector lights
        // up an opposing army rather than everything alive near it.
        List<LivingEntity> candidates = detectorEntity.level().getEntitiesOfClass(LivingEntity.class, box,
                candidate -> candidate.isAlive()
                        && FactionAttachments.get(candidate).isEnemy(faction));
        for (LivingEntity candidate : candidates) {
            // Radius against the real distance, not the inflated box's corners — a box test alone
            // would reveal a unit up to sqrt(3) times further away on the diagonal.
            if (candidate.distanceToSqr(detectorEntity) > envelope.radius() * envelope.radius()) {
                continue;
            }
            reveal(candidate, faction, envelope.revealTicks());
        }
    }

    /** Arms (or re-arms) one faction's reveal on a unit, and syncs the mask if it changed. */
    private static void reveal(Entity target, Faction detector, int revealTicks) {
        DetectionAttachments.revealTicks(target)[detector.ordinal()] = revealTicks;
        byte mask = DetectionAttachments.detectedBy(target);
        byte updated = Cloaking.with(mask, detector);
        if (updated != mask) {
            target.setData(DetectionAttachments.DETECTED_BY, updated);
        }
    }

    /**
     * Runs down every faction's countdown on one revealed unit and clears the bits that ran out. The
     * mask is only written on the edge, so a whole reveal window costs two sync packets rather than
     * one per tick.
     */
    private static void expire(Entity revealed) {
        int[] remaining = DetectionAttachments.revealTicks(revealed);
        byte mask = DetectionAttachments.detectedBy(revealed);
        byte updated = mask;
        for (Faction faction : Faction.values()) {
            int ordinal = faction.ordinal();
            if (remaining[ordinal] > 0) {
                remaining[ordinal]--;
            }
            // Unconditional rather than "on the tick it hit zero", so the mask is self-healing:
            // a bit with no countdown behind it can never get stuck set.
            if (remaining[ordinal] == 0) {
                updated = Cloaking.without(updated, faction);
            }
        }
        if (updated != mask) {
            revealed.setData(DetectionAttachments.DETECTED_BY, updated);
        }
    }
}
