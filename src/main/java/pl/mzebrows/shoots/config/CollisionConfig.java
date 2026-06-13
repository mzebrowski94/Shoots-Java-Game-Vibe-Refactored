// src/main/java/pl/mzebrows/shoots/config/CollisionConfig.java
package pl.mzebrows.shoots.config;

/** Tunables for the bounce/collision detector: edge tolerance band against tile boundaries. */
public record CollisionConfig(int ballCollisionSize) {

    public CollisionConfig {
        if (ballCollisionSize < 0) {
            throw new IllegalArgumentException("ballCollisionSize must be non-negative: " + ballCollisionSize);
        }
    }
}
