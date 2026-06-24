// src/main/java/pl/mzebrows/shoots/spatial/UniformGridCollider.java
package pl.mzebrows.shoots.spatial;

import pl.mzebrows.shoots.config.CollisionConfig;
import pl.mzebrows.shoots.config.GridConfig;

/**
 * Uniform-grid {@link SpatialCollider}: tiles are {@code unit}-pixel squares; lookups are O(1).
 *
 * <p>Holds the typed tile grid and answers tile-content queries (out-of-bounds reads as the border
 * {@link TileType#WALL}). Disc/laser reflection is computed analytically by {@link GridPathTracer},
 * which reads tiles through this collider, so the collider itself no longer performs step-sampled
 * bounce resolution.
 */
public final class UniformGridCollider implements SpatialCollider {

    private final TileType[][] tiles;

    /**
     * Builds a collider over a typed tile grid. {@code grid}/{@code collision} are accepted for
     * call-site compatibility; tile geometry is the only state needed for lookups (the analytic
     * tracer derives the unit size from {@link GridConfig} directly).
     */
    public UniformGridCollider(TileType[][] tiles, GridConfig grid, CollisionConfig collision) {
        this.tiles = tiles;
    }

    /** Adapts a legacy {@code int[][]} matrix into a typed-grid collider. */
    public static UniformGridCollider fromLegacyMatrix(int[][] matrix, GridConfig grid, CollisionConfig collision) {
        var tiles = new TileType[matrix.length][];
        for (int i = 0; i < matrix.length; i++) {
            tiles[i] = new TileType[matrix[i].length];
            for (int j = 0; j < matrix[i].length; j++) {
                tiles[i][j] = TileType.fromLegacy(matrix[i][j]);
            }
        }
        return new UniformGridCollider(tiles, grid, collision);
    }

    @Override
    public TileType tileAt(int tileX, int tileY) {
        if (tileX < 0 || tileY < 0 || tileX >= tiles.length || tileY >= tiles[tileX].length) {
            return TileType.WALL;
        }
        return tiles[tileX][tileY];
    }
}
