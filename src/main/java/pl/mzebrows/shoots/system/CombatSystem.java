// src/main/java/pl/mzebrows/shoots/system/CombatSystem.java
package pl.mzebrows.shoots.system;

import pl.mzebrows.shoots.config.DiscConfig;
import pl.mzebrows.shoots.config.PowerShotConfig;
import pl.mzebrows.shoots.entity.BounceMovementStrategy;
import pl.mzebrows.shoots.entity.Entity;
import pl.mzebrows.shoots.entity.EntitySpawner;
import pl.mzebrows.shoots.entity.EntityType;
import pl.mzebrows.shoots.entity.MovementStrategy;
import pl.mzebrows.shoots.pool.ObjectPool;

/**
 * Owns disc lifecycle: spawns pooled discs (as an {@link EntitySpawner}) and retires spent ones.
 *
 * <p>Constructor-injected with the disc pool, disc config, charged-shot config, and the shared
 * movement strategy, so no collaborators are created in hot paths and disc tuning lives entirely in
 * {@link DiscConfig}/{@link PowerShotConfig}. A normal disc and a power disc differ only in the
 * config-derived stats stamped onto the pooled entity at spawn (speed, bounce budget, capture
 * strength, acceleration cap, and the powered/glow flag) -- there is no special-cased physics.
 */
public final class CombatSystem implements EntitySpawner {

    /** Power config used by the back-compat constructors that predate the charged shot (disabled). */
    private static final PowerShotConfig POWER_DISABLED = new PowerShotConfig(false, 1.0, 1.0, 1.0, 1);

    private final ObjectPool<Entity> discPool;
    private final DiscConfig discConfig;
    private final PowerShotConfig powerConfig;
    private final MovementStrategy discMovement;

    public CombatSystem(ObjectPool<Entity> discPool, DiscConfig discConfig) {
        this(discPool, discConfig, POWER_DISABLED, new BounceMovementStrategy());
    }

    public CombatSystem(ObjectPool<Entity> discPool, DiscConfig discConfig, PowerShotConfig powerConfig) {
        this(discPool, discConfig, powerConfig, new BounceMovementStrategy());
    }

    public CombatSystem(ObjectPool<Entity> discPool, DiscConfig discConfig, PowerShotConfig powerConfig,
                        MovementStrategy discMovement) {
        this.discPool = discPool;
        this.discConfig = discConfig;
        this.powerConfig = powerConfig;
        this.discMovement = discMovement;
    }

    @Override
    public Entity spawnDisc(double x, double y, double angle, int ownerId, boolean powered) {
        Entity disc = discPool.acquire();
        if (disc == null) {
            return null;
        }
        boolean asPower = powered && powerConfig.enabled();
        double speed = asPower ? discConfig.moveSpeed() * powerConfig.speedFactor() : discConfig.moveSpeed();
        int maxBounces = asPower ? powerConfig.effectiveMaxBounces(discConfig.maxBounces()) : discConfig.maxBounces();
        int captureStrength = asPower ? powerConfig.captureStrength() : 1;

        disc.setType(EntityType.DISC);
        disc.setActive(true);
        disc.setX(x);
        disc.setY(y);
        disc.snapshot();
        disc.setAngle(angle);
        disc.setDirectionX(1);
        disc.setDirectionY(1);
        disc.setMoveSpeed(speed);
        disc.setSpeedGainPerBounce(discConfig.bounceSpeedGain());
        // Cap accelerated speed relative to the disc's own launch speed so power discs can still ramp up.
        disc.setMaxMoveSpeed(speed * discConfig.maxSpeedFactor());
        disc.setRadius(discConfig.bigRadius());
        disc.setOwnerId(ownerId);
        disc.setCaptureStrength(captureStrength);
        disc.setPowered(asPower);
        disc.setBounces(0);
        disc.setMaxBounces(maxBounces);
        disc.setMovementStrategy(discMovement);
        return disc;
    }

    /** Returns {@code true} once a disc has used up its bounce budget and should be retired. */
    public boolean isSpent(Entity disc) {
        return disc.getBounces() >= disc.getMaxBounces();
    }

    /** Deactivates a spent disc and returns it to the pool for reuse. */
    public void retire(Entity disc) {
        disc.setActive(false);
        discPool.release(disc);
    }
}
