// src/main/java/pl/mzebrows/shoots/entity/AimController.java
package pl.mzebrows.shoots.entity;

import lombok.Getter;

/**
 * AWT-free aiming state for a player base: tracks cursor rotation clamped to a symmetric limit and
 * combines it with the base's fixed firing direction to yield the current shot angle.
 *
 * <p>Replaces the rotation logic previously embedded in the legacy {@code PlayerCursor}/
 * {@code PlayerLaser}, so aiming can be unit-tested without a graphics context. {@code moveUnit}
 * carries the per-player sign so left/right map consistently regardless of base orientation.
 */
@Getter
public final class AimController {

    private final int shootDirection;
    private final double rotationLimit;
    private final double rotationStep;

    private double rotation;

    public AimController(int shootDirection, double rotationLimit, double rotationStep) {
        if (rotationLimit < 0) {
            throw new IllegalArgumentException("rotationLimit must be non-negative: " + rotationLimit);
        }
        if (rotationStep <= 0) {
            throw new IllegalArgumentException("rotationStep must be positive: " + rotationStep);
        }
        this.shootDirection = shootDirection;
        this.rotationLimit = rotationLimit;
        this.rotationStep = rotationStep;
    }

    /** Rotates left by one step (scaled by {@code moveUnit}), clamped to the rotation limit. */
    public double rotateLeft(double moveUnit) {
        return applyRotation(moveUnit * rotationStep);
    }

    /** Rotates right by one step (scaled by {@code moveUnit}), clamped to the rotation limit. */
    public double rotateRight(double moveUnit) {
        return applyRotation(-moveUnit * rotationStep);
    }

    private double applyRotation(double delta) {
        rotation += delta;
        if (rotation < -rotationLimit) {
            rotation = -rotationLimit;
        } else if (rotation > rotationLimit) {
            rotation = rotationLimit;
        }
        return rotation;
    }

    /** Resets aim back to the neutral firing direction. */
    public void reset() {
        rotation = 0;
    }

    /** Current shot angle in degrees: fixed base direction plus cursor rotation. */
    public double currentAngle() {
        return shootDirection + rotation;
    }
}
