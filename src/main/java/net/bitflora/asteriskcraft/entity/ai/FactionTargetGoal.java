package net.bitflora.asteriskcraft.entity.ai;

import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;

/**
 * Targets the nearest entity of an enemy faction, full stop: entity type is not a
 * criterion, so an enemy-faction Probe is as valid a target as an enemy soldier.
 * Same-faction entities are spared and NEUTRAL entities (the player, wild mobs) are
 * never targeted, purely because {@link net.bitflora.asteriskcraft.faction.Faction#isEnemy}
 * says so. Faction-generic: works for any owner/target faction pairing.
 */
public class FactionTargetGoal extends NearestAttackableTargetGoal<LivingEntity> {

    public FactionTargetGoal(Mob owner) {
        super(owner, LivingEntity.class, 10, true, false,
                (target, level) -> FactionAttachments.areEnemies(owner, target));
    }
}
