// src/main/java/pl/mzebrows/shoots/spatial/TileType.java
package pl.mzebrows.shoots.spatial;

/** Typed map-tile content, replacing the legacy magic ints (0/1/2/3) in the collision grid. */
public enum TileType {
    EMPTY,
    WALL,
    CAPTURE_POINT,
    PLAYER_BASE;

    /** Maps a legacy {@code mapMatrix} cell value to its tile type; unknown values are EMPTY. */
    public static TileType fromLegacy(int value) {
        return switch (value) {
            case 1 -> WALL;
            case 2 -> CAPTURE_POINT;
            case 3 -> PLAYER_BASE;
            default -> EMPTY;
        };
    }

    /** Whether a disc reflects off this tile (walls and the outer border). */
    public boolean isSolid() {
        return this == WALL;
    }
}
