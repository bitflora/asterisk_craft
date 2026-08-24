package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.command.ControlledFaction;
import net.bitflora.asteriskcraft.command.ControlledRace;
import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.Race;
import net.bitflora.asteriskcraft.game.GameBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * A generic warp-in kit: right-click the ground to stamp a building's structure template
 * immediately, leaving the core block entity to run its own warp-in countdown behind a
 * {@link WarpScaffold} of glass that fills in as it runs.
 * Reusable by later kits (Photon Cannon, etc.) that share this place-then-warm-up shape.
 *
 * <p>Whether a kit needs a Pylon in range is a flag here rather than a case inside
 * {@link PsiField}: the rule is "this building needs power", and a building that is exempt
 * (a Nexus, a Pylon itself) simply never asks. Nothing in {@code PsiField} names a building.
 */
public class BuildingKitItem extends Item implements PsiDependent, CreepDependent {
    private final Identifier template;
    // A supplier, not the Block itself: kits are registered alongside the blocks they place, so
    // the block isn't resolvable yet at construction time.
    private final Supplier<? extends Block> coreBlock;
    private final BuildingTemplates.Footprint footprint;
    private final boolean requiresPylon;
    private final boolean requiresCreep;
    private final boolean spreadsCreep;

    public BuildingKitItem(Properties properties, Identifier template, Supplier<? extends Block> coreBlock,
            BuildingTemplates.Footprint footprint, boolean requiresPylon) {
        this(properties, template, coreBlock, footprint, requiresPylon, false, false);
    }

    /**
     * @param spreadsCreep whether this kit's building should spread its race's ground cover (Zerg
     *                     creep) out from itself once placed — true only for the Hive kit. Resolved
     *                     against the placer's race at use time rather than hardcoded here, so this
     *                     class still names no race: a race with no creep (Protoss) is a no-op.
     */
    public BuildingKitItem(Properties properties, Identifier template, Supplier<? extends Block> coreBlock,
            BuildingTemplates.Footprint footprint, boolean requiresPylon, boolean spreadsCreep) {
        this(properties, template, coreBlock, footprint, requiresPylon, false, spreadsCreep);
    }

    /**
     * @param requiresCreep whether this kit's building may only be placed on or within
     *                      {@link CreepField#RADIUS} of Zerg creep — the sibling of
     *                      {@code requiresPylon}, and never true at the same time as it. No kit sets
     *                      it today (the Hive is exempt, like a Nexus is from psi), but it's wired
     *                      the same way {@code spreadsCreep} was ahead of the eggs that needed it.
     */
    public BuildingKitItem(Properties properties, Identifier template, Supplier<? extends Block> coreBlock,
            BuildingTemplates.Footprint footprint, boolean requiresPylon, boolean requiresCreep, boolean spreadsCreep) {
        super(properties);
        this.template = template;
        this.coreBlock = coreBlock;
        this.footprint = footprint;
        this.requiresPylon = requiresPylon;
        this.requiresCreep = requiresCreep;
        this.spreadsCreep = spreadsCreep;
    }

    /**
     * The volume this kit needs free, for the client-side placement outline
     * ({@code client/KitPlacementPreview}). Placement itself measures the loaded template instead —
     * see {@link BuildingTemplates.Footprint}.
     */
    public BuildingTemplates.Footprint footprint() {
        return this.footprint;
    }

    /** Whether this kit needs a Pylon in range, for the same outline to colour the reason in. */
    @Override
    public boolean requiresPylon() {
        return this.requiresPylon;
    }

    /** Whether this kit needs creep in range, for {@code client/CreepFieldOverlay}. */
    @Override
    public boolean requiresCreep() {
        return this.requiresCreep;
    }

    @Override
    public @Nullable Race placingRace(Level level, Player player) {
        return ControlledRace.of(player);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.FAIL;
        }

        BlockPos origin = context.getClickedPos().relative(context.getClickedFace());
        if (!BuildingTemplates.isSiteClear(serverLevel, origin, this.template, this.coreBlock.get())) {
            overlay(player, Component.translatable("message.asteriskcraft.kit.blocked"));
            return InteractionResult.FAIL;
        }
        if (this.requiresPylon && !PsiField.covered(serverLevel, origin, ControlledFaction.of(player))) {
            overlay(player, Component.translatable("message.asteriskcraft.kit.no_pylon", PsiField.RADIUS));
            return InteractionResult.FAIL;
        }
        if (this.requiresCreep && !CreepField.covered(serverLevel, origin, ControlledRace.of(player))) {
            overlay(player, Component.translatable("message.asteriskcraft.kit.no_creep", CreepField.RADIUS));
            return InteractionResult.FAIL;
        }

        // No support fill: the template's own stonework is the base, so a gap under a sloping
        // edge is left as a gap rather than plugged with a foreign block.
        BuildingTemplates.Placed placed = BuildingTemplates.place(serverLevel, origin, this.template,
                this.coreBlock.get(), null);
        if (placed == null) {
            // The template failed to load, so nothing was stamped — don't eat the kit for it.
            overlay(player, Component.translatable("message.asteriskcraft.kit.blocked"));
            return InteractionResult.FAIL;
        }
        BlockEntity core = serverLevel.getBlockEntity(placed.core());
        // The building belongs to whoever warped it in, asked through the one chokepoint for
        // command ownership — so a kit never assumes the player is Protoss.
        Faction faction = ControlledFaction.of(player);
        if (core instanceof WarpInBuilding building) {
            building.setFaction(faction);
        }
        if (this.spreadsCreep) {
            GameBootstrap.spreadCreep(serverLevel, placed.core(), ControlledRace.of(player));
        }
        // Start the warp explicitly rather than trusting the stamped core to arrive mid-warp: the
        // template carries the block-entity NBT captured from a finished building, spent countdown
        // and all. This is also what freezes the layout back into its glass scaffold.
        if (core instanceof SiegeTarget target) {
            target.defense().beginWarpIn(serverLevel, placed);
        }
        context.getItemInHand().shrink(1);
        overlay(player, Component.translatable("message.asteriskcraft.kit.warping"));
        return InteractionResult.SUCCESS;
    }

    private static void overlay(Player player, Component message) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(message, true);
        }
    }
}
