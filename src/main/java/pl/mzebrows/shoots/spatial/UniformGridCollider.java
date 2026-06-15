// src/main/java/pl/mzebrows/shoots/spatial/UniformGridCollider.java
package pl.mzebrows.shoots.spatial;

import pl.mzebrows.shoots.config.CollisionConfig;
import pl.mzebrows.shoots.config.GridConfig;
import pl.mzebrows.shoots.entity.Entity;

/**
 * Uniform-grid {@link SpatialCollider}: tiles are {@code unit}-pixel squares; lookups are O(1).
 *
 * <p>Replaces the legacy {@code ColisionCalculator}/{@code ColisionPoint} pair. Reflection follows
 * the original convention &mdash; the outer border always bounces, and within the field a disc
 * reflects when it enters the {@code ballCollisionSize} tolerance band of a solid neighbour in its
 * direction of travel. On a bounce the entity's {@code directionX}/{@code directionY} is flipped in
 * place and {@code bounces} is incremented; this is the only mutation the collider performs.
 */
public final class UniformGridCollider implements SpatialCollider {

    private final TileType[][] tiles;
    private final int unit;
    private final int maxIndex;
    private final int tolerance;

    /** Builds a collider over a typed tile grid; the grid is square per {@link GridConfig}. */
    public UniformGridCollider(TileType[][] tiles, GridConfig grid, CollisionConfig collision) {
        this.tiles = tiles;
        this.unit = grid.unit();
        this.maxIndex = grid.maxIndex();
        this.tolerance = collision.ballCollisionSize();
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

    @Override
    public CollisionResult resolve(Entity entity) {
        int inX = (int) entity.getX() / unit;
        int inY = (int) entity.getY() / unit;

        if (inX <= 0 || inY <= 0 || inX >= maxIndex || inY >= maxIndex) {
            entity.setBounces(entity.getBounces() + 1);
            return CollisionResult.hit(TileType.WALL, clampIndex(inX), clampIndex(inY));
        }

        int restX = (int) entity.getX() % unit;
        int restY = (int) entity.getY() % unit;

        boolean nearLeft = restX <= tolerance;
        boolean nearRight = restX >= unit - tolerance;
        boolean nearTop = restY <= tolerance;
        boolean nearBottom = restY >= unit - tolerance;

        int stepX = nearLeft ? -1 : (nearRight ? 1 : 0);
        int stepY = nearTop ? -1 : (nearBottom ? 1 : 0);

        // Corner: when the disc is in the tolerance band on both axes, decide the bounce from the
        // three neighbours in its travel quadrant. If only the diagonal tile is solid (a 45-degree
        // hit straight at a block corner), reflect BOTH axes -- otherwise the disc would keep its
        // diagonal heading, penetrate the solid corner tile, and bounce around stuck inside it.
        if (stepX != 0 && stepY != 0) {
            boolean sideX = tileAt(inX + stepX, inY).isSolid();
            boolean sideY = tileAt(inX, inY + stepY).isSolid();
            boolean diag = tileAt(inX + stepX, inY + stepY).isSolid();
            // Inner corner (both sides solid) or a clean diagonal-only corner -> flip both axes.
            if ((sideX && sideY) || (diag && !sideX && !sideY)) {
                entity.setDirectionX(-entity.getDirectionX());
                entity.setDirectionY(-entity.getDirectionY());
                entity.setBounces(entity.getBounces() + 1);
                return CollisionResult.hit(TileType.WALL, inX + stepX, inY + stepY);
            }
            // Otherwise exactly one side is solid: fall through to the single-axis branches below,
            // which reflect off that wall and let the disc slide along it.
        }

        if (stepX != 0 && tileAt(inX + stepX, inY).isSolid()) {
            entity.setDirectionX(-entity.getDirectionX());
            entity.setBounces(entity.getBounces() + 1);
            return CollisionResult.hit(TileType.WALL, inX + stepX, inY);
        }

        if (stepY != 0 && tileAt(inX, inY + stepY).isSolid()) {
            entity.setDirectionY(-entity.getDirectionY());
            entity.setBounces(entity.getBounces() + 1);
            return CollisionResult.hit(TileType.WALL, inX, inY + stepY);
        }

        TileType current = tileAt(inX, inY);
        if (current == TileType.CAPTURE_POINT) {
            return CollisionResult.hit(TileType.CAPTURE_POINT, inX, inY);
        }
        return CollisionResult.none();
    }

    private int clampIndex(int idx) {
        if (idx < 0) {
            return 0;
        }
        return Math.min(idx, maxIndex);
    }
}
