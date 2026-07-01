// src/main/java/pl/mzebrows/shoots/spatial/MapSize.java
package pl.mzebrows.shoots.spatial;

import java.util.Arrays;

/**
 * Fixed list of permissible (square) map sizes. Map generation is authored against these exact
 * dimensions (base centres, diagonal shooting-prevention walls, macro grids), so an arbitrary
 * {@code tableSize} is rejected rather than silently producing a broken layout. Adding a size =
 * add a constant here and author its fixed geometry in {@link MapGenerator}.
 */
public enum MapSize {

    /** The current 25x25 map ({@code GameConfigLoader.TABLE_SIZE}). */
    NORMAL(25);

    private final int tableSize;

    MapSize(int tableSize) {
        this.tableSize = tableSize;
    }

    /** Square grid dimension in tiles. */
    public int tableSize() {
        return tableSize;
    }

    /** Resolves a grid dimension to its permissible size; throws for anything not on the list. */
    public static MapSize fromTableSize(int tableSize) {
        for (MapSize size : values()) {
            if (size.tableSize == tableSize) {
                return size;
            }
        }
        throw new IllegalArgumentException(
                "Unsupported map size: " + tableSize + " (permissible: " + Arrays.toString(values()) + ")");
    }
}
