// src/test/java/pl/mzebrows/shoots/system/DiscAccelerationTest.java
package pl.mzebrows.shoots.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.shoots.config.CollisionConfig;
import pl.mzebrows.shoots.config.DiscConfig;
import pl.mzebrows.shoots.config.GridConfig;
import pl.mzebrows.shoots.config.PowerShotConfig;
import pl.mzebrows.shoots.spatial.GridPathTracer;
import pl.mzebrows.shoots.spatial.TileType;
import pl.mzebrows.shoots.spatial.UniformGridCollider;

/**
 * Feature #1: a disc accelerates smoothly on each wall bounce (capped). With the analytic tracer the
 * bounce path is exact regardless of speed, so even very fast discs stay on their corridor and never
 * tunnel through a wall.
 */
class DiscAccelerationTest {

    private static final int UNIT = 36;
    private static final int SIZE = 25;

    private final GridConfig grid = new GridConfig(UNIT, SIZE);
    private final CollisionConfig collision = new CollisionConfig(4);

    /** Empty bordered field with two interior wall columns forming a short horizontal corridor. */
    private TileType[][] corridorField() {
        var tiles = new TileType[SIZE][SIZE];
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                tiles[i][j] = (i == 0 || j == 0 || i == SIZE - 1 || j == SIZE - 1)
                        ? TileType.WALL : TileType.EMPTY;
            }
        }
        tiles[8][12] = TileType.WALL;  // left wall of the corridor
        tiles[16][12] = TileType.WALL; // right wall of the corridor
        return tiles;
    }

    private GridPathTracer tracer(TileType[][] tiles) {
        return new GridPathTracer(new UniformGridCollider(tiles, grid, collision), UNIT);
    }

    private ObjectPool<Entity> pool() {
        return new ObjectPool<>(4, Entity::new, Entity::reset);
    }

    @Test
    void discSpeedsUpAfterWallBouncesAndIsCappedAndNeverTunnels() {
        // gain 1.5/bounce, cap at 3x the launch speed (9.0); a very high bounce budget so it survives.
        var disc = new DiscConfig(18, 10, 3.0, 100_000, 3, 3, 4, 1.5, 3.0);
        var combat = new DiscSpawner(pool(), disc);
        var system = new DiscSystem(tracer(corridorField()), combat);

        // Launch along +X (angle -90) inside the corridor, between the two walls.
        Entity e = combat.spawnDisc(12 * UNIT + UNIT / 2.0, 12 * UNIT + UNIT / 2.0, -90, 1);
        double initialSpeed = e.getMoveSpeed();

        double maxSpeed = initialSpeed;
        for (int t = 0; t < 1500; t++) {
            system.update(List.of(e), DiscSystem.NO_OP);
            maxSpeed = Math.max(maxSpeed, e.getMoveSpeed());
            int tileX = (int) (e.getX() / UNIT);
            assertThat(tileX).as("disc must not tunnel past a corridor wall").isBetween(8, 16);
        }

        assertThat(e.getBounces()).isGreaterThan(2);
        assertThat(maxSpeed).as("accelerated past launch speed").isGreaterThan(initialSpeed);
        assertThat(maxSpeed).as("capped at maxSpeedFactor * launch speed")
                .isLessThanOrEqualTo(initialSpeed * 3.0 + 1e-9);
    }

    @Test
    void fastPowerDiscDoesNotTunnelThroughWalls() {
        var disc = new DiscConfig(18, 10, 3.0, 100_000, 3, 3, 4, 1.0, 1.0);
        var power = new PowerShotConfig(true, 0.5, 3.0, 100_000, 2);  // launch speed 9 px/step
        var combat = new DiscSpawner(pool(), disc, power);
        var system = new DiscSystem(tracer(corridorField()), combat);

        Entity e = combat.spawnDisc(12 * UNIT + UNIT / 2.0, 12 * UNIT + UNIT / 2.0, -90, 1, true);
        assertThat(e.getMoveSpeed()).isEqualTo(9.0);

        for (int t = 0; t < 1500; t++) {
            system.update(List.of(e), DiscSystem.NO_OP);
            int tileX = (int) (e.getX() / UNIT);
            assertThat(tileX).as("fast power disc must not tunnel through a wall").isBetween(8, 16);
        }
        assertThat(e.getBounces()).isGreaterThan(5);
    }
}
