// src/main/java/pl/mzebrows/shoots/spatial/SpatialCollider.java
package pl.mzebrows.shoots.spatial;

import pl.mzebrows.shoots.entity.Entity;

/**
 * Broad-phase spatial query contract, hiding the partitioning structure (uniform grid, quad-tree).
 *
 * <p>Implementations replace the legacy O(N&sup2;) per-frame scan with O(1) tile lookups. On a wall
 * hit {@link #resolve(Entity)} mutates the entity's {@code directionX}/{@code directionY} in place,
 * matching the reflection contract that {@code BounceMovementStrategy} relies on. AWT-decoupled.
 */
public interface SpatialCollider {

    /** Returns the tile type at a tile index, or {@link TileType#WALL} for out-of-bounds (border). */
    TileType tileAt(int tileX, int tileY);

    /**
     * Resolves wall collisions for a moving entity: detects a bounce, flips the relevant direction
     * axis on the entity, increments its bounce count, and reports the struck tile.
     */
    CollisionResult resolve(Entity entity);
}
