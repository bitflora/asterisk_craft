package net.bitflora.asteriskcraft.combat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the halves of a damage type that live apart from each other and fail silently when they
 * drift. {@link net.minecraft.core.registries.Registries#DAMAGE_TYPE} is a datapack registry, so a
 * key in {@link AsteriskCraftDamageTypes} is only a name — nothing in Java fails to compile if its
 * JSON is missing, and nothing fails at runtime either until a unit actually swings and the game
 * cannot resolve the type. Likewise a missing {@code death.attack.*} entry just prints its raw key
 * into chat. Both are exactly the kind of gap a test should catch instead of a play session.
 *
 * <p>Reads the files straight off the classpath rather than through a live resource manager, so it
 * needs no server. That splits the <b>tag</b> memberships these types rely on
 * ({@code #minecraft:panic_causes}, {@code #minecraft:no_knockback}) in half: the tag files' contents
 * are just JSON on the classpath and are checked here, but whether a type is actually <em>bound</em>
 * into a tag at runtime is out of scope — tags aren't bound in the JUnit bootstrap at all (see
 * {@code entity.protoss.ProbeEconomyTest} for the same limitation), so that side is verified in
 * {@code runClient} instead.
 */
class DamageTypeResourceTest {

    private static JsonObject readJson(String path) {
        try (InputStream in = DamageTypeResourceTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "missing resource: " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    @Test
    void everyKeyHasADefinitionWhoseMessageIdMatchesIt() {
        for (ResourceKey<DamageType> key : AsteriskCraftDamageTypes.ALL) {
            String path = key.identifier().getPath();
            assertEquals("asteriskcraft", key.identifier().getNamespace(), path + " is not in the mod's namespace");

            JsonObject json = readJson("data/asteriskcraft/damage_type/" + path + ".json");
            // The id and the message_id are kept equal on purpose: the message_id is what names the
            // lang keys, so letting them diverge would mean two names per damage type to keep in step.
            assertEquals(path, json.get("message_id").getAsString(),
                    path + ".json declares a message_id that doesn't match its file name");
            assertTrue(json.has("scaling"), path + ".json is missing the required scaling field");
            assertTrue(json.has("exhaustion"), path + ".json is missing the required exhaustion field");
        }
    }

    @Test
    void everyDefinitionHasBothDeathMessages() {
        JsonObject lang = readJson("assets/asteriskcraft/lang/en_us.json");
        for (ResourceKey<DamageType> key : AsteriskCraftDamageTypes.ALL) {
            String messageId = key.identifier().getPath();
            // Vanilla picks the .player variant when the killer was a player-attributed source, so a
            // mod that only wrote the base key would leak a raw key the first time a player got a kill.
            assertTrue(lang.has("death.attack." + messageId),
                    "en_us.json is missing death.attack." + messageId);
            assertTrue(lang.has("death.attack." + messageId + ".player"),
                    "en_us.json is missing death.attack." + messageId + ".player");
        }
    }

    /**
     * A vanilla tag file the mod adds to fails silently in both directions it can be wrong: a typo'd
     * value is simply a membership that never happens, and a missing {@code "replace": false} wipes
     * vanilla's own entries instead of joining them. Neither shows up as an error anywhere. Which
     * types belong in a tag is a balance question and deliberately not asserted here.
     */
    /**
     * As {@link #readJson}, but for a file in the {@code minecraft} namespace, where the mod is not the
     * only thing on the classpath holding that exact path — vanilla ships its own copy of every tag the
     * mod adds to, and a plain {@code getResourceAsStream} is free to hand back either. The mod's
     * resources are a plain directory on the test classpath while vanilla's arrive inside the MC jar,
     * so the {@code file:} candidate is the one written in this repo.
     */
    private static JsonObject readOverlayJson(String path) {
        try {
            Enumeration<URL> candidates = DamageTypeResourceTest.class.getClassLoader().getResources(path);
            while (candidates.hasMoreElements()) {
                URL url = candidates.nextElement();
                if (!"file".equals(url.getProtocol())) {
                    continue;
                }
                try (InputStream in = url.openStream()) {
                    return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                }
            }
        } catch (IOException e) {
            throw new AssertionError("could not read " + path, e);
        }
        throw new AssertionError("the mod ships no " + path);
    }

    @Test
    void damageTypeTagsListOnlyRealTypesAndDoNotReplaceVanilla() {
        Set<String> declared = new HashSet<>();
        for (ResourceKey<DamageType> key : AsteriskCraftDamageTypes.ALL) {
            declared.add(key.identifier().toString());
        }

        for (String tag : List.of("panic_causes", "no_knockback")) {
            String path = "data/minecraft/tags/damage_type/" + tag + ".json";
            JsonObject json = readOverlayJson(path);
            assertTrue(json.has("replace") && !json.get("replace").getAsBoolean(),
                    path + " must declare \"replace\": false or it wipes vanilla's members of the tag");
            for (JsonElement value : json.getAsJsonArray("values")) {
                assertTrue(declared.contains(value.getAsString()),
                        path + " lists " + value.getAsString() + ", which no key in AsteriskCraftDamageTypes declares");
            }
        }
    }

    @Test
    void keysAreDistinct() {
        Set<ResourceKey<DamageType>> seen = new HashSet<>();
        for (ResourceKey<DamageType> key : AsteriskCraftDamageTypes.ALL) {
            assertTrue(seen.add(key), "duplicate damage type key: " + key.identifier());
        }
        assertEquals(AsteriskCraftDamageTypes.ALL.size(), seen.size(), "duplicate keys in ALL");
        assertEquals(19, seen.size(), "one damage type per attacking unit is expected");
    }
}
