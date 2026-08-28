package net.bitflora.asteriskcraft.building;

import com.mojang.serialization.Codec;
import net.bitflora.asteriskcraft.race.UnitRoster;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;

/**
 * What a production building has in the works: a line of roster ids and the countdown on whichever
 * is at its head. Owned by every building that produces units — a base ({@link BaseBlockEntity}) and
 * a unit factory ({@link FactoryBlockEntity}) — so the two share one queue rather than one each.
 *
 * <p><b>Ids rather than option indices</b>, because the queue is saved: a command card reordered in
 * a later version would otherwise turn a saved queue into whatever now sits at those positions. Ids
 * are also what lets one queue hold a mix — a Hive's card morphs combat units as well as training
 * Drones — and each entry takes <em>its own</em> build time, so an Ultralisk behind a Zergling waits
 * an Ultralisk's.
 *
 * <p>Deliberately not a pure value type: it resolves build times through the {@link UnitRoster} it
 * was built with, so a caller never has to hand one in and can never hand in the wrong one. The
 * roster arrives as a {@link Supplier} because a block entity is constructed before registries
 * finish loading.
 */
public final class UnitQueue {

    /** How many units may be waiting at once, head included. */
    public static final int MAX = 5;

    private static final Codec<List<String>> QUEUE_CODEC = Codec.STRING.listOf();

    private final Supplier<UnitRoster> roster;
    private final Deque<String> ids = new ArrayDeque<>();
    /** Counts down the head of the queue; 0 whenever nothing is in production. */
    private int buildTicksRemaining;

    public UnitQueue(Supplier<UnitRoster> roster) {
        this.roster = roster;
    }

    public boolean isEmpty() {
        return this.ids.isEmpty();
    }

    public boolean isFull() {
        return this.ids.size() >= MAX;
    }

    public int size() {
        return this.ids.size();
    }

    /** The unit in production, or null when nothing is. */
    public @Nullable String head() {
        return this.ids.peek();
    }

    public int buildTicksRemaining() {
        return this.buildTicksRemaining;
    }

    /** The build time of a roster id, off the balance table; 0 for one this roster can't build. */
    public int buildTicksOf(String rosterId) {
        return this.roster.get().resolve(rosterId).map(UnitRoster.UnitDef::buildTicks).orElse(0);
    }

    /** How many of {@code rosterId} are waiting, the one in production included. */
    public int countOf(String rosterId) {
        int count = 0;
        for (String queued : this.ids) {
            if (rosterId.equals(queued)) {
                count++;
            }
        }
        return count;
    }

    /** Adds one unit to the back, starting its countdown if the queue was idle. */
    public void add(String rosterId) {
        boolean wasIdle = this.ids.isEmpty();
        this.ids.add(rosterId);
        if (wasIdle) {
            this.buildTicksRemaining = buildTicksOf(rosterId);
        }
    }

    /**
     * Advances the countdown by one tick and returns the id that just finished, or null if none did
     * — including when there was nothing in production at all. A caller that wants to know whether
     * anything is happening asks {@link #isEmpty()} first; this deliberately doesn't conflate "idle"
     * with "still building", since the two want different bookkeeping.
     */
    public @Nullable String tick() {
        if (this.ids.isEmpty()) {
            return null;
        }
        if (--this.buildTicksRemaining > 0) {
            return null;
        }
        String finished = this.ids.poll();
        // The next unit's own build time, not this one's: the queue is mixed.
        this.buildTicksRemaining = this.ids.isEmpty() ? 0 : buildTicksOf(this.ids.peek());
        return finished;
    }

    /** Throws the whole line away. True if there was anything to throw. */
    public boolean clear() {
        if (this.ids.isEmpty()) {
            return false;
        }
        this.ids.clear();
        this.buildTicksRemaining = 0;
        return true;
    }

    public void save(ValueOutput output) {
        output.store("Queue", QUEUE_CODEC, List.copyOf(this.ids));
        output.putInt("BuildTicks", this.buildTicksRemaining);
    }

    /**
     * @param legacy what to read back for a save written before {@code Queue} existed — see
     *               {@code BaseBlockEntity}, whose bare {@code Queued} count meant that many workers
     */
    public void load(ValueInput input, Supplier<List<String>> legacy) {
        this.ids.clear();
        this.ids.addAll(input.read("Queue", QUEUE_CODEC).orElseGet(legacy));
        // Read after the queue: with per-unit build times, the fallback for a save without one is
        // the head unit's own, which there is no way to know before the queue is in hand.
        this.buildTicksRemaining = input.getIntOr("BuildTicks",
                this.ids.isEmpty() ? 0 : buildTicksOf(this.ids.peek()));
    }
}
