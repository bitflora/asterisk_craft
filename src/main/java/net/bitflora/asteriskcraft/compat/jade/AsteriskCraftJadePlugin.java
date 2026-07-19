package net.bitflora.asteriskcraft.compat.jade;

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
 * <p>All shield data ({@link net.bitflora.asteriskcraft.combat.ShieldAttachments}) is already
 * synced to clients, so this needs no {@code IWailaCommonRegistration} server data provider;
 * {@link ProtossShieldProvider} reads it directly client-side.
 */
@WailaPlugin
public final class AsteriskCraftJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        // No server-side data to send: shield current/max are already client-synced attachments.
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        // Registered on LivingEntity (matches Jade's own vanilla pattern, e.g. its
        // StatusEffectsProvider) since Zealot/Dragoon/PhotonCannon/Probe don't share a common
        // supertype narrower than LivingEntity; ProtossShieldProvider guards non-shielded entities.
        registration.registerEntityComponent(ProtossShieldProvider.INSTANCE, LivingEntity.class);
    }
}
