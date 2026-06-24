// src/main/java/pl/mzebrows/shoots/spatial/SpatialCollider.java
package pl.mzebrows.shoots.spatial;

/**
 * Broad-phase grid query contract, hiding the partitioning structure (uniform grid, quad-tree).
 *
 * <p>Implementations replace the legacy O(N&sup2;) per-frame scan with O(1) tile lookups. Reflection
 * geometry itself is computed analytically by {@link GridPathTracer} (which casts a disc's ray against
 * tile faces), so this contract is just the tile-content query the tracer and AI build upon.
 */
public interface SpatialCollider {

    /** Returns the tile type at a tile index, or {@link TileType#WALL} for out-of-bounds (border). */
    TileType tileAt(int tileX, int tileY);
}
