package net.bitflora.asteriskcraft.building;

import com.mojang.serialization.Codec;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.Race;
import net.bitflora.asteriskcraft.race.RaceProfile;
import net.bitflora.asteriskcraft.race.Races;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The per-army shared resource pool that every production building belonging to one faction draws
 * from: each of a race's bases and unit factories acts like a linked chest onto its race's bank.
 * Stored as a level attachment (not on any one building) so the pool survives independently of
 * which specific building placed or removed it — see {@link ArmyLinkedContainer}.
 *
 * <p>One bank per {@link Race}, registered by walking {@link Races} rather than declared by hand,
 * so adding a race brings its bank with it. Its size is the race's own
 * {@link RaceProfile#bankSlots()} — the swarm's is far larger, since it pays for everything out of
 * one pool with no per-item cost restrictions.
 */
public final class ArmyBank {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, AsteriskCraft.MODID);

    private static final Map<Race, Supplier<AttachmentType<NonNullList<ItemStack>>>> BANKS = registerBanks();

    private ArmyBank() {
    }

    private static Map<Race, Supplier<AttachmentType<NonNullList<ItemStack>>>> registerBanks() {
        Map<Race, Supplier<AttachmentType<NonNullList<ItemStack>>>> banks = new EnumMap<>(Race.class);
        for (RaceProfile profile : Races.all()) {
            int slots = profile.bankSlots();
            banks.put(profile.race(), ATTACHMENT_TYPES.register(
                    profile.race().getSerializedName() + "_army_bank",
                    () -> AttachmentType.builder(() -> newBank(slots))
                            .serialize(codec(slots).fieldOf("items")).build()));
        }
        return banks;
    }

    /** How many slots this faction's bank holds; the Protoss fallback covers NEUTRAL. */
    public static int slotsFor(Faction faction) {
        RaceProfile profile = Races.of(faction);
        return profile == null ? Races.PROTOSS.bankSlots() : profile.bankSlots();
    }

    private static NonNullList<ItemStack> newBank(int slots) {
        return NonNullList.withSize(slots, ItemStack.EMPTY);
    }

    /** Encodes only the non-empty stacks; decodes back into a fixed-size list padded with empties. */
    private static Codec<NonNullList<ItemStack>> codec(int slots) {
        return ItemStack.CODEC.listOf().xmap(
                nonEmpty -> {
                    NonNullList<ItemStack> items = newBank(slots);
                    for (int i = 0; i < nonEmpty.size() && i < slots; i++) {
                        items.set(i, nonEmpty.get(i));
                    }
                    return items;
                },
                items -> items.stream().filter(stack -> !stack.isEmpty()).toList());
    }

    /**
     * Resolves the shared item list for a faction's army. Falls back to a fresh, disconnected
     * list if {@code level} isn't a {@link ServerLevel} yet — defensive only, since a building's
     * {@code Container} methods are only ever exercised once it's placed in a real world.
     *
     * <p>NEUTRAL has no race and so no bank of its own; it reads the Protoss one, exactly as it did
     * when this was a two-armed switch. Nothing NEUTRAL owns a production building, so this is a
     * total-function formality rather than a behaviour.
     *
     * <p>The returned list is the attachment's own cached instance, mutated in place — that
     * aliasing is what makes every building a real linked chest with no cache of its own (see
     * docs/neoforge-api-notes.md).
     */
    public static NonNullList<ItemStack> of(Level level, Faction faction) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return newBank(slotsFor(faction));
        }
        Race race = faction.race();
        return serverLevel.getData(BANKS.get(race == null ? Race.PROTOSS : race).get());
    }
}
