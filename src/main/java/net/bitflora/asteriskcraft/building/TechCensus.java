package net.bitflora.asteriskcraft.building;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.race.UnitRoster;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

/**
 * A running tally of every finished {@link StructureBlockEntity} standing in a level, so production
 * code can ask "does this army own a Spawning Pool?" without knowing what a Spawning Pool is. It is
 * the mod's only answer to that question, the way {@link CoreCensus} is the only answer to where a
 * faction's bases are — and it is deliberately a second census rather than a widening of that one:
 * a core decides the match, a structure decides what an army may produce, and only bases are
 * {@link FactionCore}s.
 *
 * <p>What each unit needs is not here. That is a column of {@code race.UnitRoster}, so this class
 * names no building, no unit and no race; it only answers whether one army holds one block.
 *
 * <p>Structures enrol themselves from their own server tick, and only <em>after</em> their build
 * countdown ends — which is what makes "owns" mean "owns a finished one", with no warp-state check
 * at the query end. Self-enrolment also back-fills worlds saved before this existed, exactly as
 * {@link CoreCensus}'s does.
 *
 * <p>An entry carries the block's <b>id</b> rather than being re-read from the world, so a Spawning
 * Pool sitting in an unloaded chunk still counts. Pruning therefore mirrors
 * {@link CoreCensus#standing}: an entry is dropped only where the chunk is loaded and the building
 * is genuinely gone or has changed sides, which is what catches a structure removed by a route that
 * skips its block entity's side effects (e.g. {@code /setblock}).
 */
public final class TechCensus {

    /** One standing finished structure: which army owns it, which block it is, and where. */
    public record Entry(Faction faction, Identifier block, BlockPos pos) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Faction.CODEC.fieldOf("faction").forGetter(Entry::faction),
                Identifier.CODEC.fieldOf("block").forGetter(Entry::block),
                BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos)
        ).apply(inst, Entry::new));
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, AsteriskCraft.MODID);

    public static final Supplier<AttachmentType<List<Entry>>> STRUCTURES = ATTACHMENT_TYPES.register(
            "tech_census", () -> AttachmentType.<List<Entry>>builder(() -> new ArrayList<Entry>())
                    .serialize(Entry.CODEC.listOf()
                            .<List<Entry>>xmap(ArrayList::new, List::copyOf)
                            .fieldOf("structures"))
                    .build());

    private TechCensus() {
    }

    /** Enrols a structure if it isn't already listed. Cheap and idempotent — safe to call every tick. */
    public static void ensureRegistered(Level level, Faction faction, Identifier block, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        List<Entry> structures = serverLevel.getData(STRUCTURES.get());
        for (Entry entry : structures) {
            if (entry.pos().equals(pos)) {
                return;
            }
        }
        structures.add(new Entry(faction, block, pos.immutable()));
    }

    /** Drops a structure from the tally — called as it is destroyed. */
    public static void unregister(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        serverLevel.getData(STRUCTURES.get()).removeIf(entry -> entry.pos().equals(pos));
    }

    /**
     * Whether {@code entries} contains a {@code block} owned by {@code faction}. Pure, so the rule
     * itself is unit-testable with no live level — the same split {@link PsiField#covered(BlockPos,
     * java.util.Collection)} draws between the geometry and the chunk walk that feeds it.
     */
    static boolean holds(List<Entry> entries, Faction faction, Identifier block) {
        for (Entry entry : entries) {
            if (entry.faction() == faction && entry.block().equals(block)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The building {@code def} needs and {@code faction} does not have, or null if this army may
     * produce it. Returning the missing block rather than a boolean is what lets both call sites
     * name it in their refusal without either of them knowing which buildings exist.
     */
    public static @Nullable Block missing(ServerLevel level, Faction faction, UnitRoster.UnitDef def) {
        return missing(standing(level), faction, def);
    }

    private static @Nullable Block missing(List<Entry> entries, Faction faction, UnitRoster.UnitDef def) {
        Block required = def.requires();
        if (required == null) {
            return null;
        }
        return holds(entries, faction, BuiltInRegistries.BLOCK.getKey(required)) ? null : required;
    }

    /**
     * Which of {@code kind}'s buttons this army may not press yet, as a bitmask over the card's
     * option indices — the one thing the client cannot work out for itself, since a structure
     * publishes neither its owner nor its build state to a blockstate. Sent down the production
     * menu's {@link ProductionMenu#DATA_LOCKED} data slot, which is why a mask rather than a list.
     *
     * <p>Only {@code TrainUnit} buttons can be locked: a worker has no prerequisite and a kit is a
     * purchase rather than a production. The census is walked once for the whole card rather than
     * once per button.
     */
    public static int lockedOptions(@Nullable Level level, Faction faction, ProductionKind kind,
            UnitRoster roster) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return 0;
        }
        List<Entry> entries = standing(serverLevel);
        List<ProductionKind.OptionView> options = kind.options();
        int locked = 0;
        for (int i = 0; i < options.size(); i++) {
            if (!(options.get(i).action() instanceof ProductionKind.Action.TrainUnit(String rosterId))) {
                continue;
            }
            if (roster.resolve(rosterId).map(def -> missing(entries, faction, def) != null).orElse(false)) {
                locked |= 1 << i;
            }
        }
        return locked;
    }

    /**
     * Every enrolled structure, pruning the ones that are gone. Shares {@link CoreCensus#standing}'s
     * rule about unloaded chunks: a building nobody has visited since the last save still counts,
     * because the alternative is an army silently losing its tech whenever it walks away from it.
     */
    private static List<Entry> standing(ServerLevel level) {
        List<Entry> structures = level.getData(STRUCTURES.get());
        for (Iterator<Entry> it = structures.iterator(); it.hasNext(); ) {
            Entry entry = it.next();
            if (level.hasChunkAt(entry.pos())
                    && !(level.getBlockEntity(entry.pos()) instanceof StructureBlockEntity structure
                    && structure.buildingFaction() == entry.faction())) {
                it.remove();
            }
        }
        return structures;
    }
}
