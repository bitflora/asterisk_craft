package net.bitflora.asteriskcraft.faction;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Data attachment tagging any entity with the faction it fights for.
 * All targeting logic must resolve hostility through this, never through
 * entity class checks, so units can be swapped per race later.
 */
public final class FactionAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, AsteriskCraft.MODID);

    public static final Supplier<AttachmentType<Faction>> FACTION = ATTACHMENT_TYPES.register(
            "faction", () -> AttachmentType.builder(() -> Faction.NEUTRAL)
                    .serialize(Faction.CODEC.fieldOf("faction"))
                    .sync(Faction.STREAM_CODEC)
                    .build());

    private FactionAttachments() {
    }

    public static Faction get(Entity entity) {
        return entity.getData(FACTION);
    }

    public static void set(Entity entity, Faction faction) {
        entity.setData(FACTION, faction);
    }

    /** Strict faction hostility: cross-faction only, and NEUTRAL fights no one. */
    public static boolean areEnemies(Entity a, Entity b) {
        return get(a).isEnemy(get(b));
    }

    /**
     * Whether {@code self} should fight {@code candidate} — the question all combat code asks.
     * True for an enemy faction, and also for a <em>wild</em> hostile mob (a zombie, creeper,
     * skeleton) so units defend themselves against the world rather than standing and watching.
     *
     * <p>The second half is needed because vanilla monsters carry no faction attachment, so they
     * default to {@link Faction#NEUTRAL}, and {@link Faction#isEnemy} deliberately says NEUTRAL
     * fights no one — that invariant is what stops units attacking the player and wild animals, so
     * it must not be relaxed. Instead the wild-monster case is carved out narrowly, and gated twice:
     * <ul>
     *   <li>on {@code candidate} actually being NEUTRAL, since the mod's own combat units (Zealot,
     *       Zergling, Dragoon, Hydralisk...) are themselves {@code Monster} subclasses and must
     *       never be caught by a bare class check;</li>
     *   <li>on {@code self} not being NEUTRAL, so an unfactioned unit still fights nobody.</li>
     * </ul>
     *
     * <p>This is the one place the {@code Monster} class check lives. Targeting code calls this and
     * stays free of entity-type tests, exactly as before — the rule is centralized, not scattered.
     */
    public static boolean isHostile(Entity self, Entity candidate) {
        return isHostile(get(self), get(candidate), candidate instanceof Monster);
    }

    /** The pure rule behind {@link #isHostile(Entity, Entity)}, free of any live entity. */
    public static boolean isHostile(Faction self, Faction candidate, boolean wildMonster) {
        return self.isEnemy(candidate)
                || (self != Faction.NEUTRAL && candidate == Faction.NEUTRAL && wildMonster);
    }
}
