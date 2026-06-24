// src/main/java/pl/mzebrows/shoots/config/ObjectStyle.java
package pl.mzebrows.shoots.config;

/**
 * Visual styling (sizes/factors) for the map-object renderers, externalised from {@code graphic.properties}
 * so the rendering layer carries no hard-coded geometry. AWT-free so it stays unit-testable.
 *
 * @param baseRingBig          radius (px) of a player base's outer rotating ring
 * @param baseRingSmall        radius (px) of a player base's inner rotating ring
 * @param discCoreRadius       radius (px) of the bright core drawn on a powered disc
 * @param cursorSizeFactor     aim-cursor arrow size as a fraction of the grid unit
 * @param cursorStandoffFactor distance from base centre to the arrow, as a multiple of the arrow size
 * @param chargeGlowThreshold  charge fraction [0,1] at which the power-charge ring starts to glow
 */
public record ObjectStyle(
        int baseRingBig,
        int baseRingSmall,
        int discCoreRadius,
        double cursorSizeFactor,
        double cursorStandoffFactor,
        double chargeGlowThreshold) {

    public ObjectStyle {
        if (baseRingBig <= 0 || baseRingSmall <= 0) {
            throw new IllegalArgumentException("base ring radii must be positive");
        }
        if (discCoreRadius < 0) {
            throw new IllegalArgumentException("discCoreRadius must be non-negative: " + discCoreRadius);
        }
        if (cursorSizeFactor <= 0 || cursorStandoffFactor <= 0) {
            throw new IllegalArgumentException("cursor factors must be positive");
        }
        if (chargeGlowThreshold < 0.0 || chargeGlowThreshold > 1.0) {
            throw new IllegalArgumentException("chargeGlowThreshold must be in [0,1]: " + chargeGlowThreshold);
        }
    }
}
