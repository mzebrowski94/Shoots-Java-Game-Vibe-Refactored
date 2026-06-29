// src/main/java/pl/mzebrows/shoots/config/PowerShotConfig.java
package pl.mzebrows.shoots.config;

/**
 * Tunables for the charged "power shot": a stronger disc released after the shoot key is held long
 * enough to fill the charge bar. Externalised from {@code game.properties} so the feature is tuned
 * without touching code.
 *
 * @param enabled          master switch; when {@code false} holding the key never produces a power shot
 * @param chargeSeconds    wall-clock seconds the key must be held for the bar to fill and auto-fire (&gt; 0)
 * @param speedFactor      power-disc launch speed as a multiple of the normal disc {@code speed} (&gt;= 1)
 * @param maxBouncesFactor power-disc bounce budget as a multiple of the normal disc {@code maxBounces}
 *                         (&gt;= 1; e.g. {@code 1.5} = 50% more deflection points than a normal disc)
 * @param captureStrength  capture levels a single power hit applies to a control point (&gt;= 1; normal = 1)
 */
public record PowerShotConfig(boolean enabled, double chargeSeconds, double speedFactor,
                              double maxBouncesFactor, int captureStrength) {

    public PowerShotConfig {
        if (chargeSeconds <= 0) {
            throw new IllegalArgumentException("chargeSeconds must be positive: " + chargeSeconds);
        }
        if (speedFactor < 1.0) {
            throw new IllegalArgumentException("speedFactor must be >= 1.0: " + speedFactor);
        }
        if (maxBouncesFactor < 1.0) {
            throw new IllegalArgumentException("maxBouncesFactor must be >= 1.0: " + maxBouncesFactor);
        }
        if (captureStrength < 1) {
            throw new IllegalArgumentException("captureStrength must be >= 1: " + captureStrength);
        }
    }

    /** Power-disc bounce budget scaled off the normal disc's {@code maxBounces} (rounded to the nearest tile). */
    public int effectiveMaxBounces(int discMaxBounces) {
        return (int) Math.round(discMaxBounces * maxBouncesFactor);
    }
}
