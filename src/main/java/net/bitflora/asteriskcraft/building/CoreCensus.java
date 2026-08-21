package net.bitflora.asteriskcraft.building;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.faction.Faction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A running tally of every {@link FactionCore} standing in a level, so any code can ask "how many
 * bases does this faction still have?" — and where they are — without knowing what a Nexus or a
 * Hive is. It is the mod's only answer to that question: the bootstrap used to write one position
 * for the player's base and a list for the computer's, which was both asymmetric and blind to any
 * base warped in from a kit afterwards.
 *
 * <p>Cores enrol themselves from their server tick rather than at their placement sites, which
 * makes the census independent of how a core came to exist (bootstrap, kit warp-in, creative
 * placement) and back-fills worlds saved before this existed. Kept on the core's own level,
 * mirroring {@link ArmyBank#of}, so a census and the bank it is counted for always describe the
 * same level.
 *
 * <p>Like {@link ArmyBank}, the attachment list is the same cached mutable instance on every
 * {@code getData} call, so it is mutated in place and persists with no extra plumbing.
 */
public final class CoreCensus {

    /** One standing core: which army it belongs to and where its core block is. */
    public record Entry(Faction faction, BlockPos pos) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Faction.CODEC.fieldOf("faction").forGetter(Entry::faction),
                BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos)
        ).apply(inst, Entry::new));
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, AsteriskCraft.MODID);

    public static final Supplier<AttachmentType<List<Entry>>> CORES = ATTACHMENT_TYPES.register(
            "core_census", () -> AttachmentType.<List<Entry>>builder(() -> new ArrayList<Entry>())
                    .serialize(Entry.CODEC.listOf()
                            .<List<Entry>>xmap(ArrayList::new, List::copyOf)
                            .fieldOf("cores"))
                    .build());

    private CoreCensus() {
    }

    /** Enrols a core if it isn't already listed. Cheap and idempotent — safe to call every tick. */
    public static void ensureRegistered(Level level, Faction faction, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        List<Entry> cores = serverLevel.getData(CORES.get());
        for (Entry entry : cores) {
            if (entry.pos().equals(pos)) {
                return;
            }
        }
        cores.add(new Entry(faction, pos.immutable()));
    }

    /** Drops a core from the tally — called as it is destroyed. */
    public static void unregister(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        serverLevel.getData(CORES.get()).removeIf(entry -> entry.pos().equals(pos));
    }

    /**
     * Where every still-standing core of {@code faction} is. This is what lets code pick <i>a</i>
     * core rather than assume there is only one — the AI director rolls its next wave's target out
     * of this list, so an expansion base is attacked like any other.
     *
     * <p>Also prunes entries whose block is gone — but only where the chunk is already loaded, so
     * a Nexus sitting in an unloaded chunk still counts instead of being quietly forgotten. That
     * covers cores removed by routes that skip the block entity's side effects (e.g. /setblock).
     */
    public static List<BlockPos> standing(ServerLevel level, Faction faction) {
        List<Entry> cores = level.getData(CORES.get());
        List<BlockPos> found = new ArrayList<>();
        for (Iterator<Entry> it = cores.iterator(); it.hasNext(); ) {
            Entry entry = it.next();
            if (level.hasChunkAt(entry.pos())
                    && !(level.getBlockEntity(entry.pos()) instanceof FactionCore core
                    && core.buildingFaction() == entry.faction())) {
                it.remove();
                continue;
            }
            if (entry.faction() == faction) {
                found.add(entry.pos());
            }
        }
        return found;
    }

    /**
     * The standing core of {@code faction} closest to {@code from}, or empty if it has none left.
     *
     * <p>Distinct from what {@link net.bitflora.asteriskcraft.director.AiDirector} does with
     * {@link #standing}: a wave picks its target at <em>random</em>, deliberately, so the computer
     * spreads its attention across a player's expansions. A unit raised in the field has no such
     * choice to make — it is one body with one detonation and nothing to spread — so it marches on
     * whatever base is actually nearest, which is also the only reading of "aim it at the player's
     * base" that means anything when there are several.
     *
     * <p>Shares {@link #standing}'s pruning, so the census is still cleaned by exactly one code path.
     */
    public static Optional<BlockPos> nearest(ServerLevel level, Faction faction, BlockPos from) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : standing(level, faction)) {
            double dist = pos.distSqr(from);
            if (dist < bestDist) {
                bestDist = dist;
                best = pos;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * How many cores of {@code faction} are still standing, ignoring {@code excluding} (the one
     * being destroyed, which may not have been unregistered yet). Shares {@link #standing}'s
     * pruning so the census is cleaned by exactly one code path.
     */
    public static int count(ServerLevel level, Faction faction, BlockPos excluding) {
        int standing = 0;
        for (BlockPos pos : standing(level, faction)) {
            if (!pos.equals(excluding)) {
                standing++;
            }
        }
        return standing;
    }
}
