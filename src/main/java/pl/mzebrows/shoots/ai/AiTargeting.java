// src/main/java/pl/mzebrows/shoots/ai/AiTargeting.java
package pl.mzebrows.shoots.ai;

import pl.mzebrows.shoots.entity.BounceMovementStrategy;
import pl.mzebrows.shoots.entity.Entity;
import pl.mzebrows.shoots.entity.MovementStrategy;
import pl.mzebrows.shoots.spatial.SpatialCollider;
import pl.mzebrows.shoots.spatial.TileType;

/**
 * AWT-free shot-reachability walker for the AI. Given a firing origin and angle, it walks the exact
 * bounce path the live discs take (reusing {@link BounceMovementStrategy} + the {@link SpatialCollider}
 * reflection math, like {@code LaserPredictor}) and reports the FIRST capture-point tile the path
 * reaches plus how many wall bounces it took to get there.
 *
 * <p>Reuses one scratch {@link Entity}, so a scan performs no per-call allocation. This is the cheap
 * core of "score each candidate angle, pick the best": the controller (C2) calls {@link #reach} for a
 * handful of angles per decision and ranks the hits.
 */
public final class AiTargeting {

    /** Outcome of walking one candidate angle: which capture-point tile it reaches and after how many bounces. */
    public record Reach(boolean reached, int tileX, int tileY, int bounces) {
        static final Reach NONE = new Reach(false, -1, -1, -1);
    }

    private final SpatialCollider collider;
    private final MovementStrategy movement;
    private final int unit;
    private final int maxBounces;
    private final int maxStepsPerSegment;
    private final Entity scratch = new Entity();

    public AiTargeting(SpatialCollider collider, int unit, int maxBounces) {
        this(collider, new BounceMovementStrategy(), unit, maxBounces, 10_000);
    }

    public AiTargeting(SpatialCollider collider, MovementStrategy movement, int unit, int maxBounces,
                       int maxStepsPerSegment) {
        this.collider = collider;
        this.movement = movement;
        this.unit = unit;
        this.maxBounces = maxBounces;
        this.maxStepsPerSegment = maxStepsPerSegment;
    }

    /**
     * Walks the bounce path from ({@code startX},{@code startY}) at {@code angle} and returns the first
     * capture-point tile it crosses, or {@link Reach#NONE} if none is reached within the bounce budget.
     *
     * @param speed step size used while walking (use the disc move speed)
     */
    public Reach reach(double startX, double startY, double angle, double speed) {
        scratch.reset();
        scratch.setX(startX);
        scratch.setY(startY);
        scratch.setAngle(angle);
        scratch.setMoveSpeed(speed);
        scratch.setDirectionX(1);
        scratch.setDirectionY(1);

        int lastTileX = (int) startX / unit;
        int lastTileY = (int) startY / unit;
        int steps = 0;
        int stepCap = maxStepsPerSegment * (maxBounces + 1);

        while (scratch.getBounces() <= maxBounces && steps < stepCap) {
            movement.move(scratch);
            int tx = (int) scratch.getX() / unit;
            int ty = (int) scratch.getY() / unit;
            if (tx != lastTileX || ty != lastTileY) {
                if (collider.tileAt(tx, ty) == TileType.CAPTURE_POINT) {
                    return new Reach(true, tx, ty, scratch.getBounces());
                }
                lastTileX = tx;
                lastTileY = ty;
            }
            collider.resolve(scratch);
            steps++;
        }
        return Reach.NONE;
    }

    /** Tile the bounce path FIRST contacts as a wall (its first reflection tile), for flank-block filtering. */
    public long firstWallTile(double startX, double startY, double angle, double speed) {
        scratch.reset();
        scratch.setX(startX);
        scratch.setY(startY);
        scratch.setAngle(angle);
        scratch.setMoveSpeed(speed);
        scratch.setDirectionX(1);
        scratch.setDirectionY(1);

        int steps = 0;
        while (scratch.getBounces() == 0 && steps < maxStepsPerSegment) {
            movement.move(scratch);
            collider.resolve(scratch);
            steps++;
        }
        int tx = (int) scratch.getX() / unit;
        int ty = (int) scratch.getY() / unit;
        return packTile(tx, ty);
    }

    /** Packs a tile (x,y) into a single long key (matches the convention used elsewhere). */
    public static long packTile(int tileX, int tileY) {
        return (((long) tileX) << 32) ^ (tileY & 0xFFFFFFFFL);
    }
}
