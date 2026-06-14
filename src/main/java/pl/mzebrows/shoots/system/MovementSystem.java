// src/main/java/pl/mzebrows/shoots/system/MovementSystem.java
package pl.mzebrows.shoots.system;

import java.util.List;
import pl.mzebrows.shoots.entity.Entity;
import pl.mzebrows.shoots.entity.MovementStrategy;

/** Advances every active, movable entity by one fixed step via its injected movement strategy. */
public final class MovementSystem {

    /**
     * Integrates each active entity that has a movement strategy. Uses an index loop and snapshots
     * the previous position first so the renderer can interpolate; no per-frame allocation.
     */
    public void update(List<Entity> entities) {
        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity e = entities.get(i);
            if (!e.isActive()) {
                continue;
            }
            MovementStrategy strategy = e.getMovementStrategy();
            if (strategy == null) {
                continue;
            }
            e.snapshot();
            strategy.move(e);
        }
    }
}
