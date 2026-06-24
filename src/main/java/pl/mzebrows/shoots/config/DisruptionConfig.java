// src/main/java/pl/mzebrows/shoots/config/DisruptionConfig.java
package pl.mzebrows.shoots.config;

/**
 * Tunables for the "base disruption" mechanic: hitting an opponent's base parks the attacking disc on
 * it and temporarily disables that opponent's shooting and laser prediction. When the effect ends the
 * victim gets a short immunity ("grace") window during which they cannot be disrupted again.
 * Externalised from {@code game.properties} so the feature is tuned without touching code.
 *
 * @param enabled         master switch; when {@code false} discs pass through bases and nothing disrupts
 * @param durationSeconds wall-clock seconds a base stays disrupted (shooting + laser off) (&gt; 0)
 * @param graceSeconds    immunity window after a disruption ends, during which the base cannot be
 *                        disrupted again (the player may shoot normally) (&gt;= 0)
 */
public record DisruptionConfig(boolean enabled, double durationSeconds, double graceSeconds) {

    public DisruptionConfig {
        if (durationSeconds <= 0) {
            throw new IllegalArgumentException("durationSeconds must be positive: " + durationSeconds);
        }
        if (graceSeconds < 0) {
            throw new IllegalArgumentException("graceSeconds must be non-negative: " + graceSeconds);
        }
    }
}
