package net.bitflora.asteriskcraft.compat.jade;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.building.BuildingDefense;
import net.bitflora.asteriskcraft.building.SiegeTarget;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;

/**
 * Puts a building's staying power in its Jade tooltip: {@code ❤ hp/max}, {@code ⛨ shield/max} for a
 * Protoss one, and the warp-in countdown while it is still materialising. Any {@link SiegeTarget}
 * qualifies — the provider reads {@link SiegeTarget#defense()} rather than switching on
 * Nexus-vs-Gateway-vs-Hive, so a new building type only has to be registered (see
 * {@link AsteriskCraftJadePlugin}).
 *
 * <p>Unlike {@link ProtossShieldProvider}, this genuinely needs a server data provider: a unit's
 * shields ride on a synced entity attachment, but a building's HP and shields are block-entity state
 * the client never sees. So the server ships the numbers per look-at ({@link #streamData}) and the
 * nested {@link Client} renders them — Jade's own paired-provider shape (its
 * {@code EntityHealthAndArmorProvider}), including sharing one uid across the pair, which is what
 * lets the client find this provider's slice of the server data. Nesting the client half also keeps
 * it from ever loading on a dedicated server, since only {@code registerClient} names it.
 */
public enum BuildingDefenseProvider implements StreamServerDataProvider<BlockAccessor, BuildingDefenseProvider.Data> {
    INSTANCE;

    /** The one look-at's worth of numbers that crosses the wire. */
    public record Data(int health, int maxHealth, float shield, float maxShield, int warpTicks) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Data::health,
                ByteBufCodecs.VAR_INT, Data::maxHealth,
                ByteBufCodecs.FLOAT, Data::shield,
                ByteBufCodecs.FLOAT, Data::maxShield,
                ByteBufCodecs.VAR_INT, Data::warpTicks,
                Data::new);
    }

    /** Null for any other block entity, which Jade takes as "nothing to send". */
    @Nullable
    @Override
    public Data streamData(BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof SiegeTarget target)) {
            return null;
        }
        BuildingDefense defense = target.defense();
        return new Data(defense.health(), defense.maxHealth(), defense.shield(), defense.maxShield(),
                defense.warpTicksRemaining());
    }

    @Override
    public boolean shouldRequestData(BlockAccessor accessor) {
        return accessor.getBlockEntity() instanceof SiegeTarget;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, Data> streamCodec() {
        return Data.STREAM_CODEC;
    }

    @Override
    public Identifier getUid() {
        return AsteriskCraft.id("building_defense");
    }

    /** The display half, client-side only. */
    public enum Client implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            BuildingDefenseProvider.INSTANCE.decodeFromData(accessor).ifPresent(data -> {
                tooltip.add(Component.literal("❤ " + data.health() + "/" + data.maxHealth())
                        .withStyle(ChatFormatting.RED));
                // Zerg buildings carry no shield pool at all, so they get no line rather than "0/0".
                if (data.maxShield() > 0.0f) {
                    tooltip.add(Component.literal("⛨ " + Math.round(data.shield()) + "/" + Math.round(data.maxShield()))
                            .withStyle(ChatFormatting.AQUA));
                }
                if (data.warpTicks() > 0) {
                    // Both pools shown above are the halved warp-in ones; this line says why.
                    tooltip.add(Component.translatable("gui.asteriskcraft.warping", (data.warpTicks() + 19) / 20)
                            .withStyle(ChatFormatting.GOLD));
                }
            });
        }

        @Override
        public Identifier getUid() {
            return BuildingDefenseProvider.INSTANCE.getUid();
        }
    }
}
