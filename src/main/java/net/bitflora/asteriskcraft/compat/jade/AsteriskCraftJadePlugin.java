package net.bitflora.asteriskcraft.compat.jade;

import net.bitflora.asteriskcraft.building.GatewayBlock;
import net.bitflora.asteriskcraft.building.GatewayBlockEntity;
import net.bitflora.asteriskcraft.building.BaseBlock;
import net.bitflora.asteriskcraft.building.BaseBlockEntity;
import net.minecraft.world.entity.LivingEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade addon entrypoint: Jade discovers this via NeoForge's mod-file annotation scan (no
 * mods.toml entrypoint or {@code AsteriskCraftClient} registration needed), only when Jade is
 * actually installed alongside this mod (see the optional {@code jade} dependency in
 * neoforge.mods.toml) — this class only loads/executes in that case.
 *
 * <p>Unit shield data ({@link net.bitflora.asteriskcraft.combat.ShieldAttachments}) is already synced
 * to clients, so {@link ProtossShieldProvider} reads it directly client-side. A building's HP and
 * shields are not: that state lives in the block entity, so {@link BuildingDefenseProvider} ships it
 * on demand as server data and its {@code Client} half draws the lines.
 *
 * <p>Both building registrations are per-type on purpose rather than blanket ones on
 * {@code BaseEntityBlock}/{@code BlockEntity}: that would attach this mod's providers to every chest
 * and furnace in the game. <b>A new building type has to be added to both lists here</b> — Jade's
 * class lookup walks superclasses only, so registering the {@code SiegeTarget} interface would never
 * match anything.
 */
@WailaPlugin
public final class AsteriskCraftJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        // Buildings only: HP/shield/warp-in state has to come from the server (see the class doc).
        registration.registerBlockDataProvider(BuildingDefenseProvider.INSTANCE, BaseBlockEntity.class);
        registration.registerBlockDataProvider(BuildingDefenseProvider.INSTANCE, GatewayBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        // Registered on LivingEntity (matches Jade's own vanilla pattern, e.g. its
        // StatusEffectsProvider) since Zealot/Dragoon/PhotonCannon/Probe don't share a common
        // supertype narrower than LivingEntity; ProtossShieldProvider guards non-shielded entities.
        registration.registerEntityComponent(ProtossShieldProvider.INSTANCE, LivingEntity.class);

        registration.registerBlockComponent(BuildingDefenseProvider.Client.INSTANCE, BaseBlock.class);
        registration.registerBlockComponent(BuildingDefenseProvider.Client.INSTANCE, GatewayBlock.class);
    }
}
