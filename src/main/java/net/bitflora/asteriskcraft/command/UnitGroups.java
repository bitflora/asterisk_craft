package net.bitflora.asteriskcraft.command;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A player's saved unit groups: {@value #SLOTS} fixed slots, each holding a snapshot of unit UUIDs
 * taken from a prior {@link PlayerSelection}. Pure storage — no glow/{@code ServerLevel} coupling;
 * {@link UnitGroupResolver} owns translating slot contents to and from live {@code Mob}s.
 *
 * <p>Slots are addressed by <b>index</b> 0..9 throughout the server side and the wire. The digit a
 * slot is <em>labelled</em> with in the overlay (1-9 then 0) is purely presentation and lives in
 * {@code command.client.UnitGroupScreen} — nothing here knows about it.
 *
 * <p>Stored as a persisted per-player attachment ({@link CommandAttachments#UNIT_GROUPS}) — unlike
 * the transient {@link PlayerSelection}, saved groups survive relogin, since building one is
 * deliberate setup effort during a match and unit UUIDs survive a world save.
 */
public final class UnitGroups {
    /** How many slots the overlay lays out: 2 columns of 5. */
    public static final int SLOTS = 10;

    private final Map<Integer, List<UUID>> groups;

    public UnitGroups() {
        this(new HashMap<>());
    }

    private UnitGroups(Map<Integer, List<UUID>> groups) {
        this.groups = groups;
    }

    /** Overwrite a slot's contents. An empty collection is valid (clears the slot). */
    public void save(int slot, Collection<UUID> ids) {
        checkSlot(slot);
        this.groups.put(slot, List.copyOf(ids));
    }

    /** UUIDs stored in a slot, in selection order; empty list if never saved. */
    public List<UUID> get(int slot) {
        checkSlot(slot);
        return this.groups.getOrDefault(slot, List.of());
    }

    public boolean isEmpty(int slot) {
        return get(slot).isEmpty();
    }

    /** Whether {@code slot} is a legal slot index — the one place the range is stated. */
    public static boolean isValidSlot(int slot) {
        return slot >= 0 && slot < SLOTS;
    }

    private static void checkSlot(int slot) {
        if (!isValidSlot(slot)) {
            throw new IllegalArgumentException("slot " + slot + " outside 0.." + (SLOTS - 1));
        }
    }

    private record Entry(int slot, List<UUID> units) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.INT.fieldOf("slot").forGetter(Entry::slot),
                UUIDUtil.CODEC.listOf().fieldOf("units").forGetter(Entry::units)
        ).apply(inst, Entry::new));
    }

    public static final Codec<UnitGroups> CODEC = Entry.CODEC.listOf().xmap(
            entries -> new UnitGroups(entries.stream()
                    .filter(e -> isValidSlot(e.slot()))
                    .collect(Collectors.toMap(Entry::slot, Entry::units, (a, b) -> b, HashMap::new))),
            groups -> groups.groups.entrySet().stream()
                    .map(e -> new Entry(e.getKey(), e.getValue())).toList());
}
