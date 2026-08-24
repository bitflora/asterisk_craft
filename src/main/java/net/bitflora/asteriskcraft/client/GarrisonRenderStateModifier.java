package net.bitflora.asteriskcraft.client;

import com.google.common.reflect.TypeToken;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.faction.Garrison;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

/**
 * Hides a unit that has climbed inside a {@link Garrison}. It is in there; the building is what you
 * see, and how many are inside is told by the barrels out of the Bunker's slits rather than by four
 * Marines clipping through its walls.
 *
 * <p>The sibling of {@link CloakRenderStateModifier}, on the same
 * {@code RegisterRenderStateModifiersEvent} hook and against the same {@code LivingEntityRenderer}
 * type token — which catches every subclass, hence the early-out on the first line. Registering
 * separately rather than folding this into the cloak modifier keeps two unrelated rules from sharing
 * a branch; they happen to want the same two booleans, and that is all they have in common.
 *
 * <p>Unlike cloak this is the <b>same for every viewer</b>, so it is the simple case of the three
 * that modifier documents: both flags set, which is vanilla's "not submitted at all". That pair is
 * also exactly what {@link UnitGlowLayer} early-outs on, so the emissive pass follows for free — a
 * garrisoned Dark Templar (were one ever Terran enough to board) would not leave its blade glowing
 * inside the wall.
 *
 * <p>The shadow and the name tag have to be taken back by hand rather than merely skipped: both are
 * extracted before any modifier runs, against an entity that was not invisible when vanilla looked at
 * it. A shadow pooling under a wall gives the garrison away more loudly than the missing body hides
 * it. The outline goes with them, since a unit that boards while selected would otherwise glow
 * through the building — {@code command.PlayerSelection} drops it from the selection on the same
 * tick, but the glow flag is server state and this is the frame that would show it.
 */
@EventBusSubscriber(modid = AsteriskCraft.MODID, value = Dist.CLIENT)
public final class GarrisonRenderStateModifier {

    private GarrisonRenderStateModifier() {
    }

    @SubscribeEvent
    public static void onRegisterRenderStateModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(
                new TypeToken<LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>>() {},
                GarrisonRenderStateModifier::applyShelter);
    }

    private static void applyShelter(LivingEntity entity, LivingEntityRenderState state) {
        if (!Garrison.isGarrisoned(entity)) {
            return;
        }
        state.isInvisible = true;
        state.isInvisibleToPlayer = true;
        state.shadowPieces.clear();
        state.shadowRadius = 0.0f;
        state.nameTag = null;
        state.outlineColor = 0;
    }
}
