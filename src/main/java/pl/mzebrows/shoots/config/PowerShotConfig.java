// src/main/java/pl/mzebrows/shoots/config/PowerShotConfig.java
package pl.mzebrows.shoots.config;

/**
 * Tunables for the charged "power shot": a stronger disc released after the shoot key is held long
 * enough to fill the charge bar. Externalised from {@code game.properties} so the feature is tuned
 * without touching code.
 *
 * @param enabled         master switch; when {@code false} holding the key never produces a power shot
 * @param chargeSeconds   wall-clock seconds the key must be held for the bar to fill and auto-fire (&gt; 0)
 * @param speedFactor     power-disc launch speed as a multiple of the normal disc {@code moveSpeed} (&gt;= 1)
 * @param maxBounces      bounce budget of a power disc -- "more deflection points" than a normal disc (&gt;= 0)
 * @param captureStrength capture levels a single power hit applies to a control point (&gt;= 1; normal = 1)
 */
public record PowerShotConfig(boolean enabled, double chargeSeconds, double speedFactor,
                              int maxBounces, int captureStrength) {

    public PowerShotConfig {
        if (chargeSeconds <= 0) {
            throw new IllegalArgumentException("chargeSeconds must be positive: " + chargeSeconds);
        }
        if (speedFactor < 1.0) {
            throw new IllegalArgumentException("speedFactor must be >= 1.0: " + speedFactor);
        }
        if (maxBounces < 0) {
            throw new IllegalArgumentException("maxBounces must be non-negative: " + maxBounces);
        }
        if (captureStrength < 1) {
            throw new IllegalArgumentException("captureStrength must be >= 1: " + captureStrength);
        }
    }
}
