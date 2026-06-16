// src/main/java/pl/mzebrows/shoots/config/DiscConfig.java
package pl.mzebrows.shoots.config;

/** Tunables for a player disc: geometry, speed, bounce limit, concurrent-disc cap, discs-per-shot, and laser-preview reflection count. */
public record DiscConfig(int bigRadius, int smallRadius, double moveSpeed, int maxBounces, int maxPerPlayer, int maxPerShot, int laserMaxBounces) {

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
        if (maxPerPlayer < 1) {
            throw new IllegalArgumentException("maxPerPlayer must be at least 1: " + maxPerPlayer);
        }
        if (maxPerShot < 1 || maxPerShot > maxPerPlayer) {
            throw new IllegalArgumentException("maxPerShot must be in [1, maxPerPlayer]: " + maxPerShot);
        }
        if (laserMaxBounces < 0) {
            throw new IllegalArgumentException("laserMaxBounces must be non-negative: " + laserMaxBounces);
        }
    }
}
