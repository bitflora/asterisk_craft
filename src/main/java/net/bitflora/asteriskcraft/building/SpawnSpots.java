package net.bitflora.asteriskcraft.building;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Finds an open, safely-footed spot near a building core to place a freshly produced unit. */
public final class SpawnSpots {
    private SpawnSpots() {
    }

    public static BlockPos findGroundSpot(ServerLevel level, BlockPos pos) {
        for (int r = 2; r <= 4; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    for (int dy = -3; dy <= 1; dy++) {
                        BlockPos candidate = pos.offset(dx, dy, dz);
                        if (level.getBlockState(candidate).isAir()
                                && level.getBlockState(candidate.above()).isAir()
                                && level.getBlockState(candidate.below()).isSolidRender()) {
                            return candidate;
                        }
                    }
                }
            }
        }
        return pos.above();
    }
}
