package net.bitflora.asteriskcraft.director;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.building.HiveBlockEntity;
import net.bitflora.asteriskcraft.building.ResourceBank;
import net.bitflora.asteriskcraft.command.CommandAttachments;
import net.bitflora.asteriskcraft.command.CommandOrder;
import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.game.GameAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The Zerg AI brain (shape A9). Per-Hive worker economy lives on {@link HiveBlockEntity}; this
 * global director spends the pooled Hive resources on escalating attack waves and marches them at
 * the player's Nexus. Runs off {@link ServerTickEvent.Post}; all schedule state is kept in
 * {@link GameAttachments} so it survives save/reload. The wave-size and interval curves are pure
 * functions ({@link #waveSize}/{@link #intervalFor}) so they can be unit-tested.
 */
@EventBusSubscriber(modid = AsteriskCraft.MODID)
public final class ZergDirector {
    // Wave scheduling (ticks; 20 ticks = 1s).
    public static final int FIRST_WAVE_DELAY = 20 * 45;      // grace period before the first wave
    public static final int WAVE_INTERVAL_MAX = 20 * 75;     // slowest cadence (early game)
    public static final int WAVE_INTERVAL_MIN = 20 * 30;     // fastest cadence (late game)
    public static final int INTERVAL_STEP = 20 * 5;          // each wave comes 5s sooner

    // Wave composition.
    public static final int BASE_WAVE_SIZE = 3;
    public static final int MAX_WAVE_SIZE = 12;

    // Unit costs — mirror the Protoss equivalents exactly (see GatewayBlockEntity).
    public static final int ZERGLING_WOOD_COST = 50;
    public static final int ZERGLING_COBBLE_COST = 50;
    public static final int HYDRALISK_IRON_COST = 10;

    private static final List<ResourceBank.Cost> ZERGLING_COST = List.of(
            new ResourceBank.Cost(stack -> stack.is(ItemTags.LOGS), ZERGLING_WOOD_COST),
            new ResourceBank.Cost(stack -> stack.is(Items.COBBLESTONE), ZERGLING_COBBLE_COST));
    private static final List<ResourceBank.Cost> HYDRALISK_COST = List.of(
            new ResourceBank.Cost(stack -> stack.is(Items.IRON_INGOT), HYDRALISK_IRON_COST));

    private ZergDirector() {
    }

    /** How many units the given (0-based) wave fields, escalating up to {@link #MAX_WAVE_SIZE}. */
    public static int waveSize(int waveNumber) {
        return Math.min(MAX_WAVE_SIZE, BASE_WAVE_SIZE + waveNumber);
    }

    /** Ticks between wave {@code waveNumber} and the next, shrinking toward {@link #WAVE_INTERVAL_MIN}. */
    public static int intervalFor(int waveNumber) {
        return Math.max(WAVE_INTERVAL_MIN, WAVE_INTERVAL_MAX - waveNumber * INTERVAL_STEP);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ServerLevel overworld = event.getServer().overworld();
        if (!overworld.getData(GameAttachments.BOOTSTRAPPED) || overworld.getData(GameAttachments.GAME_OVER)) {
            return;
        }
        List<BlockPos> hivePositions = overworld.getData(GameAttachments.HIVE_POSITIONS);
        BlockPos nexus = overworld.getData(GameAttachments.NEXUS_POS);
        if (hivePositions.isEmpty() || nexus.equals(BlockPos.ZERO)) {
            return;
        }

        int countdown = overworld.getData(GameAttachments.WAVE_COUNTDOWN);
        if (countdown < 0) {
            countdown = FIRST_WAVE_DELAY;
        }
        if (--countdown <= 0) {
            int waveNumber = overworld.getData(GameAttachments.WAVE_NUMBER);
            launchWave(overworld, nexus, hivePositions, waveNumber);
            overworld.setData(GameAttachments.WAVE_NUMBER, waveNumber + 1);
            countdown = intervalFor(waveNumber + 1);
        }
        overworld.setData(GameAttachments.WAVE_COUNTDOWN, countdown);
    }

    private static void launchWave(ServerLevel overworld, BlockPos nexus, List<BlockPos> hivePositions, int waveNumber) {
        List<HiveBlockEntity> hives = new ArrayList<>();
        for (BlockPos pos : hivePositions) {
            if (overworld.getBlockEntity(pos) instanceof HiveBlockEntity hive) {
                hives.add(hive);
            }
        }
        if (hives.isEmpty()) {
            return; // Hive chunks not loaded / already gone
        }

        int size = waveSize(waveNumber);
        for (int i = 0; i < size; i++) {
            boolean hydralisk = i % 3 == 2; // roughly one ranged unit per three
            if (!payFromHives(hives, hydralisk ? HYDRALISK_COST : ZERGLING_COST)) {
                continue; // Zerg economy can't afford this unit right now
            }
            BlockPos spawnHive = hives.get(overworld.getRandom().nextInt(hives.size())).getBlockPos();
            Mob unit = hydralisk
                    ? ZergSpawns.spawn(overworld, spawnHive, AsteriskCraft.HYDRALISK.get(), Faction.ZERG, true)
                    : ZergSpawns.spawn(overworld, spawnHive, AsteriskCraft.ZERGLING.get(), Faction.ZERG, true);
            if (unit != null) {
                CommandAttachments.setOrder(unit, CommandOrder.move(nexus));
            }
        }
    }

    /** Pays a unit's cost from the first Hive that can afford it (atomic per Hive). */
    private static boolean payFromHives(List<HiveBlockEntity> hives, List<ResourceBank.Cost> cost) {
        for (HiveBlockEntity hive : hives) {
            if (ResourceBank.extractAll(hive, cost)) {
                return true;
            }
        }
        return false;
    }
}
