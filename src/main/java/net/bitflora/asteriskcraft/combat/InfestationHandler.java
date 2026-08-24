package net.bitflora.asteriskcraft.combat;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.building.CoreCensus;
import net.bitflora.asteriskcraft.building.SpawnSpots;
import net.bitflora.asteriskcraft.command.CommandAttachments;
import net.bitflora.asteriskcraft.command.CommandOrder;
import net.bitflora.asteriskcraft.entity.zerg.InfestedVillagerEntity;
import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.bitflora.asteriskcraft.faction.Race;
import net.bitflora.asteriskcraft.game.MatchSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * The swarm's dividend on an overrun village: a Zerg kill on a villager has a
 * {@link Infestation#CHANCE} chance of raising the corpse as an {@link InfestedVillagerEntity}. This
 * is the only thing that ever produces one — there is no Hive button for it — which is what ties the
 * unit to the Zerg actually pushing into settled ground rather than to their economy.
 *
 * <p>The rule itself lives in {@link Infestation} so it can be tested without a world; everything
 * here is the world half. {@code LivingDeathEvent} fires from {@code LivingEntity#die} before any
 * teardown, so the victim still has a valid position and level at this point.
 *
 * <p>A fresh bomber is <b>aimed the moment it stands up</b>, at the nearest standing Protoss core
 * rather than a random one — see {@link CoreCensus#nearest} for why those two differ. Without an
 * order it would fall back on autonomous acquisition, and since its acquisition is narrowed to strict
 * cross-faction enemies (unlike every other Zerg unit), a bomber raised in a village with no Protoss
 * in sight would simply stand among the corpses. The march is the point: it is a body with one
 * detonation in it and a base to spend it on.
 */
@EventBusSubscriber(modid = AsteriskCraft.MODID)
public final class InfestationHandler {

    private InfestationHandler() {
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) {
            return;
        }
        Entity killer = event.getSource().getEntity();
        if (killer == null) {
            return;
        }
        Faction raiser = FactionAttachments.get(killer);
        Race raiserRace = FactionAttachments.raceOf(killer);
        if (!Infestation.infests(raiserRace, victim.getClass(), level.getRandom().nextFloat())) {
            return;
        }
        raise(level, victim.blockPosition(), raiser, raiserRace);
    }

    /**
     * Stands one bomber up on the corpse, on the side of whoever raised it, and points it at that
     * side's opponent's nearest base — so a match with the sides swapped sends the bomber the other
     * way with no change here.
     */
    private static void raise(ServerLevel level, BlockPos where, Faction raiser, Race raiserRace) {
        InfestedVillagerEntity bomber =
                AsteriskCraft.INFESTED_VILLAGER.get().create(level, EntitySpawnReason.TRIGGERED);
        if (bomber == null) {
            return;
        }
        // Placed through the shared spot search rather than dropped on the victim's block: the corpse
        // is not gone yet, and a village is exactly the kind of cramped space where the naive spot is
        // inside a wall.
        BlockPos spot = SpawnSpots.findGroundSpot(level, where, AsteriskCraft.INFESTED_VILLAGER.get());
        bomber.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5,
                level.getRandom().nextFloat() * 360f, 0f);
        EventHooks.finalizeMobSpawn(bomber, level, level.getCurrentDifficultyAt(spot),
                EntitySpawnReason.TRIGGERED, null);
        FactionAttachments.set(bomber, raiser, raiserRace);
        level.addFreshEntity(bomber);

        CoreCensus.nearest(level, MatchSetup.of(level).opponentOf(raiser), spot)
                .ifPresent(core -> CommandAttachments.setOrder(bomber, CommandOrder.move(core)));

        level.playSound(null, spot, SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.HOSTILE,
                1.0f, 0.7f);
    }
}
