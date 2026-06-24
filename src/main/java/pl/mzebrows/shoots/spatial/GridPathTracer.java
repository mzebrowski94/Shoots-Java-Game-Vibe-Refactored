// src/main/java/pl/mzebrows/shoots/spatial/GridPathTracer.java
package pl.mzebrows.shoots.spatial;

/**
 * Analytic, speed-independent reflection walker for a static uniform tile grid.
 *
 * <p>Instead of sampling a disc's position against a tolerance band each step (which makes the bounce
 * point depend on step size, and lets a disc rack up several bounce events while lingering in a
 * corner band), this casts the disc's straight-line ray against grid cell boundaries (a DDA / grid
 * traversal) and reflects at the <em>exact</em> tile face. The resulting path -- the sequence of
 * reflection vertices -- is a pure function of {@code (start, direction, grid)}: a slow disc, a fast
 * disc and the predictive laser all follow the identical polyline; they only traverse it at different
 * rates. Each corner is a single, well-defined reflection event, so bounce counts never inflate.
 *
 * <p>Only {@link TileType#WALL} tiles (and the out-of-bounds border, via {@link SpatialCollider#tileAt})
 * reflect; capture-point tiles are passed through and reported via {@link PathVisitor#onCapturePoint}.
 * Reuses a caller-supplied {@link Ray}, so a walk performs no allocation and is safe in the hot loop.
 */
public final class GridPathTracer {

    /** Events emitted along a traced ray; any callback may halt the walk by returning {@code true}. */
    public interface PathVisitor {
        /** The ray reflected off solid tile ({@code tileX},{@code tileY}) at exact contact ({@code x},{@code y}). */
        default boolean onReflect(double x, double y, int tileX, int tileY) { return false; }

        /** The ray entered capture-point tile ({@code tileX},{@code tileY}) at ({@code x},{@code y}). */
        default boolean onCapturePoint(double x, double y, int tileX, int tileY) { return false; }

        /** The ray entered player-base tile ({@code tileX},{@code tileY}) at ({@code x},{@code y}). */
        default boolean onPlayerBase(double x, double y, int tileX, int tileY) { return false; }
    }

    /** Reusable ray state: position, unit travel direction, and a running reflection tally. */
    public static final class Ray {
        public double x;
        public double y;
        public double dirX;
        public double dirY;
        public int reflections;

        /** Re-seeds the ray at a point with a (unit) direction and a zero reflection count. */
        public void set(double x, double y, double dirX, double dirY) {
            this.x = x;
            this.y = y;
            this.dirX = dirX;
            this.dirY = dirY;
            this.reflections = 0;
        }
    }

    /** Pixel tolerance used to detect an exact corner (both grid lines crossed at once) and to bias cell membership. */
    private static final double EPS = 1e-7;

    private final SpatialCollider grid;
    private final int unit;
    private final long stepCap;

    public GridPathTracer(SpatialCollider grid, int unit) {
        this(grid, unit, 1_000_000L);
    }

    public GridPathTracer(SpatialCollider grid, int unit, long stepCap) {
        if (unit <= 0) {
            throw new IllegalArgumentException("unit must be positive: " + unit);
        }
        this.grid = grid;
        this.unit = unit;
        this.stepCap = stepCap;
    }

    public int unit() {
        return unit;
    }

    /** Tile lookup (out-of-bounds reads as the border {@link TileType#WALL}). */
    public TileType tileAt(int tileX, int tileY) {
        return grid.tileAt(tileX, tileY);
    }

    /**
     * Advances {@code ray} along its reflecting straight-line path by up to {@code maxDistance} pixels,
     * performing at most {@code maxReflections} reflections. Returns when the distance is consumed, the
     * reflection budget is reached, a visitor callback stops it, or the step cap trips. Mutates the ray
     * in place (position, direction and reflection tally).
     */
    public void walk(Ray ray, double maxDistance, int maxReflections, PathVisitor visitor) {
        double x = ray.x;
        double y = ray.y;
        double dx = ray.dirX;
        double dy = ray.dirY;
        int cx = cellIndex(x, dx);
        int cy = cellIndex(y, dy);

        // A disc may start inside a capture tile (e.g. sitting on the point); report it before moving.
        if (grid.tileAt(cx, cy).isCapturePoint() && visitor.onCapturePoint(x, y, cx, cy)) {
            store(ray, x, y, dx, dy);
            return;
        }
        // Likewise it may start on a player-base tile (e.g. an attacker firing across a base).
        if (grid.tileAt(cx, cy).isPlayerBase() && visitor.onPlayerBase(x, y, cx, cy)) {
            store(ray, x, y, dx, dy);
            return;
        }

        double remaining = maxDistance;
        long steps = 0;
        while (remaining > EPS && steps++ < stepCap) {
            double tX = boundaryDistance(x, dx, cx);
            double tY = boundaryDistance(y, dy, cy);
            double t = Math.min(tX, tY);
            if (t > remaining) { // exact reach (t==remaining) falls through so the wall crossing is processed
                x += remaining * dx;
                y += remaining * dy;
                break;
            }
            x += t * dx;
            y += t * dy;
            remaining -= t;

            int sx = dx > 0 ? 1 : (dx < 0 ? -1 : 0);
            int sy = dy > 0 ? 1 : (dy < 0 ? -1 : 0);
            boolean corner = sx != 0 && sy != 0 && Math.abs(tX - tY) <= EPS;

            int nCx = cx;
            int nCy = cy;
            boolean reflectX = false;
            boolean reflectY = false;
            boolean entered = false;
            int wallTx = 0;
            int wallTy = 0;

            if (corner) {
                boolean solX = solid(cx + sx, cy);
                boolean solY = solid(cx, cy + sy);
                boolean solD = solid(cx + sx, cy + sy);
                if (solX && solY) {                 // concave pocket: reflect both, stay put
                    reflectX = true;
                    reflectY = true;
                    wallTx = cx + sx;
                    wallTy = cy;
                } else if (solX) {                  // flat wall on the X side: reflect X, glide along it
                    reflectX = true;
                    wallTx = cx + sx;
                    wallTy = cy;
                    nCy = cy + sy;
                    entered = true;
                } else if (solY) {                  // flat wall on the Y side: reflect Y, glide along it
                    reflectY = true;
                    wallTx = cx;
                    wallTy = cy + sy;
                    nCx = cx + sx;
                    entered = true;
                } else if (solD) {                  // convex corner: glance off the dominant axis
                    wallTx = cx + sx;
                    wallTy = cy + sy;
                    if (Math.abs(dx) >= Math.abs(dy)) {
                        reflectX = true;
                        nCy = cy + sy;
                    } else {
                        reflectY = true;
                        nCx = cx + sx;
                    }
                    entered = true;
                } else {                            // open corner: pass straight through diagonally
                    nCx = cx + sx;
                    nCy = cy + sy;
                    entered = true;
                }
            } else if (tX < tY) {                   // vertical grid line
                int nx = cx + sx;
                if (solid(nx, cy)) {
                    reflectX = true;
                    wallTx = nx;
                    wallTy = cy;
                } else {
                    nCx = nx;
                    entered = true;
                }
            } else {                                // horizontal grid line
                int ny = cy + sy;
                if (solid(cx, ny)) {
                    reflectY = true;
                    wallTx = cx;
                    wallTy = ny;
                } else {
                    nCy = ny;
                    entered = true;
                }
            }

            boolean reflected = reflectX || reflectY;
            if (reflected && ray.reflections >= maxReflections) {
                break; // out of bounce budget: stop at the contact point
            }
            if (reflectX) {
                dx = -dx;
            }
            if (reflectY) {
                dy = -dy;
            }
            cx = nCx;
            cy = nCy;
            if (reflected) {
                ray.reflections++;
                if (visitor.onReflect(x, y, wallTx, wallTy)) {
                    break;
                }
            }
            if (entered) {
                TileType entryTile = grid.tileAt(cx, cy);
                if (entryTile.isCapturePoint() && visitor.onCapturePoint(x, y, cx, cy)) {
                    break;
                }
                if (entryTile.isPlayerBase() && visitor.onPlayerBase(x, y, cx, cy)) {
                    break;
                }
            }
        }
        store(ray, x, y, dx, dy);
    }

    private boolean solid(int tileX, int tileY) {
        return grid.tileAt(tileX, tileY).isSolid();
    }

    /** Cell index a point belongs to when travelling in {@code dir}, biased off an exact grid line. */
    private int cellIndex(double coord, double dir) {
        if (dir == 0) {
            return (int) Math.floor(coord / unit);
        }
        return (int) Math.floor((coord + Math.copySign(EPS, dir)) / unit);
    }

    /** Distance along the ray from {@code coord} (in {@code cell}) to the next grid line in {@code dir}. */
    private double boundaryDistance(double coord, double dir, int cell) {
        if (dir > 0) {
            return ((cell + 1.0) * unit - coord) / dir;
        }
        if (dir < 0) {
            return (cell * (double) unit - coord) / dir;
        }
        return Double.POSITIVE_INFINITY;
    }

    private static void store(Ray ray, double x, double y, double dx, double dy) {
        ray.x = x;
        ray.y = y;
        ray.dirX = dx;
        ray.dirY = dy;
    }
}
