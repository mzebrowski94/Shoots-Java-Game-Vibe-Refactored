// src/main/java/pl/mzebrows/shoots/spatial/CollisionResult.java
package pl.mzebrows.shoots.spatial;

/**
 * Immutable outcome of a single collision query: whether a hit occurred, the tile struck, and the
 * tile indices of the struck cell ({@code -1} when no tile is involved, e.g. the outer border).
 */
public record CollisionResult(boolean collided, TileType tile, int tileX, int tileY) {

    private static final CollisionResult NONE = new CollisionResult(false, TileType.EMPTY, -1, -1);

    /** Shared "no collision" instance, avoiding allocation on the common path. */
    public static CollisionResult none() {
        return NONE;
    }

    /** A collision against a specific tile cell. */
    public static CollisionResult hit(TileType tile, int tileX, int tileY) {
        return new CollisionResult(true, tile, tileX, tileY);
    }
}
