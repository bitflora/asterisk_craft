package net.bitflora.asteriskcraft.faction;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Data attachments tagging any entity with the army it fights for: its {@link Faction} — which
 * <em>side</em> — and its {@link Race} — what that army <em>is</em>. All targeting logic must
 * resolve hostility through {@link #isHostile}, never through entity class checks.
 *
 * <p>The two are separate attachments, and both are needed, because a side no longer names a race:
 * two sides may play the same one (a mirror match), so nothing can be recovered from the other.
 */
public final class FactionAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, AsteriskCraft.MODID);

    public static final Supplier<AttachmentType<Faction>> FACTION = ATTACHMENT_TYPES.register(
            "faction", () -> AttachmentType.builder(() -> Faction.NEUTRAL)
                    .serialize(Faction.CODEC.fieldOf("faction"))
                    .sync(Faction.STREAM_CODEC)
                    .build());

    /**
     * Which race the army this entity fights for <em>is</em> — the other half of the pair
     * {@link #FACTION} starts, and empty for everything the mod doesn't own.
     *
     * <p>It rides on the entity rather than being looked up from its side because a side no longer
     * names a race: in a mirror match both sides play the same one, and in any match the assignment
     * is per world ({@code game.MatchSetup}). Level attachments are not synced, so deriving it from
     * the match would leave the client guessing — where this is synced, which
     * {@code combat.ShieldAttachments.maxShieldFor} needs (the Jade tooltip asks for a max shield
     * client-side) and the creep overlay wants.
     *
     * <p>Empty rather than a default race: {@link Faction#NEUTRAL} is nobody's army, and a wild cow
     * that answered "Protoss" would carry shields.
     */
    public static final Supplier<AttachmentType<Optional<Race>>> RACE = ATTACHMENT_TYPES.register(
            "race", () -> AttachmentType.<Optional<Race>>builder(Optional::empty)
                    .serialize(Race.CODEC.optionalFieldOf("race"))
                    .sync(ByteBufCodecs.optional(Race.STREAM_CODEC))
                    .build());

    /**
     * Which side a <em>human</em> is commanding, held on the overworld. This is the leaf-package
     * projection of {@code game.MatchSetup.playerFaction()}: {@code faction} may not import
     * {@code game}, and {@code MatchSetup.of} answers its default on the client anyway, but
     * {@link Race#attacksWild} needs the answer because what an army hunts in the wild depends on
     * whether a person is standing in it. {@code game.GameBootstrap} is the single writer, on
     * every login, right where it writes the player's own {@link #FACTION}.
     *
     * <p>Not synced: every caller below runs server-side (targeting, retaliation, damage handlers,
     * the command resolver). The client's cloak rendering goes through {@link Cloaking} directly
     * and never asks this.
     */
    public static final Supplier<AttachmentType<Faction>> COMMANDED = ATTACHMENT_TYPES.register(
            "commanded_faction", () -> AttachmentType.builder(() -> Faction.NEUTRAL)
                    .serialize(Faction.CODEC.fieldOf("commanded"))
                    .build());

    private FactionAttachments() {
    }

    public static Faction get(Entity entity) {
        return entity.getData(FACTION);
    }

    /**
     * Tags {@code entity} with the army it fights for: a side and the race that side is playing.
     * The two are set together because every rule that asks for one eventually asks for the other —
     * hostility widens by race, shields and regen are race traits — and an entity carrying a side
     * with no race is an army that is nothing.
     */
    public static void set(Entity entity, Faction faction, @Nullable Race race) {
        entity.setData(FACTION, faction);
        entity.setData(RACE, Optional.ofNullable(race));
    }

    /**
     * What this entity's army is, or null for the unfactioned world. Null rather than an Optional
     * because every caller is a per-tick combat check or a null-safe trait lookup, exactly as
     * {@code Faction.race()} was before a side stopped naming a race.
     */
    public static @Nullable Race raceOf(Entity entity) {
        return entity.getData(RACE).orElse(null);
    }

    /** Records which side the human is playing; see {@link #COMMANDED}. */
    public static void setCommanded(Level level, Faction faction) {
        level.setData(COMMANDED, faction);
    }

    /**
     * The side a human is commanding, read off the overworld the way {@code game.MatchSetup} does,
     * so a unit standing in any dimension gets the same answer. NEUTRAL on the client, and on a
     * world nobody has joined yet — in both cases nothing is commanded, which is the right answer.
     */
    public static Faction commandedFaction(Level level) {
        MinecraftServer server = level.getServer();
        return server == null ? Faction.NEUTRAL : server.overworld().getData(COMMANDED);
    }

    /** Whether {@code faction} is the side a human is commanding in {@code level}. */
    public static boolean isCommanded(Level level, Faction faction) {
        return faction != Faction.NEUTRAL && commandedFaction(level) == faction;
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
     * ({@link Race#attacksWild}), which is why the rule can differ per race without a single
     * race check appearing in targeting code. It stays gated twice:
     * <ul>
     *   <li>on {@code candidate} actually being NEUTRAL, since the mod's own combat units (Zealot,
     *       Zergling, Dragoon, Hydralisk...) implement {@code Enemy} themselves (via {@code
     *       Monster}) and must never be caught by a bare class check;</li>
     *   <li>on {@code self} having a race at all — the unfactioned world plays none, so an
     *       unfactioned unit still fights nobody.</li>
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
     * <p>It is where <b>garrisoning</b> is enforced for the same reason ({@link Garrison}): a unit
     * riding inside a Bunker is not hostile to anything, because everything aimed at it has to go
     * through the shell instead. Cloak and shelter are the same shape of rule and share the same
     * choke point, which is why neither needed a line in any goal.
     *
     * <p><b>Both are only half a rule.</b> A {@code TargetingConditions.Selector} is consulted when a
     * target is acquired and never again, so a unit that cloaks — or boards — while something
     * already holds it stays held. {@code combat.TargetRetentionHandler} is the other half for both.
     *
     * <p>The pure overload below is deliberately left cloak- and shelter-free: both are facts about a
     * live entity, not about a pair of factions, and keeping them out preserves that rule's
     * testability. The one targeting site that consumes the pure overload directly — the Photon
     * Cannon's own selector, via {@code building.PhotonCannonTargeting} — therefore applies
     * {@link #isEngageable(Entity, Faction)} itself.
     */
    public static boolean isHostile(Entity self, Entity candidate) {
        Faction selfFaction = get(self);
        return isHostile(selfFaction, raceOf(self), get(candidate), WildKind.of(candidate),
                isCommanded(self.level(), selfFaction))
                && isEngageable(candidate, selfFaction);
    }

    /**
     * Whether {@code candidate} can be fought at all by {@code viewer}'s side, setting aside whose
     * side it is on: it must not be cloaked-and-undetected, and it must not be sheltered inside a
     * {@link Garrison}. The two live-entity gates {@link #isHostile(Entity, Entity)} applies on top
     * of the pure faction rule, factored out so the one site that bypasses that overload can apply
     * exactly the same pair rather than a drifting copy of it.
     */
    public static boolean isEngageable(Entity candidate, Faction viewer) {
        return Cloaking.isVisibleTo(candidate, viewer) && !Garrison.isGarrisoned(candidate);
    }

    /**
     * The pure rule behind {@link #isHostile(Entity, Entity)}, free of any live entity.
     *
     * @param selfRace      the race {@code self}'s army is, from {@link #RACE}; null for the
     *                      unfactioned world, which hunts nothing
     * @param selfCommanded whether {@code self} is the side a human is playing — see
     *                      {@link #COMMANDED}. It only ever widens the wild carve-out; strict
     *                      cross-faction hostility does not depend on who is holding the reins.
     */
    public static boolean isHostile(Faction self, @Nullable Race selfRace, Faction candidate,
                                    WildKind candidateKind, boolean selfCommanded) {
        return self.isEnemy(candidate)
                || (candidate == Faction.NEUTRAL && selfRace != null
                        && selfRace.attacksWild(candidateKind, selfCommanded));
    }
}
