// src/test/java/pl/mzebrows/shoots/spatial/GridPathTracerTest.java
package pl.mzebrows.shoots.spatial;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.mzebrows.shoots.config.CollisionConfig;
import pl.mzebrows.shoots.config.GridConfig;

/** Analytic, speed-independent reflection geometry: exact tile-face bounces, corner handling, no inflation. */
class GridPathTracerTest {

    private static final int UNIT = 36;
    private static final int SIZE = 25;
    private final GridConfig grid = new GridConfig(UNIT, SIZE);
    private final CollisionConfig collision = new CollisionConfig(4);

    private TileType[][] emptyField() {
        var tiles = new TileType[SIZE][SIZE];
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                tiles[i][j] = (i == 0 || j == 0 || i == SIZE - 1 || j == SIZE - 1)
                        ? TileType.WALL : TileType.EMPTY;
            }
        }
        return tiles;
    }

    private GridPathTracer tracer(TileType[][] tiles) {
        return new GridPathTracer(new UniformGridCollider(tiles, grid, collision), UNIT);
    }

    private static double centre(int tile) {
        return tile * UNIT + UNIT / 2.0;
    }

    private GridPathTracer.Ray rayAt(double x, double y, double angle) {
        var r = new GridPathTracer.Ray();
        double rad = Math.toRadians(-angle);
        r.set(x, y, Math.sin(rad), Math.cos(rad));
        return r;
    }

    /** Captures the first reflection (point + tile) and first capture-tile entry. */
    private static final class Probe implements GridPathTracer.PathVisitor {
        double firstReflectX = Double.NaN, firstReflectY = Double.NaN;
        int wallTileX = -1, wallTileY = -1;
        int captureTileX = -1, captureTileY = -1;
        boolean stopOnReflect, stopOnCapture;
        @Override public boolean onReflect(double x, double y, int tx, int ty) {
            if (Double.isNaN(firstReflectX)) { firstReflectX = x; firstReflectY = y; wallTileX = tx; wallTileY = ty; }
            return stopOnReflect;
        }
        @Override public boolean onCapturePoint(double x, double y, int tx, int ty) {
            if (captureTileX < 0) { captureTileX = tx; captureTileY = ty; }
            return stopOnCapture;
        }
    }

    @Test
    void straightDownReflectsOffBottomBorderExactlyAtTheFace() {
        var t = tracer(emptyField());
        var ray = rayAt(centre(12), 1 * UNIT, 0); // angle 0 -> +Y, centre column
        var probe = new Probe();
        probe.stopOnReflect = true;

        t.walk(ray, 1_000_000, 5, probe);

        assertThat(ray.reflections).isEqualTo(1);
        assertThat(ray.dirY).isLessThan(0);                    // reversed off the floor
        assertThat(probe.firstReflectY).isEqualTo(24.0 * UNIT); // exact tile face, not a tolerance band
        assertThat(probe.wallTileY).isEqualTo(24);
    }

    @Test
    void convexCornerGlancesOffASingleAxis() {
        var tiles = emptyField();
        tiles[6][6] = TileType.WALL; // lone block; (6,5) and (5,6) stay EMPTY
        var t = tracer(tiles);
        // From the centre of (5,5) heading +x +y straight at the block's (6,6) corner lattice point.
        var ray = rayAt(centre(5), centre(5), -45);
        var probe = new Probe();
        probe.stopOnReflect = true;

        t.walk(ray, 1_000_000, 5, probe);

        assertThat(ray.reflections).isEqualTo(1);
        assertThat(probe.wallTileX).isEqualTo(6);
        assertThat(probe.wallTileY).isEqualTo(6);
        // Exactly one axis flips (a glance), never both (which would be a straight reversal).
        boolean xFlipped = ray.dirX < 0;
        boolean yFlipped = ray.dirY < 0;
        assertThat(xFlipped ^ yFlipped).isTrue();
    }

    @Test
    void concaveCornerReversesBothAxes() {
        var tiles = emptyField();
        tiles[6][6] = TileType.WALL;
        tiles[6][5] = TileType.WALL;
        tiles[5][6] = TileType.WALL;
        var t = tracer(tiles);
        var ray = rayAt(centre(5), centre(5), -45); // into the concave pocket
        var probe = new Probe();
        probe.stopOnReflect = true;

        t.walk(ray, 1_000_000, 5, probe);

        assertThat(ray.reflections).isEqualTo(1);
        assertThat(ray.dirX).isLessThan(0);
        assertThat(ray.dirY).isLessThan(0);
    }

    @Test
    void diagonalThroughOpenFieldDoesNotInflateBouncesAtInteriorCorners() {
        // A 45-degree ray crosses ~70 interior lattice points over this distance; the OLD band model
        // could rack up a bounce at each. The analytic tracer only reflects at real walls (borders).
        var t = tracer(emptyField());
        var ray = rayAt(centre(12), centre(12), -45);

        t.walk(ray, 100.0 * UNIT, 1000, new GridPathTracer.PathVisitor() { });

        assertThat(ray.reflections).isLessThan(12); // only border bounces, nowhere near one-per-lattice
    }

    @Test
    void capturePointDetectedOnEntry() {
        var tiles = emptyField();
        tiles[18][12] = TileType.CAPTURE_POINT;
        var t = tracer(tiles);
        var ray = rayAt(centre(12), centre(12), -90); // angle -90 -> +X along row 12
        var probe = new Probe();
        probe.stopOnCapture = true;

        t.walk(ray, 1_000_000, 5, probe);

        assertThat(probe.captureTileX).isEqualTo(18);
        assertThat(probe.captureTileY).isEqualTo(12);
        assertThat(ray.reflections).isZero(); // reached directly, no wall bounce
    }

    @Test
    void pathGeometryIsIndependentOfWalkDistance() {
        // Walking a short distance vs a long one yields the SAME reflection sequence (a prefix), i.e.
        // the path is determined by geometry, not by how far/fast we travel along it.
        var t = tracer(emptyField());
        var shortProbe = new SeqProbe();
        var longProbe = new SeqProbe();

        t.walk(rayAt(centre(10), centre(10), 33), 40.0 * UNIT, 1000, shortProbe);
        t.walk(rayAt(centre(10), centre(10), 33), 200.0 * UNIT, 1000, longProbe);

        assertThat(shortProbe.count).isGreaterThan(0);
        assertThat(longProbe.count).isGreaterThanOrEqualTo(shortProbe.count);
        for (int i = 0; i < shortProbe.count; i++) {
            assertThat(longProbe.tx[i]).isEqualTo(shortProbe.tx[i]);
            assertThat(longProbe.ty[i]).isEqualTo(shortProbe.ty[i]);
        }
    }

    @Test
    void deterministicForSameInputs() {
        var t = tracer(emptyField());
        var a = rayAt(centre(7), centre(9), 51);
        var b = rayAt(centre(7), centre(9), 51);

        t.walk(a, 250.0 * UNIT, 1000, new GridPathTracer.PathVisitor() { });
        t.walk(b, 250.0 * UNIT, 1000, new GridPathTracer.PathVisitor() { });

        assertThat(a.x).isEqualTo(b.x);
        assertThat(a.y).isEqualTo(b.y);
        assertThat(a.dirX).isEqualTo(b.dirX);
        assertThat(a.dirY).isEqualTo(b.dirY);
        assertThat(a.reflections).isEqualTo(b.reflections);
    }

    /** Records the sequence of wall tiles struck. */
    private static final class SeqProbe implements GridPathTracer.PathVisitor {
        final int[] tx = new int[2000];
        final int[] ty = new int[2000];
        int count;
        @Override public boolean onReflect(double x, double y, int t, int u) {
            if (count < tx.length) { tx[count] = t; ty[count] = u; count++; }
            return false;
        }
    }
}
