package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.combat.DelayedRegen;
import net.bitflora.asteriskcraft.combat.ShieldAttachments;
import net.bitflora.asteriskcraft.combat.ShieldEventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * What it takes to bring a building down: its siege HP, its Protoss shield buffer, and the warp-in
 * phase during which both are only half there. Owned by every {@link SiegeTarget} block entity
 * (Nexus, Gateway, Hive) so all three take damage, regenerate shields and collapse identically —
 * a Zerg building simply passes a zero shield pool, and a pre-placed one a zero warp duration.
 *
 * <p>Shields behave exactly as a unit's do (see {@link ShieldEventHandler}): they soak damage ahead
 * of HP and recharge on their own after a lull, on the same delay and at the same rate. HP never
 * regenerates. What they can't reuse is {@code ShieldAttachments} itself — that pool is an entity
 * attachment driven by {@code LivingIncomingDamageEvent}, and a block entity is neither — so the
 * buffer lives here as saved state and the shared arithmetic comes from
 * {@link ShieldAttachments#resolveDamage} and {@link DelayedRegen#regen}.
 *
 * <p>While warping in, both pools are halved and the whole hit-splitting rule still applies as
 * normal; finishing the warp scales what survived back up ({@link WarpInVulnerability}), so a
 * building caught mid-warp comes out of it having sustained twice the damage. A kit-warped building
 * also stands as glass for that whole phase and fills itself in over it ({@link WarpScaffold}), which
 * is the one thing that can lengthen the countdown rather than only run it down — and whose smashed
 * panes are charged as siege damage the instant the building stands up, on the finished pools rather
 * than the halved ones.
 *
 * <p>The damage split is exposed as a pure {@link #resolve} so it is unit-testable without a world.
 */
public final class BuildingDefense {

    /** The outcome of one hit: the pools afterwards, and what the building should show for it. */
    public record Hit(int health, float shield, boolean absorbed, boolean razed) {
    }

    private final int maxHealth;
    private final int maxShield;
    private final int warpDuration;

    private int health;
    private float shield;
    private int warpTicksRemaining;
    private int shieldRegenDelay;
    /** The glass layout that fills in over the warp. Empty for a building that was never warped in. */
    private final WarpScaffold scaffold = new WarpScaffold();

    /**
     * @param maxHealth    siege HP once fully warped in
     * @param maxShield    shield buffer once fully warped in; zero for a Zerg building
     * @param warpDuration ticks the building spends warping in; zero for one that was never warped
     *                     in at all (a Hive, or the starting Nexus — see {@link #skipWarpIn})
     */
    public BuildingDefense(int maxHealth, int maxShield, int warpDuration) {
        this.maxHealth = maxHealth;
        this.maxShield = maxShield;
        this.warpDuration = warpDuration;
        this.warpTicksRemaining = warpDuration;
        this.health = Math.round(effectiveMax(maxHealth));
        this.shield = effectiveMax(maxShield);
    }

    private float effectiveMax(float finishedPool) {
        return this.isWarping() ? WarpInVulnerability.warpPool(finishedPool) : finishedPool;
    }

    public boolean isWarping() {
        return this.warpTicksRemaining > 0;
    }

    public int warpTicksRemaining() {
        return this.warpTicksRemaining;
    }

    public int health() {
        return this.health;
    }

    public float shield() {
        return this.shield;
    }

    /** Current HP ceiling — half the finished pool while warping in. */
    public int maxHealth() {
        return Math.round(effectiveMax(this.maxHealth));
    }

    /** Current shield ceiling — half the finished pool while warping in. */
    public float maxShield() {
        return effectiveMax(this.maxShield);
    }

    /**
     * Advances the warp countdown and shield regen by a tick. Returns true on the single tick the
     * warp-in completes, so the building can play its own "ready" effects.
     */
    public boolean tick() {
        boolean completed = false;
        if (this.warpTicksRemaining > 0 && --this.warpTicksRemaining == 0) {
            // Scaling up what survived is what makes mid-warp damage cost double; the clamps only
            // matter for an odd pool, where half of it rounded up doubles to one over the max.
            this.health = Math.min(this.maxHealth, Math.round(WarpInVulnerability.onWarpComplete(this.health)));
            this.shield = Math.min(this.maxShield, WarpInVulnerability.onWarpComplete(this.shield));
            completed = true;
        }
        if (this.shieldRegenDelay > 0) {
            this.shieldRegenDelay--;
        } else {
            this.shield = DelayedRegen.regen(this.shield, this.maxShield(), ShieldEventHandler.REGEN_PER_TICK);
        }
        return completed;
    }

    /**
     * Starts this building's warp-in from the top: the full countdown, both pools back down to their
     * mid-warp halves, and the just-stamped layout frozen into the glass scaffold that materialises
     * over it. The exact inverse of {@link #skipWarpIn}, and the only way a scaffold is ever raised —
     * glass with no countdown running it down would stand there forever.
     *
     * <p>Load-bearing, not belt-and-braces: a building stamped from a structure template comes back
     * carrying the block-entity NBT the structure block captured in the <em>design</em> world, where
     * it was standing finished (see docs/neoforge-api-notes.md). So a freshly warped-in building
     * arrives with a spent countdown and has to be told to warp, rather than being trusted to start
     * out that way.
     */
    public void beginWarpIn(ServerLevel level, BuildingTemplates.Placed placed) {
        if (this.restartWarpIn()) {
            this.scaffold.raise(level, placed);
        }
    }

    /**
     * The state half of {@link #beginWarpIn} — countdown and pools, no world — split out so the reset
     * is testable without a live level. False for a building with no warp phase at all (a Hive), which
     * is also what keeps a scaffold from being raised with no countdown to run it down.
     */
    boolean restartWarpIn() {
        if (this.warpDuration <= 0) {
            return false;
        }
        this.warpTicksRemaining = this.warpDuration;
        this.health = Math.round(effectiveMax(this.maxHealth));
        this.shield = effectiveMax(this.maxShield);
        return true;
    }

    /**
     * Clears the unfinished glass away when this building is destroyed — see
     * {@link WarpScaffold#collapse}. A no-op for anything not caught mid-warp.
     */
    public void collapseScaffold(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            this.scaffold.collapse(serverLevel);
        }
    }

    /**
     * {@link #tick()} plus the warp-in presentation the Protoss buildings share: the layout filling
     * in pane by pane out of its glass scaffold, soul-fire flames while it does, and a beacon chime
     * the moment it stands up. Returns true while it is still warping — i.e. "this building isn't
     * open for business yet".
     *
     * <p>The scaffold is ticked before the countdown so panes smashed since the last sweep have
     * already lengthened the warp they are being paced against.
     */
    public boolean tickWarpIn(Level level, BlockPos pos) {
        ServerLevel serverLevel = level instanceof ServerLevel server ? server : null;
        if (serverLevel != null && this.isWarping()) {
            this.warpTicksRemaining += this.scaffold.tick(serverLevel, this.warpTicksRemaining);
        }
        boolean completed = this.tick();
        if (serverLevel == null) {
            return this.isWarping();
        }
        if (completed) {
            this.scaffold.finish(serverLevel); // nothing may still be glass once the building is open
            serverLevel.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 1.0f);
            // Every pane an attacker smashed comes due now, against the pools the building actually
            // finished with — a warp harried the whole way through can collapse as it comes online.
            int owed = this.scaffold.collectDamageOwed();
            if (owed > 0) {
                this.damage(owed, serverLevel, pos);
            }
        } else if (this.isWarping()) {
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.4, 0.6, 0.4, 0.02);
        }
        return this.isWarping();
    }

    /**
     * Finishes the warp-in on the spot, at full strength. For a building the game stands up itself
     * rather than warping in from a kit — the starting Nexus, which is simply there when the world
     * begins (R1) and must not spend its first two minutes half-built and unable to train.
     */
    public void skipWarpIn() {
        this.warpTicksRemaining = 0;
        this.health = this.maxHealth;
        this.shield = this.maxShield;
    }

    /**
     * How a hit of {@code amount} divides between a shield buffer and HP. Pure, so the rule can be
     * unit-tested without a world. Leftover damage is rounded up onto HP: a hit is never softened by
     * the shields keeping a fraction of a point.
     */
    public static Hit resolve(int health, float shield, int amount) {
        ShieldAttachments.DamageResult split = ShieldAttachments.resolveDamage(shield, amount);
        int toHealth = (int) Math.ceil(split.remainingDamage());
        int newHealth = Math.max(0, health - toHealth);
        return new Hit(newHealth, split.remainingShield(), toHealth <= 0, newHealth <= 0);
    }

    /**
     * Applies {@code amount} points of siege damage and plays the matching effects. When HP hits zero
     * the core block is removed with {@code destroyBlock}, whose side effects resolve the match for a
     * {@link FactionCore}. Returns true if this hit razed the building.
     */
    public boolean damage(int amount, ServerLevel level, BlockPos pos) {
        Hit hit = resolve(this.health, this.shield, amount);
        this.health = hit.health();
        this.shield = hit.shield();
        this.shieldRegenDelay = ShieldEventHandler.REGEN_DELAY_TICKS;
        playHitEffects(level, pos, hit.absorbed());
        if (hit.razed()) {
            level.destroyBlock(pos, false); // fires preRemoveSideEffects -> GameOutcome for a core
        }
        return hit.razed();
    }

    /**
     * Shielded hits chime and spark; hits that reach the stonework crack it. A building has no hurt
     * flash to lean on, so this is the only way to read whether the shields are still holding.
     */
    private static void playHitEffects(ServerLevel level, BlockPos pos, boolean absorbed) {
        if (absorbed) {
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.7f, 1.4f);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.6, 0.6, 0.6, 0.05);
            return;
        }
        level.playSound(null, pos, SoundEvents.NETHERITE_BLOCK_HIT, SoundSource.BLOCKS, 0.7f, 0.8f);
        level.levelEvent(2001, pos, Block.getId(level.getBlockState(pos))); // crack particles at the core
    }

    public void save(ValueOutput output) {
        output.putInt("CoreHealth", this.health);
        output.putFloat("Shield", this.shield);
        output.putInt("ShieldDelay", this.shieldRegenDelay);
        output.putInt("WarpTicks", this.warpTicksRemaining);
        this.scaffold.save(output);
    }

    /**
     * Restores saved state. A missing warp countdown means "done warping" rather than a full one:
     * every building writes its own countdown out on every save, so the only readers that hit the
     * default are worlds saved before the building had a warp phase at all — and those buildings are
     * long since finished.
     */
    public void load(ValueInput input) {
        this.warpTicksRemaining = input.getIntOr("WarpTicks", 0);
        this.health = Math.min(this.maxHealth(), input.getIntOr("CoreHealth", this.maxHealth()));
        this.shield = Math.min(this.maxShield(), input.getFloatOr("Shield", this.maxShield()));
        this.shieldRegenDelay = input.getIntOr("ShieldDelay", 0);
        this.scaffold.load(input);
    }
}
