package net.bitflora.asteriskcraft.building;

import com.mojang.serialization.Codec;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.faction.Faction;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * The per-army shared resource pool that every production building belonging to one faction
 * draws from: {@link NexusBlockEntity} and {@link GatewayBlockEntity} (Protoss) act like linked
 * chests onto {@link #PROTOSS_BANK}; every {@link HiveBlockEntity} (Zerg) onto {@link #ZERG_BANK}.
 * Stored as a level attachment (not on any one building) so the pool survives independently of
 * which specific building placed or removed it — see {@link ArmyLinkedContainer}.
 */
public final class ArmyBank {
    /** 3x a single building's original 9 slots. */
    public static final int PROTOSS_SLOTS = 27;
    /** 3x a single Hive's original 27 slots. */
    public static final int ZERG_SLOTS = 81;

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, AsteriskCraft.MODID);

    public static final Supplier<AttachmentType<NonNullList<ItemStack>>> PROTOSS_BANK = ATTACHMENT_TYPES.register(
            "protoss_army_bank", () -> AttachmentType.builder(() -> newBank(PROTOSS_SLOTS))
                    .serialize(codec(PROTOSS_SLOTS).fieldOf("items")).build());

    public static final Supplier<AttachmentType<NonNullList<ItemStack>>> ZERG_BANK = ATTACHMENT_TYPES.register(
            "zerg_army_bank", () -> AttachmentType.builder(() -> newBank(ZERG_SLOTS))
                    .serialize(codec(ZERG_SLOTS).fieldOf("items")).build());

    private ArmyBank() {
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
     */
    public static NonNullList<ItemStack> of(Level level, Faction faction) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return newBank(faction == Faction.ZERG ? ZERG_SLOTS : PROTOSS_SLOTS);
        }
        return switch (faction) {
            case ZERG -> serverLevel.getData(ZERG_BANK.get());
            case PROTOSS, NEUTRAL -> serverLevel.getData(PROTOSS_BANK.get());
        };
    }
}
