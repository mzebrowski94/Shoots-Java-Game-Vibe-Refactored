// src/main/java/pl/mzebrows/shoots/entity/Entity.java
package pl.mzebrows.shoots.entity;

import lombok.Getter;
import lombok.Setter;

/**
 * Mutable, pooled runtime entity composed via injected strategies (no inheritance, no AWT).
 *
 * <p>Position/velocity are primitive fields to avoid autoboxing in the hot loop. {@code prevX}/
 * {@code prevY} hold last-step position so the renderer can interpolate using the loop's alpha.
 * Pooled instances are reused: call {@link #reset()} before re-acquiring, never replace the object.
 */
@Getter
@Setter
public final class Entity {

    private EntityType type;
    private boolean active;

    private double x;
    private double y;
    private double prevX;
    private double prevY;
    private double velX;
    private double velY;

    /** Travel angle in degrees, matching the legacy disc convention. */
    private double angle;
    private int directionX;
    private int directionY;
    private double moveSpeed;

    private int radius;

    /** Owning player id (-1 = none); used by combat/scoring without a separate component array. */
    private int ownerId;

    private int bounces;
    private int maxBounces;

    private MovementStrategy movementStrategy;
    private AttackStrategy attackStrategy;
    private AiStrategy aiStrategy;

    /** Returns position on the given axis component interpolated toward the current step. */
    public double interpolatedX(double alpha) {
        return prevX + (x - prevX) * alpha;
    }

    /** Returns Y position interpolated between the previous and current step. */
    public double interpolatedY(double alpha) {
        return prevY + (y - prevY) * alpha;
    }

    /** Snapshots the current position into the previous-step fields before integration. */
    public void snapshot() {
        prevX = x;
        prevY = y;
    }

    /** Clears all state so the instance can be safely returned to and re-acquired from a pool. */
    public void reset() {
        type = null;
        active = false;
        x = y = prevX = prevY = velX = velY = 0.0;
        angle = 0.0;
        directionX = 1;
        directionY = 1;
        moveSpeed = 0.0;
        radius = 0;
        ownerId = -1;
        bounces = 0;
        maxBounces = 0;
        movementStrategy = null;
        attackStrategy = null;
        aiStrategy = null;
    }
}
