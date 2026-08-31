package net.bitflora.asteriskcraft.combat;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

import java.util.List;

/**
 * One damage type per attacking unit, so a kill reads as the weapon that made it ("slashed by psi
 * blades") instead of the single {@code minecraft:magic} every attack used to share.
 *
 * <p>{@link Registries#DAMAGE_TYPE} is a <em>datapack</em> registry, not a {@code DeferredRegister}
 * one, so there is no registration call anywhere: the definitions are the JSON files under
 * {@code data/asteriskcraft/damage_type/}, loaded by {@code RegistryDataLoader} straight from the
 * mod's resources and synced to clients automatically. All this class holds is the keys to look them
 * up by. That also keeps it registry-free at class-init, so it loads in the JUnit bootstrap.
 *
 * <p>Each type's {@code message_id} is the stem of its death message: {@code death.attack.<id>} and
 * {@code death.attack.<id>.player} in {@code assets/asteriskcraft/lang/en_us.json}. A missing lang
 * entry shows as a raw key in chat rather than failing anything, which is exactly the kind of silent
 * gap {@code DamageTypeResourceTest} exists to catch.
 *
 * <p><b>None of them are in {@code #minecraft:bypasses_armor}</b>, which {@code minecraft:magic} is in
 * — so unlike before, armour reduces every one of these hits. They <em>are</em> all in
 * {@code #minecraft:panic_causes}, which magic also was; dropping out of it would have stopped animals
 * fleeing from combat.
 *
 * <p>{@code #minecraft:no_knockback} is the one tag they split on, and the line is <b>whether the
 * attack needs its target to stay put</b>. The shove is worth keeping when a unit closes to arm's
 * length and connects once, so the four melee types and the two fanged ones stay out of the tag.
 * Every type dealt by {@link net.bitflora.asteriskcraft.entity.ai.HitscanAttacks} is in it: a shooter
 * that pushed its target back a little on each shot was kiting it by accident, which turns a firing
 * line holding ground into a bumper and stops an assault ever arriving.
 *
 * <p>{@code flame_thrower} is in it too, and is why that line is not simply "melee versus ranged".
 * It is dealt at two blocks — melee distance by any reading — but it is dealt into a
 * {@link net.bitflora.asteriskcraft.combat.FlameCone}, a fixed volume the victims have to still be
 * standing in when the next sweep lands. Shoving them would push them out of the attacker's own
 * cone, which is the same bumper problem arriving from the other end. Membership is declared in
 * {@code data/minecraft/tags/damage_type/no_knockback.json} (additively — vanilla's own members are
 * kept), and the shove itself comes from {@code hurtServer} gated on that tag, so nothing in the
 * attack code decides it. See docs/neoforge-api-notes.md.
 */
public final class AsteriskCraftDamageTypes {

    // Protoss
    public static final ResourceKey<DamageType> PSI_BLADES = key("psi_blades");
    public static final ResourceKey<DamageType> PHASE_DISRUPTOR = key("phase_disruptor");
    public static final ResourceKey<DamageType> ANTI_MATTER_MISSILE = key("anti_matter_missile");
    public static final ResourceKey<DamageType> PHOTON_BLAST = key("photon_blast");
    public static final ResourceKey<DamageType> WARP_BLADE = key("warp_blade");

    // Zerg
    public static final ResourceKey<DamageType> ZERGLING_CLAWS = key("zergling_claws");
    public static final ResourceKey<DamageType> NEEDLE_SPINES = key("needle_spines");
    public static final ResourceKey<DamageType> GLAVE_WURM = key("glave_wurm");
    public static final ResourceKey<DamageType> KAISER_BLADES = key("kaiser_blades");
    /** The Sunken Colony's tentacle. Its canonical StarCraft name, not the Lurker's below. */
    public static final ResourceKey<DamageType> SUBTERRANEAN_TENTACLE = key("subterranean_tentacle");
    public static final ResourceKey<DamageType> SUBTERRANEAN_SPINES = key("subterranean_spines");
    public static final ResourceKey<DamageType> SEEKER_SPORES = key("seeker_spores");
    /** The Infested Villager's detonation. Its blast, not a swing — the unit only ever deals it once. */
    public static final ResourceKey<DamageType> INFESTED_BLAST = key("infested_blast");

    // Terran
    /** The SCV's welding torch. The only damage type in the mod dealt by a worker. */
    public static final ResourceKey<DamageType> FUSION_CUTTER = key("fusion_cutter");
    /** The Marine's rifle. */
    public static final ResourceKey<DamageType> GAUSS_RIFLE = key("gauss_rifle");
    /** The Firebat's flamethrower. The mod's only attack that hurts several targets at once
     * without being a bounce chain or a detonation. */
    public static final ResourceKey<DamageType> FLAME_THROWER = key("flame_thrower");
    /** The Ghost's C-10 canister rifle. The longest reach the Terran have on foot. */
    public static final ResourceKey<DamageType> C10_CANISTER_RIFLE = key("c10_canister_rifle");
    /** The Missile Turret's ordnance. The only Terran damage type that can only ever be dealt to
     * something off the ground. */
    public static final ResourceKey<DamageType> LONGBOLT_MISSILE = key("longbolt_missile");
    /** The Wraith's burst lasers. The only Terran damage type dealt <em>from</em> the air, and the
     * second in the mod (after the Scout's) whose amount depends on whether its target is a flyer. */
    public static final ResourceKey<DamageType> BURST_LASERS = key("burst_lasers");

    /** The Goliath's twin autocannons. Named for its ground gun though it fires the same burst at
     * both layers, the way the Wraith's {@link #BURST_LASERS} is: one attacking unit, one type. */
    public static final ResourceKey<DamageType> TWIN_AUTOCANNONS = key("twin_autocannons");

    /** Every key above — what {@code DamageTypeResourceTest} iterates to check each one's data files. */
    public static final List<ResourceKey<DamageType>> ALL = List.of(
            PSI_BLADES, PHASE_DISRUPTOR, ANTI_MATTER_MISSILE, PHOTON_BLAST, WARP_BLADE,
            ZERGLING_CLAWS, NEEDLE_SPINES, GLAVE_WURM, KAISER_BLADES, SUBTERRANEAN_TENTACLE,
            SUBTERRANEAN_SPINES, SEEKER_SPORES, INFESTED_BLAST, FUSION_CUTTER, GAUSS_RIFLE, FLAME_THROWER,
            C10_CANISTER_RIFLE, LONGBOLT_MISSILE, BURST_LASERS, TWIN_AUTOCANNONS);

    private AsteriskCraftDamageTypes() {
    }

    private static ResourceKey<DamageType> key(String path) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, AsteriskCraft.id(path));
    }
}
