package net.bitflora.asteriskcraft.client;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.building.CreepDependent;
import net.bitflora.asteriskcraft.building.CreepField;
import net.bitflora.asteriskcraft.faction.Faction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

import java.util.List;

/**
 * The Zerg sibling of {@link PsiFieldOverlay}: paints the ground within reach of any creep source,
 * or already covered in creep outright, while the player holds something the creep prerequisite
 * applies to. Mycelium-toned colours keep it from reading as the same field as psi's.
 *
 * <p>Shares its grid/rendering mechanics with {@link PsiFieldOverlay} through
 * {@link GroundGridOverlay}; only {@link CreepField}'s coverage rule and the colours differ. Both
 * clauses of that rule paint together — a column lights up whether it is in range of a source or is
 * mycelium outright, so the creep carpet a placed colony spreads reads as buildable ground the moment
 * it appears, regardless of which faction spread it — {@link CreepField} is deliberately faction-blind
 * on both clauses, unlike psi.
 *
 * <p>{@link CreepDependent#placingFaction} still resolves which <em>race's</em> ground counts as
 * creep for the on-creep clause (so a Protoss item, if one is ever gated, paints nothing), but source
 * ownership is never filtered on — an enemy colony egg's overlay lights up beside an ally's creep the
 * same as its own.
 */
@EventBusSubscriber(modid = AsteriskCraft.MODID, value = Dist.CLIENT)
public final class CreepFieldOverlay {
    private static final int DISPLAY_RADIUS = 16;

    private static final int FILL_COLOR = 0x33B266FF;
    private static final int EDGE_COLOR = 0xDDD9A6FF;
    private static final float EDGE_WIDTH = 2.5f;
    private static final float LIFT = 0.015f;
    private static final int REFRESH_TICKS = 10;

    private static final GroundGridOverlay GRID =
            new GroundGridOverlay(DISPLAY_RADIUS, FILL_COLOR, EDGE_COLOR, EDGE_WIDTH, LIFT, REFRESH_TICKS);

    private CreepFieldOverlay() {
    }

    @SubscribeEvent
    static void onSubmitGeometry(SubmitCustomGeometryEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            return;
        }
        CreepDependent placing = holdingCreepDependent(player);
        if (placing == null) {
            return;
        }

        BlockPos playerPos = player.blockPosition();
        Faction faction = placing.placingFaction(level, player);
        // Sources are looked up once for the whole grid, exactly like PsiField#onlinePylons — and for
        // any owner, since CreepField is deliberately faction-blind about whose creep counts.
        List<BlockPos> sources = CreepField.creepSources(level, playerPos, DISPLAY_RADIUS + CreepField.RADIUS, null);
        GRID.refresh(level, playerPos, pos -> CreepField.covered(level, pos, faction, sources));
        GRID.submit(event);
    }

    /** Whichever hand holds an item whose building needs creep in range, or null. */
    private static CreepDependent holdingCreepDependent(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            Item item = player.getItemInHand(hand).getItem();
            if (item instanceof CreepDependent placing && placing.requiresCreep()) {
                return placing;
            }
        }
        return null;
    }
}
