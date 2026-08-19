package net.bitflora.asteriskcraft.command;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.List;

/**
 * What the client is told about a player's {@link UnitGroups}: for each of the {@link
 * UnitGroups#SLOTS} slots, the live composition of that group as {@code (type, count)} pairs in
 * selection order. This is the whole of the server-to-client half of the feature — the overlay
 * needs to <em>label</em> groups ("1Z 3D"), and group membership only exists server-side.
 *
 * <p>Counts rather than UUIDs: the client never has to resolve an entity, and a group of 200
 * Zerglings costs one pair on the wire. Composition rather than a pre-formatted string: the
 * abbreviation is presentation, so it stays client-side in {@link UnitLabels}.
 */
public record UnitGroupSnapshot(List<Slot> slots) {

    /** One unit type's contribution to a group. */
    public record Entry(EntityType<?> type, int count) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.registry(Registries.ENTITY_TYPE), Entry::type,
                ByteBufCodecs.VAR_INT, Entry::count,
                Entry::new);
    }

    /** One slot's composition; an empty entry list means "nothing assigned here". */
    public record Slot(List<Entry> entries) {
        public static final Slot EMPTY = new Slot(List.of());

        public static final StreamCodec<RegistryFriendlyByteBuf, Slot> STREAM_CODEC =
                Entry.STREAM_CODEC.apply(ByteBufCodecs.list()).map(Slot::new, Slot::entries);

        public boolean isEmpty() {
            return this.entries.isEmpty();
        }

        /** Total units in the slot, across every type. */
        public int total() {
            return this.entries.stream().mapToInt(Entry::count).sum();
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, UnitGroupSnapshot> STREAM_CODEC =
            Slot.STREAM_CODEC.apply(ByteBufCodecs.list()).map(UnitGroupSnapshot::new, UnitGroupSnapshot::slots);

    /** All slots empty — what the client shows before the first sync arrives. */
    public static final UnitGroupSnapshot EMPTY = new UnitGroupSnapshot(emptySlots());

    private static List<Slot> emptySlots() {
        List<Slot> slots = new ArrayList<>(UnitGroups.SLOTS);
        for (int i = 0; i < UnitGroups.SLOTS; i++) {
            slots.add(Slot.EMPTY);
        }
        return List.copyOf(slots);
    }

    /**
     * The slot at {@code index}, or an empty one if the index is out of range — a snapshot that
     * arrived from an older/newer build must never crash the overlay mid-match.
     */
    public Slot slot(int index) {
        return index >= 0 && index < this.slots.size() ? this.slots.get(index) : Slot.EMPTY;
    }
}
