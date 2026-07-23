package net.bitflora.asteriskcraft.command;

import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A player's current unit selection: a set of entity UUIDs plus the selection operations
 * the command scheme needs. Stored as a transient per-player attachment
 * ({@link CommandAttachments#SELECTION}) — resetting on relog is fine. Membership changes
 * drive the vanilla glow flag on the affected units for visual feedback.
 */
public class PlayerSelection {
    /** How far Ctrl-click reaches when grabbing all units of a type around the player. */
    public static final double TYPE_SELECT_RADIUS = 24.0;

    private final Set<UUID> ids = new LinkedHashSet<>();

    public boolean isEmpty() {
        return this.ids.isEmpty();
    }

    public boolean contains(Mob unit) {
        return this.ids.contains(unit.getUUID());
    }

    /** Replace the whole selection with just this unit (plain left-click). */
    public void setSingle(ServerLevel level, Mob unit) {
        clear(level);
        add(unit);
    }

    /** Add the unit if absent, remove it if present (Shift+left-click). */
    public void toggle(ServerLevel level, Mob unit) {
        if (this.ids.remove(unit.getUUID())) {
            unit.setGlowingTag(false);
        } else {
            add(unit);
        }
    }

    /** Replace the selection with every friendly unit of {@code type} near {@code center} (Ctrl+left-click). */
    public void selectAllOfType(ServerLevel level, EntityType<?> type, Vec3 center, Faction owner) {
        clear(level);
        for (Mob unit : findFriendlyOfType(level, type, center, owner)) {
            add(unit);
        }
    }

    /**
     * Bulk add/remove every friendly unit of {@code type} near {@code center} (Ctrl+Shift+left-click):
     * if any is already selected, remove them all; otherwise add them all.
     */
    public void toggleAllOfType(ServerLevel level, EntityType<?> type, Vec3 center, Faction owner) {
        List<Mob> matching = findFriendlyOfType(level, type, center, owner);
        boolean anySelected = matching.stream().anyMatch(this::contains);
        if (anySelected) {
            for (Mob unit : matching) {
                if (this.ids.remove(unit.getUUID())) {
                    unit.setGlowingTag(false);
                }
            }
        } else {
            for (Mob unit : matching) {
                add(unit);
            }
        }
    }

    /** Replace the whole selection with this exact set of units (control-group recall). */
    public void replaceWith(ServerLevel level, Collection<Mob> units) {
        clear(level);
        for (Mob unit : units) {
            add(unit);
        }
    }

    /** Drop everything from the selection, clearing each unit's glow. */
    public void clear(ServerLevel level) {
        for (UUID id : this.ids) {
            if (level.getEntity(id) instanceof Mob unit) {
                unit.setGlowingTag(false);
            }
        }
        this.ids.clear();
    }

    /** Live, still-valid selected units; prunes dead/despawned ids as a side effect. */
    public List<Mob> pruneAndGet(ServerLevel level) {
        List<Mob> live = new ArrayList<>();
        Iterator<UUID> it = this.ids.iterator();
        while (it.hasNext()) {
            if (level.getEntity(it.next()) instanceof Mob unit && unit.isAlive()) {
                live.add(unit);
            } else {
                it.remove();
            }
        }
        return live;
    }

    public int size() {
        return this.ids.size();
    }

    private void add(Mob unit) {
        if (this.ids.add(unit.getUUID())) {
            unit.setGlowingTag(true);
        }
    }

    private static List<Mob> findFriendlyOfType(ServerLevel level, EntityType<?> type, Vec3 center, Faction owner) {
        AABB box = new AABB(center, center).inflate(TYPE_SELECT_RADIUS);
        return level.getEntitiesOfClass(Mob.class, box,
                unit -> unit.getType() == type && unit.isAlive() && FactionAttachments.get(unit) == owner);
    }
}
