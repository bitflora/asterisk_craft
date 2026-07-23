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
 * A player's saved control groups (numpad slots 1-9): each slot holds a snapshot of unit UUIDs
 * from a prior {@link PlayerSelection}. Pure storage — no glow/{@code ServerLevel} coupling; the
 * resolver ({@link ControlGroupResolver}) owns translating slot contents to/from live
 * {@code Mob}s and driving {@link PlayerSelection}'s glow side effects. Stored as a persisted
 * per-player attachment ({@link CommandAttachments#CONTROL_GROUPS}) — unlike the transient
 * {@link PlayerSelection} attachment, saved groups survive relogin since building one represents
 * deliberate setup effort during a match.
 */
public final class ControlGroups {
    private final Map<Integer, List<UUID>> groups;

    public ControlGroups() {
        this(new HashMap<>());
    }

    private ControlGroups(Map<Integer, List<UUID>> groups) {
        this.groups = groups;
    }

    /** Overwrite slot's contents. An empty collection is valid (saves an empty group). */
    public void save(int slot, Collection<UUID> ids) {
        this.groups.put(slot, List.copyOf(ids));
    }

    /** UUIDs stored in slot; empty list if never saved. */
    public List<UUID> get(int slot) {
        return this.groups.getOrDefault(slot, List.of());
    }

    private record Entry(int slot, List<UUID> units) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.INT.fieldOf("slot").forGetter(Entry::slot),
                UUIDUtil.CODEC.listOf().fieldOf("units").forGetter(Entry::units)
        ).apply(inst, Entry::new));
    }

    public static final Codec<ControlGroups> CODEC = Entry.CODEC.listOf().xmap(
            entries -> new ControlGroups(entries.stream()
                    .collect(Collectors.toMap(Entry::slot, Entry::units, (a, b) -> b, HashMap::new))),
            groups -> groups.groups.entrySet().stream().map(e -> new Entry(e.getKey(), e.getValue())).toList());
}
