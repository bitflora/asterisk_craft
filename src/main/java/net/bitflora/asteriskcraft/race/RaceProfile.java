package net.bitflora.asteriskcraft.race;

import net.bitflora.asteriskcraft.building.ProductionKind;
import net.bitflora.asteriskcraft.faction.Race;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Everything about a {@link Race} that generic code needs but must not know first-hand: what its
 * base is built from, what it can produce, what its army bank looks like, and what its starting
 * position on the map looks like.
 *
 * <p>This is the one table that knows a Hive is a Zerg base. Nothing above it — not
 * {@code building.BaseBlockEntity}, not {@code director.AiDirector}, not {@code game.GameBootstrap}
 * — names a Hive, a Nexus, a Drone or a Probe; they ask a profile. Adding a race is an entry in
 * {@link Races} plus its assets, exactly as adding one to {@code faction.WildKind}'s targeting
 * table is a single edit (see CLAUDE.md).
 *
 * <p>Registry-bound values are held as {@link Supplier}s because the table is built at class-init,
 * well before blocks and entity types finish registering. Nothing here is resolved until a profile
 * is actually used in a world.
 *
 * @param race           which race this describes
 * @param coreBlock      the base's core block (a Nexus core, a Hive core)
 * @param baseTemplate   the {@code .nbt} structure the base is stamped from
 * @param baseHealth     the base's siege HP pool
 * @param baseShield     the base's shield buffer; 0 for a race without shields
 * @param baseWarpTicks  how long a kit-warped base of this race spends warping in
 * @param bankSlots      size of this race's shared army bank
 * @param roster         what this race can produce, including its worker
 * @param productionKind the base's command card, or null for a race whose base has no GUI yet.
 *                       A supplier, not the value: {@link ProductionKind}'s constants resolve
 *                       registered items for their button icons at class-init, so naming one
 *                       directly here would drag item registration forward into this table's own
 *                       class-init. Read it through {@link #production()}.
 * @param buildScript    the AI build script this race runs when it is the computer player
 * @param creep          ground cover spread around a new base, or null for a race that lays none
 * @param support        what a base's template fills gaps beneath it with, or null to leave the
 *                       ground as it lies (the Protoss stonework is its own plinth)
 * @param baseDefences   static defence planted with each base at world generation. The
 *                       <em>computer's</em> only — the human's starting base deliberately plants
 *                       none and gets {@link #playerKit} instead, so it mines its own way up
 * @param baseEscort     mobile units spawned beside every base this race starts the match with,
 *                       on <em>both</em> sides. That symmetry is the whole reason it is not folded
 *                       into {@link #baseDefences}: a rule like "every Hive comes with an Overlord"
 *                       has to hold for the player's base as much as the computer's, where static
 *                       defence deliberately does not. Bootstrap only — a base warped in later from
 *                       a kit brings no escort, because it is the <em>starting</em> position this
 *                       describes
 * @param startingBank   what this race's shared bank is seeded with when it is the computer player
 * @param playerBank     what this race's shared bank is seeded with when it is the <em>human's</em>
 *                       — a fraction of {@link #startingBank}, because a player mines their own way
 *                       up while the computer is handed a stockpile to bankroll its script
 * @param playerKit      what goes into the human's inventory at world start. The Command Crystal is
 *                       not in here: it is race-generic and every player gets one. These are the
 *                       race-flavoured openers — a Pylon and a Cannon kit, a Sunken Colony egg
 */
public record RaceProfile(
        Race race,
        Supplier<Block> coreBlock,
        Identifier baseTemplate,
        int baseHealth,
        int baseShield,
        int baseWarpTicks,
        int bankSlots,
        UnitRoster roster,
        @Nullable Supplier<ProductionKind> productionKind,
        Identifier buildScript,
        @Nullable Supplier<BlockState> creep,
        @Nullable Supplier<BlockState> support,
        List<BaseDefence> baseDefences,
        List<BaseDefence> baseEscort,
        List<StartingStack> startingBank,
        List<StartingStack> playerBank,
        List<Supplier<Item>> playerKit) {

    /**
     * A unit spawned with each base at world generation, and how many of it. Used by both
     * {@link #baseDefences} (the computer's static defence) and {@link #baseEscort} (each side's
     * mobile escort) — the two differ in <em>which bases</em> they are walked for, not in shape.
     */
    public record BaseDefence(Supplier<? extends EntityType<? extends Mob>> type, int count) {
    }

    /** One resource this race's bank starts stocked with. Split across slots by the seeder. */
    public record StartingStack(Supplier<Item> item, int amount) {
    }

    /** This race's worker — shorthand for the one roster lookup nearly every caller wants. */
    public UnitRoster.UnitDef worker() {
        return this.roster.worker();
    }

    /** The base's command card, or empty for a race whose base has no GUI. */
    public Optional<ProductionKind> production() {
        return this.productionKind == null ? Optional.empty() : Optional.of(this.productionKind.get());
    }
}
