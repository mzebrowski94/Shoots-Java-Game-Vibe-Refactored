// src/main/java/pl/mzebrows/shoots/config/RoundConfig.java
package pl.mzebrows.shoots.config;

/** Round/match pacing: per-round duration, number of rounds, and animation/end delays (ticks). */
public record RoundConfig(int roundTimeSeconds, int roundLimit, int roundEndDelay, int animationTime) {

    public RoundConfig {
        if (roundTimeSeconds <= 0) {
            throw new IllegalArgumentException("roundTimeSeconds must be positive: " + roundTimeSeconds);
        }
        if (roundLimit <= 0) {
            throw new IllegalArgumentException("roundLimit must be positive: " + roundLimit);
        }
        if (roundEndDelay < 0) {
            throw new IllegalArgumentException("roundEndDelay must be non-negative: " + roundEndDelay);
        }
        if (animationTime < 0) {
            throw new IllegalArgumentException("animationTime must be non-negative: " + animationTime);
        }
    }
}
