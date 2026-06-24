// src/main/java/pl/mzebrows/shoots/config/MenuTheme.java
package pl.mzebrows.shoots.config;

/**
 * Menu chrome colours and panel geometry, externalised from {@code graphic.properties} so the AWT menu
 * holds no hard-coded colours. Colours are {@link RgbColor} (AWT-free); converted to AWT at the
 * rendering boundary.
 *
 * @param label           action-row label colour (Continue / Start / Controls / Quit)
 * @param sublabel        section sub-label colour (e.g. "- Round Limit -")
 * @param value           idle numeric/value-row colour
 * @param separator       AI-section separator colour
 * @param panelFill       backdrop fill colour
 * @param panelBorder     backdrop outer border colour
 * @param panelGlow       backdrop inner glow colour
 * @param highlightFill   selected-row highlight fill colour
 * @param highlightBorder selected-row highlight rim colour
 * @param shadow          drop-shadow colour behind menu text
 * @param panelArc        backdrop corner radius (px)
 */
public record MenuTheme(
        RgbColor label,
        RgbColor sublabel,
        RgbColor value,
        RgbColor separator,
        RgbColor panelFill,
        RgbColor panelBorder,
        RgbColor panelGlow,
        RgbColor highlightFill,
        RgbColor highlightBorder,
        RgbColor shadow,
        int panelArc) {

    public MenuTheme {
        if (panelArc < 0) {
            throw new IllegalArgumentException("panelArc must be non-negative: " + panelArc);
        }
    }
}
