package net.bitflora.asteriskcraft.race;

import net.bitflora.asteriskcraft.stats.UnitCost;
import net.bitflora.asteriskcraft.stats.UnitStat;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * One race's producible roster: which units a build script (or a command card) can name, and how
 * each is produced. The numbers themselves — cost and build time included — come from
 * {@link net.bitflora.asteriskcraft.stats.UnitStats}, the single source of truth for unit stats;
 * a roster only wires that table up to an {@code EntityType} under a resolvable name.
 *
 * <p>Names resolve case-insensitively, and a unit's script name is exactly its
 * {@link UnitStat#id()}.
 *
 * <p>An instance per race rather than a static table, held in {@link RaceProfile}, so the AI
 * director can run whichever race it is handed. Entity types are resolved lazily on first
 * {@link #resolve} — the rosters are built at {@code Races} class-init, which happens well before
 * entity-type registration completes.
 */
public final class UnitRoster {

    /** A producible unit: its entity type, its cost and build time, and whether it is the worker. */
    public record UnitDef(EntityType<? extends Mob> type, UnitCost cost, int buildTicks, boolean isWorker) {
    }

    private record Entry(UnitStat stat, Supplier<? extends EntityType<? extends Mob>> type, boolean isWorker) {
    }

    private final List<Entry> entries;
    private final String workerId;
    /** Lazily built after entity-type registration completes; keys are lower-cased. */
    private volatile Map<String, UnitDef> byName;

    private UnitRoster(List<Entry> entries, String workerId) {
        this.entries = List.copyOf(entries);
        this.workerId = workerId;
    }

    /** Resolves a unit name (any case) to its definition, or empty if this race has no such unit. */
    public Optional<UnitDef> resolve(String name) {
        return Optional.ofNullable(byName().get(normalize(name)));
    }

    /**
     * A script's spelling of a unit name reduced to its {@link UnitStat#id()} form. Case is
     * ignored, and any run of spaces or hyphens folds to the single underscore an id uses — so a
     * script can write "Dark Templar" for {@code dark_templar}, which is the only spelling a
     * person would think to type.
     */
    private static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
    }

    /** This race's worker — the unit the AI director keeps a standing floor of. */
    public UnitDef worker() {
        return byName().get(this.workerId);
    }

    /** Every name this roster answers to, for validating a build script against it. */
    public List<String> names() {
        return this.entries.stream().map(entry -> entry.stat().id()).toList();
    }

    private Map<String, UnitDef> byName() {
        Map<String, UnitDef> local = this.byName;
        if (local == null) {
            local = build();
            this.byName = local;
        }
        return local;
    }

    private Map<String, UnitDef> build() {
        Map<String, UnitDef> map = new LinkedHashMap<>();
        for (Entry entry : this.entries) {
            UnitStat stat = entry.stat();
            map.put(stat.id(), new UnitDef(entry.type().get(), stat.cost(), stat.buildTicks(), entry.isWorker()));
        }
        return Map.copyOf(map);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Collects a race's units. Exactly one must be declared the {@link #worker}, since the AI
     * director's standing worker floor has to know what to train — {@link #build()} throws if a
     * roster is assembled without one, so a half-configured race fails at class-init rather than
     * the first time the director tries to rebuild its economy.
     */
    public static final class Builder {
        private final List<Entry> entries = new ArrayList<>();
        private String workerId;

        private Builder() {
        }

        /** The race's worker; exactly one per roster. */
        public Builder worker(UnitStat stat, Supplier<? extends EntityType<? extends Mob>> type) {
            if (this.workerId != null) {
                throw new IllegalStateException("roster already has worker " + this.workerId);
            }
            this.workerId = stat.id();
            this.entries.add(new Entry(stat, type, true));
            return this;
        }

        public Builder unit(UnitStat stat, Supplier<? extends EntityType<? extends Mob>> type) {
            this.entries.add(new Entry(stat, type, false));
            return this;
        }

        public UnitRoster build() {
            if (this.workerId == null) {
                throw new IllegalStateException("roster declares no worker");
            }
            return new UnitRoster(this.entries, this.workerId);
        }
    }
}
