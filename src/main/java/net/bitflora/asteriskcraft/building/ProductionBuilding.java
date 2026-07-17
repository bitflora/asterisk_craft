package net.bitflora.asteriskcraft.building;

import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;

/**
 * Bridge between a {@link ProductionMenu} and a production building's block entity.
 * Keeps the menu generic: it never touches unit types or costs directly, it just reads
 * synced {@link ContainerData} and dispatches a button press back to {@link #trainOption}.
 */
public interface ProductionBuilding extends MenuProvider {
    /** Client-safe layout/display descriptor (slot count, option buttons). */
    ProductionKind kind();

    /** The building's own input slots that hold the resources training consumes. */
    Container inputContainer();

    /** Live production state synced to the menu (build progress, warp, per-option queue counts). */
    ContainerData dataAccess();

    /** Server-side: attempt to queue the unit for {@code optionIndex}, paying from the input slots. */
    void trainOption(int optionIndex, Player player);
}
