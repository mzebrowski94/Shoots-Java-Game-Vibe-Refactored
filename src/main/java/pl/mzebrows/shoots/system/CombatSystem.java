// src/main/java/pl/mzebrows/shoots/system/CombatSystem.java
package pl.mzebrows.shoots.system;

import pl.mzebrows.shoots.config.DiscConfig;
import pl.mzebrows.shoots.entity.BounceMovementStrategy;
import pl.mzebrows.shoots.entity.Entity;
import pl.mzebrows.shoots.entity.EntitySpawner;
import pl.mzebrows.shoots.entity.EntityType;
import pl.mzebrows.shoots.entity.MovementStrategy;
import pl.mzebrows.shoots.pool.ObjectPool;

/**
 * Owns disc lifecycle: spawns pooled discs (as an {@link EntitySpawner}) and retires spent ones.
 *
 * <p>Constructor-injected with the disc pool, disc config, and the shared movement strategy, so no
 * collaborators are created in hot paths and disc tuning lives entirely in {@link DiscConfig}.
 */
public final class CombatSystem implements EntitySpawner {

    private final ObjectPool<Entity> discPool;
    private final DiscConfig discConfig;
    private final MovementStrategy discMovement;

    public CombatSystem(ObjectPool<Entity> discPool, DiscConfig discConfig) {
        this(discPool, discConfig, new BounceMovementStrategy());
    }

    public CombatSystem(ObjectPool<Entity> discPool, DiscConfig discConfig, MovementStrategy discMovement) {
        this.discPool = discPool;
        this.discConfig = discConfig;
        this.discMovement = discMovement;
    }

    @Override
    public Entity spawnDisc(double x, double y, double angle, int ownerId) {
        Entity disc = discPool.acquire();
        if (disc == null) {
            return null;
        }
        disc.setType(EntityType.DISC);
        disc.setActive(true);
        disc.setX(x);
        disc.setY(y);
        disc.snapshot();
        disc.setAngle(angle);
        disc.setDirectionX(1);
        disc.setDirectionY(1);
        disc.setMoveSpeed(discConfig.moveSpeed());
        disc.setRadius(discConfig.bigRadius());
        disc.setOwnerId(ownerId);
        disc.setBounces(0);
        disc.setMaxBounces(discConfig.maxBounces());
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
