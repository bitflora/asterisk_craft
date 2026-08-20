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
 * <p><b>Deliberately not in {@code #minecraft:bypasses_armor} or {@code #minecraft:no_knockback}</b>,
 * both of which {@code minecraft:magic} is in — so unlike before, armour reduces these hits and they
 * knock their target back. They <em>are</em> in {@code #minecraft:panic_causes}, which magic also was;
 * dropping out of it would have stopped animals fleeing from combat.
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
    public static final ResourceKey<DamageType> SUBTERRANEAN_SPINES = key("subterranean_spines");
    public static final ResourceKey<DamageType> SEEKER_SPORES = key("seeker_spores");

    /** Every key above — what {@code DamageTypeResourceTest} iterates to check each one's data files. */
    public static final List<ResourceKey<DamageType>> ALL = List.of(
            PSI_BLADES, PHASE_DISRUPTOR, ANTI_MATTER_MISSILE, PHOTON_BLAST, WARP_BLADE,
            ZERGLING_CLAWS, NEEDLE_SPINES, GLAVE_WURM, KAISER_BLADES, SUBTERRANEAN_SPINES, SEEKER_SPORES);

    private AsteriskCraftDamageTypes() {
    }

    private static ResourceKey<DamageType> key(String path) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, AsteriskCraft.id(path));
    }
}
