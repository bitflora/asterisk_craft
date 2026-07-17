package net.bitflora.asteriskcraft.entity;

import net.bitflora.asteriskcraft.entity.ai.CommandableGoals;
import net.bitflora.asteriskcraft.entity.ai.FactionTargetGoal;
import net.bitflora.asteriskcraft.entity.ai.SiegeBlockGoal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The Zerg melee unit — the mirror of {@link ZealotEntity}: a repurposed Zombie with vanilla
 * player-aggression stripped, faction targeting installed, plus a {@link SiegeBlockGoal} so it can
 * batter down the Nexus. Faster and squishier than a Zealot to read as a swarm unit. Sun-immune
 * because its EntityType is never added to {@code #minecraft:burn_in_daylight}.
 */
public class ZerglingEntity extends Zombie {

    public ZerglingEntity(EntityType<? extends ZerglingEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.ARMOR, 1.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new ZombieAttackGoal(this, 1.0, false));
        this.goalSelector.addGoal(3, new SiegeBlockGoal(this));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new FactionTargetGoal(this));
        CommandableGoals.install(this, this.goalSelector, this.targetSelector);
    }
}
