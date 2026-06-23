// src/main/java/pl/mzebrows/shoots/config/WindowConfig.java
package pl.mzebrows.shoots.config;

/**
 * AWT window/panel sizing, expressed as multiples of the grid {@code unit} so the layout scales with
 * the tile size. The playfield is a square of {@code windowTiles} tiles; the score counter and the
 * side pointer panels are sized relative to the same unit.
 *
 * @param windowTiles        playfield width/height in tiles (square window)
 * @param counterHeightTiles score-counter panel height in tiles
 * @param pointerWidthTiles  side-pointer panel width in tiles
 */
public record WindowConfig(int windowTiles, int counterHeightTiles, int pointerWidthTiles) {

    public WindowConfig {
        if (windowTiles <= 0) {
            throw new IllegalArgumentException("windowTiles must be positive: " + windowTiles);
        }
        if (counterHeightTiles <= 0) {
            throw new IllegalArgumentException("counterHeightTiles must be positive: " + counterHeightTiles);
        }
        if (pointerWidthTiles <= 0) {
            throw new IllegalArgumentException("pointerWidthTiles must be positive: " + pointerWidthTiles);
        }
    }
}
