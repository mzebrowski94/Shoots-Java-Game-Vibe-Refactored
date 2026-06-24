// src/main/java/pl/mzebrows/shoots/ai/AiTargeting.java
package pl.mzebrows.shoots.ai;

import pl.mzebrows.shoots.spatial.GridPathTracer;

/**
 * AWT-free shot-reachability walker for the AI. Given a firing origin and angle, it walks the exact
 * analytic bounce path the live discs take ({@link GridPathTracer}) and reports the FIRST capture-point
 * tile the path reaches plus how many wall bounces it took to get there.
 *
 * <p>Reuses one {@link GridPathTracer.Ray}, so a scan performs no per-call allocation. This is the
 * cheap core of "score each candidate angle, pick the best": the controller calls {@link #reach} for a
 * handful of angles per decision and ranks the hits.
 */
public final class AiTargeting {

    /** Outcome of walking one candidate angle: which capture-point tile it reaches and after how many bounces. */
    public record Reach(boolean reached, int tileX, int tileY, int bounces) {
        static final Reach NONE = new Reach(false, -1, -1, -1);
    }

    private final GridPathTracer tracer;
    private final int unit;
    private final int maxBounces;
    private final GridPathTracer.Ray ray = new GridPathTracer.Ray();
    private final ReachVisitor reachVisitor = new ReachVisitor();
    private final FirstWallVisitor firstWallVisitor = new FirstWallVisitor();

    public AiTargeting(GridPathTracer tracer, int maxBounces) {
        this.tracer = tracer;
        this.unit = tracer.unit();
        this.maxBounces = maxBounces;
    }

    /**
     * Walks the bounce path from ({@code startX},{@code startY}) at {@code angle} and returns the first
     * capture-point tile it crosses, or {@link Reach#NONE} if none is reached within the bounce budget.
     * {@code speed} is accepted for compatibility but ignored (the path is speed-independent).
     */
    public Reach reach(double startX, double startY, double angle, double speed) {
        seedRay(startX, startY, angle);
        reachVisitor.reset();
        tracer.walk(ray, Double.MAX_VALUE, maxBounces, reachVisitor);
        if (reachVisitor.reached) {
            return new Reach(true, reachVisitor.tileX, reachVisitor.tileY, reachVisitor.bounces);
        }
        return Reach.NONE;
    }

    /** Tile the bounce path FIRST contacts as a wall (its first reflection tile), for flank-block filtering. */
    public long firstWallTile(double startX, double startY, double angle, double speed) {
        seedRay(startX, startY, angle);
        firstWallVisitor.reset();
        tracer.walk(ray, Double.MAX_VALUE, 1, firstWallVisitor);
        return packTile(firstWallVisitor.tileX, firstWallVisitor.tileY);
    }

    private void seedRay(double startX, double startY, double angle) {
        double radians = Math.toRadians(-angle);
        ray.set(startX, startY, Math.sin(radians), Math.cos(radians));
    }

    /** Packs a tile (x,y) into a single long key (matches the convention used elsewhere). */
    public static long packTile(int tileX, int tileY) {
        return (((long) tileX) << 32) ^ (tileY & 0xFFFFFFFFL);
    }

    /** Stops at the first capture tile entered, recording it and the bounce count so far. */
    private final class ReachVisitor implements GridPathTracer.PathVisitor {
        private boolean reached;
        private int tileX;
        private int tileY;
        private int bounces;

        void reset() {
            reached = false;
            tileX = -1;
            tileY = -1;
            bounces = -1;
        }

        @Override
        public boolean onCapturePoint(double x, double y, int tx, int ty) {
            reached = true;
            tileX = tx;
            tileY = ty;
            bounces = ray.reflections;
            return true;
        }
    }

    /** Stops at the first wall reflection, recording the struck tile. */
    private static final class FirstWallVisitor implements GridPathTracer.PathVisitor {
        private int tileX = -1;
        private int tileY = -1;

        void reset() {
            tileX = -1;
            tileY = -1;
        }

        @Override
        public boolean onReflect(double x, double y, int tx, int ty) {
            tileX = tx;
            tileY = ty;
            return true;
        }
    }
}
