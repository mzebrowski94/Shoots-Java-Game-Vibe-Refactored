// pl/mzebrows/shoots/ui/GameDimensions.java
package pl.mzebrows.shoots.ui;

import lombok.Getter;

/**
 * Podstawowe stałe jednostek używane w całej grze: rozmiar jednego kafelka w pikselach i rozmiar pola gry w kafelkach.
 */
@Getter
public enum GameDimensions {
    UNIT(36),
    TABLE_SIZE(25),
    WINDOW_TILES(25);

    private final int value;

    GameDimensions(int value) {
        this.value = value;
    }
}
