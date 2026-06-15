// pl/mzebrows/shoots/ui/PSConst.java
package pl.mzebrows.shoots.ui;

import lombok.Getter;

/**
 * Core unit constants used across the game: the pixel size of one tile and the playfield size in tiles.
 */
@Getter
public enum PSConst {
    UNIT(36),
    TABLESIZE(25),
    WINDOW_TILES(25);

    private final int value;

    PSConst(int value) {
        this.value = value;
    }
}
