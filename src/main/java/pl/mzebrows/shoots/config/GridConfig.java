// src/main/java/pl/mzebrows/shoots/config/GridConfig.java
package pl.mzebrows.shoots.config;

/** Static map geometry: tile size in pixels and the square grid dimension in tiles. */
public record GridConfig(int unit, int tableSize) {

    public GridConfig {
        if (unit <= 0) {
            throw new IllegalArgumentException("unit must be positive: " + unit);
        }
        if (tableSize <= 0) {
            throw new IllegalArgumentException("tableSize must be positive: " + tableSize);
        }
    }

    /** Playfield width/height in pixels (square map). */
    public int playfieldPixels() {
        return unit * tableSize;
    }

    /** Highest valid tile index on either axis. */
    public int maxIndex() {
        return tableSize - 1;
    }
}
