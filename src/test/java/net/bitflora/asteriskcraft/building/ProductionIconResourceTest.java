package net.bitflora.asteriskcraft.building;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Guards the half of a command-card icon that lives outside Java. A
 * {@link ProductionKind.Icon.FromTexture} is only an {@link Identifier} — a typo'd or deleted PNG
 * compiles fine and shows up as a missing-texture button in a play session, nothing more.
 *
 * <p>The size assertion is the load-bearing one. {@code client.ProductionScreen} blits these 1:1
 * into a 16x16 quad and no longer passes a per-icon source resolution, so a re-export at any other
 * size doesn't fail anywhere — it just silently draws the icon's top-left corner, or a blurred
 * downscale of it, on every train button. {@code tools/gen_command_icons.py} is what produces them
 * at the right size; this is what notices when something else didn't.
 *
 * <p>Reads straight off the classpath rather than through a live resource manager, the same way
 * {@code combat.DamageTypeResourceTest} does, so it needs no server and no client.
 */
class ProductionIconResourceTest {

    /** {@code client.ProductionScreen.ICON_SIZE} — the quad these textures are blitted into. */
    private static final int ICON_SIZE = 16;

    private static BufferedImage read(Identifier location) {
        String path = "assets/" + location.getNamespace() + "/" + location.getPath();
        try (InputStream in = ProductionIconResourceTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "missing icon texture: " + path);
            BufferedImage image = ImageIO.read(in);
            assertNotNull(image, path + " is not a readable image");
            return image;
        } catch (IOException e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    @Test
    void everyCommandCardIconShipsAtTheSizeItIsDrawnAt() {
        for (ProductionKind kind : ProductionKind.values()) {
            for (ProductionKind.OptionView option : kind.options()) {
                if (!(option.icon() instanceof ProductionKind.Icon.FromTexture(Identifier location))) {
                    continue;
                }
                BufferedImage image = read(location);
                assertEquals(ICON_SIZE, image.getWidth(), location + " is not " + ICON_SIZE + " wide");
                assertEquals(ICON_SIZE, image.getHeight(), location + " is not " + ICON_SIZE + " tall");
            }
        }
    }
}
