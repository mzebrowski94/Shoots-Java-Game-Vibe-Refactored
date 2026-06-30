// src/test/java/pl/mzebrows/shoots/system/TrajectoryConsistencyTest.java
package pl.mzebrows.shoots.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.shoots.config.CollisionConfig;
import pl.mzebrows.shoots.config.DiscConfig;
import pl.mzebrows.shoots.config.GridConfig;
import pl.mzebrows.shoots.spatial.GridPathTracer;
import pl.mzebrows.shoots.spatial.TileType;
import pl.mzebrows.shoots.spatial.UniformGridCollider;

/**
 * Reproduces the reported bugs and locks in the fix: with the analytic tracer the bounce path is a
 * pure function of (start, angle, grid), so a slow disc, a fast disc, and the predictive laser all
 * trace the identical trajectory.
 */
class TrajectoryConsistencyTest {

    private static final int UNIT = 36;
    private static final int SIZE = 25;
    private final GridConfig grid = new GridConfig(UNIT, SIZE);
    private final CollisionConfig collision = new CollisionConfig(4);
    // High bounce budget so discs survive long enough to compare a long path; no acceleration.
    private static final DiscConfig DISC = new DiscConfig(18, 10, 2.0, 100_000, 3, 3, 4);

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

    private GridPathTracer tracer() {
        return new GridPathTracer(new UniformGridCollider(emptyField(), grid, collision), UNIT);
    }

    /** Records the sequence of wall tiles a disc bounces off. */
    private static final class WallSeq implements DiscSystem.DiscEventSink {
        final List<Long> tiles = new ArrayList<>();
        @Override public boolean onCapturePointHit(Entity disc, int tileX, int tileY) { return false; }
        @Override public void onWallHit(Entity disc, int tileX, int tileY) {
            tiles.add((((long) tileX) << 32) ^ (tileY & 0xFFFFFFFFL));
        }
        @Override public void onDiscRetired(Entity disc) { }
    }

    private List<Long> bounceTiles(double startX, double startY, double angle, double speed, int frames) {
        var tracer = tracer();
        var combat = new DiscSpawner(new ObjectPool<>(2, Entity::new, Entity::reset), DISC);
        var system = new DiscSystem(tracer, combat);
        Entity disc = combat.spawnDisc(startX, startY, angle, 1);
        disc.setMoveSpeed(speed);
        var seq = new WallSeq();
        for (int f = 0; f < frames; f++) {
            system.update(List.of(disc), seq);
        }
        return seq.tiles;
    }

    @Test
    void slowAndFastDiscsFollowTheSameBouncePath() {
        double sx = 12 * UNIT + UNIT / 2.0;
        double sy = 12 * UNIT + UNIT / 2.0;
        double angle = 37; // a generic, non-axis-aligned heading

        List<Long> slow = bounceTiles(sx, sy, angle, 2.0, 1200);
        List<Long> fast = bounceTiles(sx, sy, angle, 8.0, 1200);

        assertThat(slow.size()).isGreaterThan(2);
        assertThat(fast.size()).isGreaterThanOrEqualTo(slow.size());
        // The slow disc's bounce tiles are a prefix of the fast disc's: identical geometry, different rate.
        assertThat(fast.subList(0, slow.size())).isEqualTo(slow);
    }

    @Test
    void laserPredictionMatchesTheTracedReflectionPoints() {
        var tracer = tracer();
        double sx = 10 * UNIT + UNIT / 2.0;
        double sy = 13 * UNIT + UNIT / 2.0;
        double angle = 51;

        int[] xs = new int[5];
        int[] ys = new int[5];
        new LaserPredictor(tracer).predict(sx, sy, angle, 2.0, xs, ys);

        // Independently walk the same geometry, recording reflection points.
        var ray = new GridPathTracer.Ray();
        double rad = Math.toRadians(-angle);
        ray.set(sx, sy, Math.sin(rad), Math.cos(rad));
        int[] rx = new int[4];
        int[] ry = new int[4];
        GridPathTracer.PathVisitor recorder = new GridPathTracer.PathVisitor() {
            int n;
            @Override public boolean onReflect(double x, double y, int tx, int ty) {
                if (n < rx.length) { rx[n] = (int) x; ry[n] = (int) y; n++; }
                return false;
            }
        };
        tracer.walk(ray, Double.MAX_VALUE, 4, recorder);

        // Laser index 0 is the origin; indices 1..4 must equal the traced reflection points.
        assertThat(xs[0]).isEqualTo((int) sx);
        assertThat(ys[0]).isEqualTo((int) sy);
        for (int i = 0; i < 4; i++) {
            assertThat(xs[i + 1]).isEqualTo(rx[i]);
            assertThat(ys[i + 1]).isEqualTo(ry[i]);
        }
    }

    @Test
    void discFiredAt45IntoACornerDoesNotVanishFromBounceInflation() {
        // Bug #1: a 45-degree shot crosses dozens of interior lattice points. The OLD band model could
        // bank a phantom bounce at each, blow past the 9-bounce budget and make the disc vanish within a
        // few frames. With the analytic tracer it only bounces off real walls, so it stays alive.
        var tracer = tracer();
        var combat = new DiscSpawner(new ObjectPool<>(2, Entity::new, Entity::reset),
                new DiscConfig(18, 10, 3.0, 9, 3, 3, 4)); // the live default bounce budget of 9
        var system = new DiscSystem(tracer, combat);
        Entity disc = combat.spawnDisc(12 * UNIT + UNIT / 2.0, 12 * UNIT + UNIT / 2.0, -45, 1);

        var discs = new ArrayList<Entity>();
        discs.add(disc);
        int firstFrameWithBounce = -1;
        for (int f = 0; f < 400 && !discs.isEmpty(); f++) {
            system.update(discs, DiscSystem.NO_OP);
            if (firstFrameWithBounce < 0 && disc.getBounces() > 0) {
                firstFrameWithBounce = f;
            }
        }

        // It bounced off real walls but is nowhere near exhausting its budget -> no phantom inflation.
        assertThat(firstFrameWithBounce).isGreaterThanOrEqualTo(0);
        assertThat(disc.isActive()).isTrue();
        assertThat(disc.getBounces()).isLessThan(9);
    }
}
