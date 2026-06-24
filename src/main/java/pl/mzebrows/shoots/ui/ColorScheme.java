// pl/mzebrows/shoots/ui/ColorScheme.java
package pl.mzebrows.shoots.ui;

import java.awt.Color;

import lombok.Getter;
import pl.mzebrows.shoots.config.ColorPalette;

/**
 * AWT colour adapter for the game's UI panels. Converts the AWT-free config {@link ColorPalette}
 * (immutable {@code RgbColor}s sourced from {@code graphic.properties}) into reusable {@link Color}s
 * once, so the panels share a single source of truth with the rest of the game and hold no hard-coded
 * colours. Getters are Lombok-generated, so call sites keep the {@code getXxxColor()} API.
 */
@Getter
public final class ColorScheme {

    private final Color backgroundColor;
    private final Color standardColor;
    private final Color winBlockColor;

    private final Color deadLineColor;
    private final Color deadLineBackgroundColor;
    private final Color backgroundFontColor;
    private final Color backgroundPointBarColor;

    private final Color player1Color;
    private final Color player2Color;
    private final Color player3Color;
    private final Color player4Color;

    /** Near-opaque dark tint laid over the frozen game frame on the pause / menu screens. */
    private final Color menuStandardColor;

    /** Builds the AWT colours from the configured palette (the single source of truth). */
    public ColorScheme(ColorPalette palette) {
        this.backgroundColor = palette.background().toAwt();
        this.standardColor = palette.standard().toAwt();
        this.winBlockColor = palette.winBlock().toAwt();
        this.deadLineColor = palette.deadLine().toAwt();
        this.deadLineBackgroundColor = palette.deadLineBackground().toAwt();
        this.backgroundFontColor = palette.fontBackground().toAwt();
        this.backgroundPointBarColor = palette.pointBarBackground().toAwt();
        this.player1Color = palette.playerColor(1).toAwt();
        this.player2Color = palette.playerColor(2).toAwt();
        this.player3Color = palette.playerColor(3).toAwt();
        this.player4Color = palette.playerColor(4).toAwt();
        this.menuStandardColor = palette.menuStandard().toAwt();
    }
}
