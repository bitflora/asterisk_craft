package net.bitflora.asteriskcraft.client;

import com.google.common.reflect.TypeToken;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.command.ControlledFaction;
import net.bitflora.asteriskcraft.faction.Cloaked;
import net.bitflora.asteriskcraft.faction.Cloaking;
import net.bitflora.asteriskcraft.faction.DetectionAttachments;
import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

/**
 * Decides, per viewer and per frame, what a detected or {@link Cloaked} unit looks like.
 *
 * <p>It answers two questions off the one synced reveal mask, and they are separate on purpose. The
 * <b>outline</b> is what a detector buys its own army: every enemy inside a detector's envelope is
 * drawn under a red outline, cloaked or not, so a detector is an eye and not merely a
 * counter-measure. The <b>body</b> is the cloak rule, and it applies to nothing else — a unit that
 * is not cloaked is drawn exactly as vanilla drew it and only gains the outline.
 *
 * <p>This is the client half of cloaking; the server half is the targeting gate in
 * {@code FactionAttachments.isHostile}. Keeping them apart is the whole trick: the enemy AI is blind
 * to a cloaked unit no matter what is on screen, while the commanding player keeps a unit they can
 * see, click and order. Nothing is ever hidden by the <em>server</em> — the entity is tracked and
 * synced as normal, which is exactly why selection (a hitbox raycast) still works on a unit nobody
 * can see.
 *
 * <p>It rides {@code RegisterRenderStateModifiersEvent}, whose modifiers run from
 * {@code RenderStateExtensions.onUpdateEntityRenderState} <em>after</em>
 * {@code EntityRenderer.extractRenderState} — late enough to overwrite what vanilla just computed,
 * early enough that {@code LevelRenderer.extractVisibleEntities} still reads the result. That
 * ordering is load-bearing in both directions: it is why writing {@link #DETECTED_OUTLINE} into
 * {@code outlineColor} both colours the outline <em>and</em> switches the outline pass on
 * ({@code EntityRenderState.appearsGlowing()} is defined as {@code outlineColor != 0}, and nothing
 * server-side needs a glowing tag for it), and it is why the shadow and name tag have to be undone
 * by hand below rather than simply not happening.
 *
 * <p>Registering against {@code LivingEntityRenderer} catches every subclass, which is all of the
 * mod's bespoke renderers with one registration — and every vanilla one too, hence the early-out on
 * the first line: a cow is neither cloaked nor ever revealed, so it costs one {@code instanceof} and
 * one map probe per frame and nothing else.
 *
 * <p>The cloak cases, all expressed through the two booleans vanilla's
 * {@code LivingEntityRenderer.submit} already branches on:
 *
 * <table border="1">
 *   <caption>Cloak visibility</caption>
 *   <tr><th>Viewer</th><th>{@code isInvisible}</th><th>{@code isInvisibleToPlayer}</th><th>Result</th></tr>
 *   <tr><td>commands this unit</td><td>true</td><td>false</td><td>15%-alpha ghost body</td></tr>
 *   <tr><td>enemy, no detector</td><td>true</td><td>true</td><td>nothing at all</td></tr>
 *   <tr><td>enemy, detected</td><td>true</td><td>false</td><td>ghost body + red outline</td></tr>
 * </table>
 */
@EventBusSubscriber(modid = AsteriskCraft.MODID, value = Dist.CLIENT)
public final class DetectionRenderStateModifier {
    /**
     * The outline colour of an enemy a detector has revealed. Deliberately not routed through
     * {@code entity.TeamColors}: this does not say "Zerg", it says "detected", and it must read the
     * same whichever faction is on the receiving end of the reveal.
     */
    private static final int DETECTED_OUTLINE = 0xFFFF3030;

    private DetectionRenderStateModifier() {
    }

    @SubscribeEvent
    public static void onRegisterRenderStateModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(
                new TypeToken<LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>>() {},
                DetectionRenderStateModifier::applyDetection);
    }

    private static void applyDetection(LivingEntity entity, LivingEntityRenderState state) {
        boolean cloaked = Cloaked.isCloaked(entity);
        byte detectedBy = DetectionAttachments.detectedBy(entity);
        if (!cloaked && detectedBy == 0) {
            return;
        }
        Player viewer = Minecraft.getInstance().player;
        if (viewer == null) {
            return;
        }
        // What the viewer commands, not what they are — the same chokepoint the command scheme uses
        // to decide whose units a click may select, so "units I can see cloaked" and "units I can
        // order" can never drift apart.
        Faction commanded = ControlledFaction.of(viewer);
        Faction side = FactionAttachments.get(entity);
        boolean own = side == commanded;
        boolean detected = Cloaking.isDetectedBy(detectedBy, commanded);

        if (!cloaked) {
            // The whole of the outline half. The enemy test is a guard rather than a second rule:
            // the mask is only ever stamped across factions, so it re-states what the sweep already
            // decided and covers a reveal outliving the unit's side changing.
            if (detected && side.isEnemy(commanded)) {
                state.outlineColor = DETECTED_OUTLINE;
            }
            return;
        }

        boolean visible = own || detected;
        state.isInvisible = true;
        state.isInvisibleToPlayer = !visible;

        if (!visible) {
            // Both of these were already extracted, against an entity that wasn't invisible when
            // vanilla looked at it — so they have to be taken back rather than merely skipped. A
            // shadow pooling under nothing, or a name tag floating over it, gives the unit away far
            // more loudly than a missing body hides it.
            state.shadowPieces.clear();
            state.shadowRadius = 0.0f;
            state.nameTag = null;
        } else if (!own) {
            state.outlineColor = DETECTED_OUTLINE;
        }
        // An own-faction cloaked unit is deliberately left alone here: its outline stays whatever
        // the selection glow made it, so a selected cloaked unit still reads as selected.
    }
}
