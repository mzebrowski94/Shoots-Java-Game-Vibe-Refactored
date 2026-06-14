// src/test/java/pl/mzebrows/shoots/entity/LaserPredictorTest.java
package pl.mzebrows.shoots.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.mzebrows.shoots.config.CollisionConfig;
import pl.mzebrows.shoots.config.GridConfig;
import pl.mzebrows.shoots.spatial.TileType;
import pl.mzebrows.shoots.spatial.UniformGridCollider;

/** Graphics-free tests that laser prediction reuses the collider's reflection math. */
class LaserPredictorTest {

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

    private LaserPredictor predictor(TileType[][] tiles) {
        var collider = new UniformGridCollider(tiles, grid, collision);
        return new LaserPredictor(collider, new BounceMovementStrategy());
    }

    @Test
    void firstPointIsOrigin() {
        var predictor = predictor(emptyField());
        int[] xs = new int[4];
        int[] ys = new int[4];

        predictor.predict(12 * UNIT, 12 * UNIT, 0, 2, xs, ys);

        assertThat(xs[0]).isEqualTo(12 * UNIT);
        assertThat(ys[0]).isEqualTo(12 * UNIT);
    }

    @Test
    void straightDownShotReflectsOffBottomBorder() {
        // angle 0 -> moves in +Y (cos(0)=1). Centre column, should bounce off bottom wall.
        var predictor = predictor(emptyField());
        int[] xs = new int[2];
        int[] ys = new int[2];

        int written = predictor.predict(12 * UNIT + UNIT / 2.0, 12 * UNIT, 0, 2, xs, ys);

        assertThat(written).isEqualTo(2);
        // X stays in the same column; Y advances downward to near the bottom border.
        assertThat(xs[1]).isEqualTo(xs[0]);
        assertThat(ys[1]).isGreaterThan(ys[0]);
        assertThat(ys[1]).isGreaterThanOrEqualTo((SIZE - 1) * UNIT - UNIT);
    }

    @Test
    void reflectionPointsAreDeterministic() {
        var tiles = emptyField();
        var predictor = predictor(tiles);
        int[] xs1 = new int[4];
        int[] ys1 = new int[4];
        int[] xs2 = new int[4];
        int[] ys2 = new int[4];

        predictor.predict(10 * UNIT, 10 * UNIT, 33, 2, xs1, ys1);
        predictor.predict(10 * UNIT, 10 * UNIT, 33, 2, xs2, ys2);

        assertThat(xs2).containsExactly(xs1[0], xs1[1], xs1[2], xs1[3]);
        assertThat(ys2).containsExactly(ys1[0], ys1[1], ys1[2], ys1[3]);
    }

    @Test
    void multipleReflectionPointsAreProduced() {
        var predictor = predictor(emptyField());
        int[] xs = new int[4];
        int[] ys = new int[4];

        predictor.predict(12 * UNIT, 12 * UNIT, 45, 2, xs, ys);

        // Each successive bounce point should differ from the previous (path keeps reflecting).
        assertThat(xs[1] != xs[0] || ys[1] != ys[0]).isTrue();
        assertThat(xs[2] != xs[1] || ys[2] != ys[1]).isTrue();
    }

    @Test
    void zeroLengthOutputWritesNothing() {
        var predictor = predictor(emptyField());
        assertThat(predictor.predict(0, 0, 0, 2, new int[0], new int[0])).isZero();
    }
}
