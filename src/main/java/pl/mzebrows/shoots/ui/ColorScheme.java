// pl/mzebrows/shoots/ui/ColorScheme.java
package pl.mzebrows.shoots.ui;

import java.awt.Color;

import lombok.Getter;

/**
 * Immutable AWT colour palette for the game's UI panels. Holds the built-in default colours; the live
 * per-player palette is sourced from the externalised config ({@code ColorPalette}) elsewhere. Getters
 * are Lombok-generated, so call sites keep the {@code getXxxColor()} API while the boilerplate is gone.
 */
@Getter
public final class ColorScheme {

    private final Color backgroundColor = new Color(95, 99, 104);
    private final Color standardColor = new Color(25, 25, 25);
    private final Color winBlockColor = new Color(68, 74, 80);

    private final Color deadLineColor = new Color(102, 0, 102);
    private final Color deadLineBackgroundColor = new Color(102, 75, 102);
    private final Color backgroundFontColor = new Color(192, 192, 192);
    private final Color backgroundPointBarColor = new Color(68, 74, 80);

    private final Color player1Color = new Color(124, 252, 0);
    private final Color player2Color = new Color(48, 213, 200);
    private final Color player3Color = new Color(252, 3, 0);
    private final Color player4Color = new Color(237, 26, 116);

    /** Near-opaque dark tint laid over the frozen game frame on the pause / menu screens. */
    private final Color menuStandardColor = new Color(35, 35, 35, 200);
}
