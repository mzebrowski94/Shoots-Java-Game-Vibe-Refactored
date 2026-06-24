// src/main/java/pl/mzebrows/shoots/entity/LaserPredictor.java
package pl.mzebrows.shoots.entity;

import pl.mzebrows.shoots.spatial.GridPathTracer;

/**
 * AWT-free aiming guide: predicts a disc's bounce trajectory by walking the SAME analytic reflection
 * geometry the live discs use ({@link GridPathTracer}). Because that geometry is a pure function of
 * origin, direction and the grid, the predicted polyline matches the fired disc's path exactly,
 * independent of disc speed.
 *
 * <p>Constructor-injected with the tracer so it stays decoupled from rendering and unit-testable
 * without a graphics context. Reuses one {@link GridPathTracer.Ray} and the caller-supplied output
 * arrays, so a per-frame prediction performs no allocation.
 */
public final class LaserPredictor {

    private final GridPathTracer tracer;
    private final GridPathTracer.Ray ray = new GridPathTracer.Ray();
    private final Recorder recorder = new Recorder();

    public LaserPredictor(GridPathTracer tracer) {
        this.tracer = tracer;
    }

    /**
     * Fills {@code xs}/{@code ys} with the laser polyline starting at ({@code startX},{@code startY}).
     * Index 0 is the origin; each subsequent index is the next predicted reflection point. The
     * {@code speed} argument is accepted for API compatibility but ignored -- the reflection path is
     * speed-independent.
     *
     * @param angle travel angle in degrees (matching the disc convention: dir = (sin(-a), cos(-a)))
     * @return number of points written (always at least 1)
     */
    public int predict(double startX, double startY, double angle, double speed, int[] xs, int[] ys) {
        int points = Math.min(xs.length, ys.length);
        if (points == 0) {
            return 0;
        }
        xs[0] = (int) startX;
        ys[0] = (int) startY;

        double radians = Math.toRadians(-angle);
        ray.set(startX, startY, Math.sin(radians), Math.cos(radians));
        recorder.reset(xs, ys, points);
        // Walk an effectively unbounded distance, recording up to (points - 1) reflection vertices.
        tracer.walk(ray, Double.MAX_VALUE, points - 1, recorder);

        // Pad any unfilled tail (e.g. the path could not produce enough reflections) with the end point.
        for (int i = 1 + recorder.count; i < points; i++) {
            xs[i] = (int) ray.x;
            ys[i] = (int) ray.y;
        }
        return points;
    }

    /** Records each reflection point into the output polyline arrays. */
    private static final class Recorder implements GridPathTracer.PathVisitor {
        private int[] xs;
        private int[] ys;
        private int cap;
        private int count;

        void reset(int[] xs, int[] ys, int points) {
            this.xs = xs;
            this.ys = ys;
            this.cap = points - 1;
            this.count = 0;
        }

        @Override
        public boolean onReflect(double x, double y, int tileX, int tileY) {
            if (count < cap) {
                xs[1 + count] = (int) x;
                ys[1 + count] = (int) y;
                count++;
            }
            return false;
        }
    }
}
