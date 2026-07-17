package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The Photon Cannon core: warps in like the Gateway (a one-time countdown after the kit
 * stamps the structure), then auto-attacks the nearest enemy-faction unit in range once
 * per cooldown with an instant "energy bolt" — direct damage plus a particle trail and a
 * zap sound. No production queue, no GUI. Hostility is resolved purely through the faction
 * attachment, so it never fights same-faction or NEUTRAL entities.
 */
public class PhotonCannonBlockEntity extends BlockEntity implements WarpInBuilding {
    public static final int WARP_TICKS = 200;       // 10 seconds to warp in (mirrors the Gateway)
    public static final double RANGE = 7.0;         // StarCraft Photon Cannon range
    public static final float ATTACK_DAMAGE = 4.0f; // 2 hearts per shot
    public static final int ATTACK_COOLDOWN = 20;   // one shot per second

    // Economy figures (from docs/shaping.md V4). The crafting recipe is the real item sink;
    // these are the design source of truth, guarded by PhotonCannonEconomyTest.
    public static final int WOOD_COST = 100;
    public static final int COBBLE_COST = 100;
    public static final int IRON_COST = 20;

    private int warpTicksRemaining = WARP_TICKS;
    private int attackCooldownRemaining = ATTACK_COOLDOWN;
    private Faction faction = Faction.PROTOSS;

    public PhotonCannonBlockEntity(BlockPos pos, BlockState state) {
        super(AsteriskCraft.PHOTON_CANNON_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public void setFaction(Faction faction) {
        this.faction = faction;
        this.setChanged();
    }

    public boolean isWarping() {
        return this.warpTicksRemaining > 0;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PhotonCannonBlockEntity cannon) {
        if (cannon.warpTicksRemaining > 0) {
            cannon.warpTicksRemaining--;
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        6, 0.4, 0.6, 0.4, 0.02);
            }
            if (cannon.warpTicksRemaining == 0) {
                level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 1.0f);
            }
            cannon.setChanged();
            return;
        }

        if (cannon.attackCooldownRemaining > 0) {
            cannon.attackCooldownRemaining--;
            return;
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        AABB range = new AABB(pos).inflate(RANGE);
        List<LivingEntity> candidates = serverLevel.getEntitiesOfClass(LivingEntity.class, range,
                e -> PhotonCannonTargeting.isTargetable(cannon.faction, FactionAttachments.get(e), e.isAlive()));

        PhotonCannonTargeting.nearest(candidates, e -> e.distanceToSqr(cx, cy, cz))
                .ifPresent(target -> {
                    cannon.fireAt(serverLevel, new Vec3(cx, cy, cz), target);
                    cannon.attackCooldownRemaining = ATTACK_COOLDOWN;
                    cannon.setChanged();
                });
    }

    /** Deals the bolt's damage and draws an energy-beam particle trail from the core to the target. */
    private void fireAt(ServerLevel level, Vec3 origin, LivingEntity target) {
        target.hurtServer(level, level.damageSources().magic(), ATTACK_DAMAGE);

        Vec3 hit = target.getEyePosition();
        Vec3 delta = hit.subtract(origin);
        int steps = 8;
        for (int i = 1; i <= steps; i++) {
            Vec3 point = origin.add(delta.scale((double) i / steps));
            level.sendParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        level.playSound(null, this.worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0f, 1.6f);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("WarpTicks", this.warpTicksRemaining);
        output.store("Faction", Faction.CODEC, this.faction);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.warpTicksRemaining = input.getIntOr("WarpTicks", WARP_TICKS);
        this.faction = input.read("Faction", Faction.CODEC).orElse(Faction.PROTOSS);
        this.attackCooldownRemaining = ATTACK_COOLDOWN;
    }
}
