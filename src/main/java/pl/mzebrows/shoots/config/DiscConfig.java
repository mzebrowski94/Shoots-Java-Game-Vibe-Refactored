// src/main/java/pl/mzebrows/shoots/config/DiscConfig.java
package pl.mzebrows.shoots.config;

/** Tunables for a player disc (projectile): geometry, speed, and bounce limit. */
public record DiscConfig(int bigRadius, int smallRadius, double moveSpeed, int maxBounces) {

    public DiscConfig {
        if (bigRadius <= 0) {
            throw new IllegalArgumentException("bigRadius must be positive: " + bigRadius);
        }
        if (smallRadius < 0 || smallRadius >= bigRadius) {
            throw new IllegalArgumentException("smallRadius must be in [0, bigRadius): " + smallRadius);
        }
        if (moveSpeed <= 0) {
            throw new IllegalArgumentException("moveSpeed must be positive: " + moveSpeed);
        }
        if (maxBounces < 0) {
            throw new IllegalArgumentException("maxBounces must be non-negative: " + maxBounces);
        }
    }
}
