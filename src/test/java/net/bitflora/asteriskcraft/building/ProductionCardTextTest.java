package net.bitflora.asteriskcraft.building;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the half of a train button's tooltip that lives outside Java. A button carries no visible
 * label, so its tooltip is the only place it says what it makes — and both the name and the
 * description are {@link Component#translatable} keys, which compile fine when nothing answers to
 * them and render as the raw key on the button in a play session.
 *
 * <p>Reads the language file straight off the classpath rather than through a live resource
 * manager, the same way {@link ProductionIconResourceTest} reads the icons beside it, so it needs
 * no server and no client.
 */
class ProductionCardTextTest {

    private static final String LANG = "assets/asteriskcraft/lang/en_us.json";

    private static JsonObject lang() {
        try (InputStream in = ProductionCardTextTest.class.getClassLoader().getResourceAsStream(LANG)) {
            assertNotNull(in, "missing " + LANG);
            return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
        } catch (Exception e) {
            throw new AssertionError("could not read " + LANG, e);
        }
    }

    private static String keyOf(Component component) {
        assertTrue(component.getContents() instanceof TranslatableContents,
                component + " is not a translatable component");
        return ((TranslatableContents) component.getContents()).getKey();
    }

    @Test
    void everyButtonNamesAndDescribesItselfInTheLanguageFile() {
        JsonObject lang = lang();
        for (ProductionKind kind : ProductionKind.values()) {
            for (ProductionKind.OptionView option : kind.options()) {
                for (Component text : new Component[] {option.name(), option.description()}) {
                    String key = keyOf(text);
                    assertTrue(lang.has(key), kind + " button has no en_us entry for " + key);
                    assertTrue(!lang.get(key).getAsString().isBlank(), key + " is blank");
                }
            }
        }
    }
}
