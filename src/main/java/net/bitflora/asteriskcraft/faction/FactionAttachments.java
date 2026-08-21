package net.bitflora.asteriskcraft.faction;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.minecraft.world.entity.Entity;
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
     * True for an enemy faction, and also for the parts of the <em>unfactioned</em> world its race
     * hunts: a wild hostile mob for the Protoss, a villager or golem for the Zerg.
     *
     * <p>That second half is needed because everything the mod doesn't own carries no faction
     * attachment, so it defaults to {@link Faction#NEUTRAL}, and {@link Faction#isEnemy}
     * deliberately says NEUTRAL fights no one — that invariant is what stops units attacking the
     * player and wild animals, so it must not be relaxed. Instead the neutral world is classified
     * once ({@link WildKind}) and each race declares which classes of it it picks a fight with
     * ({@link Faction#attacksWild}), which is why the rule can differ per race without a single
     * race check appearing in targeting code. It stays gated twice:
     * <ul>
     *   <li>on {@code candidate} actually being NEUTRAL, since the mod's own combat units (Zealot,
     *       Zergling, Dragoon, Hydralisk...) implement {@code Enemy} themselves (via {@code
     *       Monster}) and must never be caught by a bare class check;</li>
     *   <li>on {@code self}'s own set — NEUTRAL's is empty, so an unfactioned unit still fights
     *       nobody.</li>
     * </ul>
     *
     * <p>This is the one place the neutral-world carve-out lives. Targeting code calls this and
     * stays free of entity-type tests, exactly as before — the rule is centralized, not scattered.
     *
     * <p>It is also where <b>cloak</b> is enforced ({@link Cloaking}): a {@link Cloaked} enemy that
     * no detector of {@code self}'s faction currently reveals is not hostile, because as far as
     * {@code self} is concerned it is not there at all. Putting the gate here rather than in each
     * goal is what makes it total — acquisition ({@code FactionTargetGoal}), <em>retaliation</em>
     * ({@code RetaliateGoal}) and splash chains ({@code HitscanAttacks.fireChained}) all resolve
     * hostility through this one call, so an undetected cloaked attacker cannot even be swung back
     * at. That is the intended rule, and it is the whole reason cloak is frightening rather than
     * cosmetic.
     *
     * <p>The pure overload below is deliberately left cloak-free: cloak is a fact about a live
     * entity, not about a pair of factions, and keeping it out preserves that rule's testability.
     * The one targeting site that consumes the pure overload directly — the Photon Cannon's own
     * selector, via {@code building.PhotonCannonTargeting} — therefore applies
     * {@link Cloaking#isVisibleTo(Entity, Faction)} itself.
     */
    public static boolean isHostile(Entity self, Entity candidate) {
        return isHostile(get(self), get(candidate), WildKind.of(candidate))
                && Cloaking.isVisibleTo(candidate, get(self));
    }

    /** The pure rule behind {@link #isHostile(Entity, Entity)}, free of any live entity. */
    public static boolean isHostile(Faction self, Faction candidate, WildKind candidateKind) {
        return self.isEnemy(candidate)
                || (candidate == Faction.NEUTRAL && self.attacksWild(candidateKind));
    }
}
