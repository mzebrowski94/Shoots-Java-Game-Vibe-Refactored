// src/main/java/pl/mzebrows/shoots/config/DiscConfig.java
package pl.mzebrows.shoots.config;

/**
 * Tunables for a player disc: geometry, speed, bounce limit, concurrent-disc cap, discs-per-shot,
 * laser-preview reflection count, per-bounce laser alpha falloff, and the per-bounce acceleration model.
 *
 * <p>{@code bounceSpeedGain} is the multiplicative speed increase applied on each wall bounce
 * ({@code 1.0} = no acceleration); {@code maxSpeedFactor} caps the realised speed at
 * {@code moveSpeed * maxSpeedFactor} so a disc cannot accelerate past a safe collision-detection
 * bound. The gain is applied deterministically (no randomness/time dependence) so disc motion stays
 * reproducible for replay/online-prediction.
 *
 * <p>{@code laserBounceAlphaFalloff} is the multiplicative opacity factor applied to each successive
 * aiming-laser segment ({@code 1.0} = uniform opacity); each segment after a bounce is rendered at
 * {@code falloff^bounceIndex} of full opacity, so the predicted path fades the further it reaches.
 */
public record DiscConfig(int bigRadius, int smallRadius, double moveSpeed, int maxBounces, int maxPerPlayer,
                         int maxPerShot, int laserMaxBounces, double bounceSpeedGain, double maxSpeedFactor,
                         double laserBounceAlphaFalloff) {

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
        if (bounceSpeedGain < 1.0) {
            throw new IllegalArgumentException("bounceSpeedGain must be >= 1.0: " + bounceSpeedGain);
        }
        if (maxSpeedFactor < 1.0) {
            throw new IllegalArgumentException("maxSpeedFactor must be >= 1.0: " + maxSpeedFactor);
        }
        if (laserBounceAlphaFalloff <= 0.0 || laserBounceAlphaFalloff > 1.0) {
            throw new IllegalArgumentException(
                    "laserBounceAlphaFalloff must be in (0, 1]: " + laserBounceAlphaFalloff);
        }
    }

    /** Back-compatible constructor with no acceleration (gain {@code 1.0}, cap {@code 1.0x}) and no laser falloff. */
    public DiscConfig(int bigRadius, int smallRadius, double moveSpeed, int maxBounces, int maxPerPlayer,
                      int maxPerShot, int laserMaxBounces) {
        this(bigRadius, smallRadius, moveSpeed, maxBounces, maxPerPlayer, maxPerShot, laserMaxBounces, 1.0, 1.0, 1.0);
    }

    /** Back-compatible constructor with acceleration but uniform laser opacity (falloff {@code 1.0}). */
    public DiscConfig(int bigRadius, int smallRadius, double moveSpeed, int maxBounces, int maxPerPlayer,
                      int maxPerShot, int laserMaxBounces, double bounceSpeedGain, double maxSpeedFactor) {
        this(bigRadius, smallRadius, moveSpeed, maxBounces, maxPerPlayer, maxPerShot, laserMaxBounces,
                bounceSpeedGain, maxSpeedFactor, 1.0);
    }

    /** Realised top speed a normal disc may reach after acceleration. */
    public double maxMoveSpeed() {
        return moveSpeed * maxSpeedFactor;
    }
}
