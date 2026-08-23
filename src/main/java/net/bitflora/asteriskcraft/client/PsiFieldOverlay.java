package net.bitflora.asteriskcraft.client;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.building.PsiDependent;
import net.bitflora.asteriskcraft.building.PsiField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

import java.util.List;

/**
 * Paints the ground a Pylon powers, while the player holds something that needs one.
 *
 * <p>{@link KitPlacementPreview} answers "may I build <em>here</em>" for the one square being
 * pointed at; this answers "where may I build at all", which is the question a player actually has
 * while walking around looking for a spot. Without it, finding the edge of a Pylon's reach means
 * sweeping the crosshair over the terrain and watching for the outline to change colour.
 *
 * <p>It asks {@link PsiField} the same question the server will, once per ground column, so the
 * paint and the refusal cannot disagree: a column is powered exactly when a right-click on that
 * surface would be allowed. Ownership is the one thing it cannot check; see
 * {@link PsiField#onlinePylons}. The grid/rendering mechanics are shared with the Zerg equivalent of
 * this overlay through {@link GroundGridOverlay}; only the coverage rule and the colours differ.
 *
 * <p>Only appears while the player holds something that is actually gated
 * ({@link PsiDependent#requiresPylon()}), so a Nexus or Pylon kit paints nothing. It keys off that
 * interface rather than off a kit because the Photon Cannon is placed by a spawn egg, and the egg is
 * the case that needs this most: a kit at least gets a coloured volume outline from
 * {@link KitPlacementPreview}, while an egg draws nothing at all.
 */
@EventBusSubscriber(modid = AsteriskCraft.MODID, value = Dist.CLIENT)
public final class PsiFieldOverlay {
    /** How far around the player the ground is painted. Beyond this the field is simply not drawn. */
    private static final int DISPLAY_RADIUS = 16;

    private static final int FILL_COLOR = 0x3355FF99;
    private static final int EDGE_COLOR = 0xDD8CFFC4;
    private static final float EDGE_WIDTH = 2.5f;
    private static final float LIFT = 0.015f;
    private static final int REFRESH_TICKS = 10;

    private static final GroundGridOverlay GRID =
            new GroundGridOverlay(DISPLAY_RADIUS, FILL_COLOR, EDGE_COLOR, EDGE_WIDTH, LIFT, REFRESH_TICKS);

    private PsiFieldOverlay() {
    }

    @SubscribeEvent
    static void onSubmitGeometry(SubmitCustomGeometryEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            return;
        }
        if (!holdingPsiDependent(player)) {
            return;
        }

        BlockPos playerPos = player.blockPosition();
        // Pylons are looked up once for the whole grid — the reason PsiField#onlinePylons exists as
        // its own step rather than being folded into the per-position test.
        List<BlockPos> pylons = PsiField.onlinePylons(level, playerPos, DISPLAY_RADIUS + PsiField.RADIUS, null);
        GRID.refresh(level, playerPos, pos -> PsiField.covered(pos, pylons));
        GRID.submit(event);
    }

    /** Whether either hand holds an item whose building needs a Pylon in range. */
    private static boolean holdingPsiDependent(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).getItem() instanceof PsiDependent placing && placing.requiresPylon()) {
                return true;
            }
        }
        return false;
    }
}
