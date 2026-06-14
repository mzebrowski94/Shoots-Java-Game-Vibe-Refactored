// src/main/java/pl/mzebrows/shoots/entity/LaserPredictor.java
package pl.mzebrows.shoots.entity;

import pl.mzebrows.shoots.spatial.SpatialCollider;

/**
 * AWT-free aiming guide: predicts a disc's bounce trajectory by reusing the exact reflection math
 * validated in cluster 5 (the {@link SpatialCollider} flips direction in place) plus the shared
 * {@link MovementStrategy} integration. Produces the polyline of reflection points the laser draws.
 *
 * <p>Constructor-injected with the collider and movement strategy so it stays decoupled from
 * rendering and is unit-testable without a graphics context. Reuses one scratch {@link Entity} and
 * caller-supplied output arrays, so a per-frame prediction performs no allocation.
 */
public final class LaserPredictor {

    private final SpatialCollider collider;
    private final MovementStrategy movement;
    private final int maxStepsPerSegment;
    private final Entity scratch = new Entity();

    public LaserPredictor(SpatialCollider collider, MovementStrategy movement) {
        this(collider, movement, 10_000);
    }

    public LaserPredictor(SpatialCollider collider, MovementStrategy movement, int maxStepsPerSegment) {
        this.collider = collider;
        this.movement = movement;
        this.maxStepsPerSegment = maxStepsPerSegment;
    }

    /**
     * Fills {@code xs}/{@code ys} with the laser polyline starting at ({@code startX},{@code startY}).
     * Index 0 is the origin; each subsequent index is the next predicted reflection point. Walks until
     * {@code xs.length} points are filled or the path is exhausted.
     *
     * @param angle travel angle in degrees (matching the disc/{@link BounceMovementStrategy} convention)
     * @param speed step size used while walking the prediction
     * @return number of points written (always at least 1)
     */
    public int predict(double startX, double startY, double angle, double speed, int[] xs, int[] ys) {
        int points = Math.min(xs.length, ys.length);
        if (points == 0) {
            return 0;
        }
        xs[0] = (int) startX;
        ys[0] = (int) startY;

        scratch.reset();
        scratch.setX(startX);
        scratch.setY(startY);
        scratch.setAngle(angle);
        scratch.setMoveSpeed(speed);
        scratch.setDirectionX(1);
        scratch.setDirectionY(1);

        for (int seg = 1; seg < points; seg++) {
            int baseline = scratch.getBounces();
            int steps = 0;
            while (scratch.getBounces() == baseline && steps < maxStepsPerSegment) {
                movement.move(scratch);
                collider.resolve(scratch);
                steps++;
            }
            xs[seg] = (int) scratch.getX();
            ys[seg] = (int) scratch.getY();
        }
        return points;
    }
}
