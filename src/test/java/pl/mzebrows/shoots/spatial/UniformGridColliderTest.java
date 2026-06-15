// src/test/java/pl/mzebrows/shoots/spatial/UniformGridColliderTest.java
package pl.mzebrows.shoots.spatial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import pl.mzebrows.shoots.config.CollisionConfig;
import pl.mzebrows.shoots.config.GridConfig;
import pl.mzebrows.shoots.entity.BounceMovementStrategy;
import pl.mzebrows.shoots.entity.Entity;
import pl.mzebrows.shoots.entity.MovementStrategy;

/** Deterministic, graphics-free tests for grid queries and disc-bounce reflection. */
class UniformGridColliderTest {

    private static final int UNIT = 36;
    private static final int SIZE = 25; // tiles 0..24, border at 0 and 24
    private static final int TOLERANCE = 4;

    private final GridConfig grid = new GridConfig(UNIT, SIZE);
    private final CollisionConfig collision = new CollisionConfig(TOLERANCE);

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

    private Entity discAt(double x, double y) {
        var e = new Entity();
        e.setX(x);
        e.setY(y);
        e.setDirectionX(1);
        e.setDirectionY(1);
        return e;
    }

    @Test
    void outOfBoundsTileReportsWall() {
        var collider = new UniformGridCollider(emptyField(), grid, collision);

        assertThat(collider.tileAt(-1, 5)).isEqualTo(TileType.WALL);
        assertThat(collider.tileAt(SIZE, 5)).isEqualTo(TileType.WALL);
        assertThat(collider.tileAt(5, 5)).isEqualTo(TileType.EMPTY);
    }

    @Test
    void legacyMatrixMapsValuesToTileTypes() {
        int[][] matrix = new int[SIZE][SIZE];
        matrix[5][5] = 1;
        matrix[6][6] = 2;
        matrix[7][7] = 3;
        var collider = UniformGridCollider.fromLegacyMatrix(matrix, grid, collision);

        assertThat(collider.tileAt(5, 5)).isEqualTo(TileType.WALL);
        assertThat(collider.tileAt(6, 6)).isEqualTo(TileType.CAPTURE_POINT);
        assertThat(collider.tileAt(7, 7)).isEqualTo(TileType.PLAYER_BASE);
        assertThat(collider.tileAt(8, 8)).isEqualTo(TileType.EMPTY);
    }

    @Test
    void outerBorderAlwaysBounces() {
        var collider = new UniformGridCollider(emptyField(), grid, collision);
        var disc = discAt(0.5 * UNIT, 0.5 * UNIT); // tile (0,0) -> border

        var result = collider.resolve(disc);

        assertThat(result.collided()).isTrue();
        assertThat(result.tile()).isEqualTo(TileType.WALL);
        assertThat(disc.getBounces()).isEqualTo(1);
    }

    @Test
    void emptyInteriorTileDoesNotCollide() {
        var collider = new UniformGridCollider(emptyField(), grid, collision);
        var disc = discAt(5 * UNIT + UNIT / 2.0, 5 * UNIT + UNIT / 2.0); // center of tile (5,5)

        var result = collider.resolve(disc);

        assertThat(result.collided()).isFalse();
        assertThat(disc.getBounces()).isZero();
    }

    @Test
    void rightNeighbourWallFlipsDirectionX() {
        var tiles = emptyField();
        tiles[6][5] = TileType.WALL;
        var collider = new UniformGridCollider(tiles, grid, collision);
        // In tile (5,5), restX within tolerance of the right edge -> tests neighbour (6,5).
        var disc = discAt(5 * UNIT + (UNIT - 1), 5 * UNIT + UNIT / 2.0);

        var result = collider.resolve(disc);

        assertThat(result.collided()).isTrue();
        assertThat(disc.getDirectionX()).isEqualTo(-1);
        assertThat(disc.getDirectionY()).isEqualTo(1);
        assertThat(disc.getBounces()).isEqualTo(1);
    }

    @Test
    void bottomNeighbourWallFlipsDirectionY() {
        var tiles = emptyField();
        tiles[5][6] = TileType.WALL;
        var collider = new UniformGridCollider(tiles, grid, collision);
        var disc = discAt(5 * UNIT + UNIT / 2.0, 5 * UNIT + (UNIT - 1));

        var result = collider.resolve(disc);

        assertThat(result.collided()).isTrue();
        assertThat(disc.getDirectionY()).isEqualTo(-1);
        assertThat(disc.getDirectionX()).isEqualTo(1);
    }

    @Test
    void cornerWallFlipsBothAxes() {
        var tiles = emptyField();
        tiles[6][6] = TileType.WALL;
        tiles[6][5] = TileType.WALL;
        tiles[5][6] = TileType.WALL;
        var collider = new UniformGridCollider(tiles, grid, collision);
        var disc = discAt(5 * UNIT + (UNIT - 1), 5 * UNIT + (UNIT - 1));

        collider.resolve(disc);

        assertThat(disc.getDirectionX()).isEqualTo(-1);
        assertThat(disc.getDirectionY()).isEqualTo(-1);
    }

    @Test
    void diagonalOnlyCornerFlipsBothAxesInsteadOfPenetrating() {
        // Bug: a disc heading diagonally straight at a block CORNER -- only the diagonal neighbour
        // is solid, both orthogonal neighbours empty -- matched no branch, kept its diagonal heading,
        // entered the solid tile and bounced around stuck inside. It must now reflect on both axes.
        var tiles = emptyField();
        tiles[6][6] = TileType.WALL; // diagonal block only; (6,5) and (5,6) stay EMPTY
        var collider = new UniformGridCollider(tiles, grid, collision);
        var disc = discAt(5 * UNIT + (UNIT - 1), 5 * UNIT + (UNIT - 1)); // bottom-right band of (5,5)

        var result = collider.resolve(disc);

        assertThat(result.collided()).isTrue();
        assertThat(result.tile()).isEqualTo(TileType.WALL);
        assertThat(disc.getDirectionX()).isEqualTo(-1);
        assertThat(disc.getDirectionY()).isEqualTo(-1);
        assertThat(disc.getBounces()).isEqualTo(1);
    }

    @Test
    void capturePointTileIsReportedWithoutReflection() {
        var tiles = emptyField();
        tiles[5][5] = TileType.CAPTURE_POINT;
        var collider = new UniformGridCollider(tiles, grid, collision);
        var disc = discAt(5 * UNIT + UNIT / 2.0, 5 * UNIT + UNIT / 2.0);

        var result = collider.resolve(disc);

        assertThat(result.collided()).isTrue();
        assertThat(result.tile()).isEqualTo(TileType.CAPTURE_POINT);
        assertThat(disc.getDirectionX()).isEqualTo(1);
        assertThat(disc.getDirectionY()).isEqualTo(1);
    }

    @Test
    void reflectionReversesTravelAfterBounce() {
        MovementStrategy movement = new BounceMovementStrategy();
        var tiles = emptyField();
        tiles[6][5] = TileType.WALL;
        var collider = new UniformGridCollider(tiles, grid, collision);

        // Disc travelling along +X (angle 90 -> sin(-90)=-1, so directionX must be -1 for +X... )
        // Use angle 90 with directionX flips: verify post-bounce X travel reverses sign.
        var disc = discAt(5 * UNIT + (UNIT - 1), 5 * UNIT + UNIT / 2.0);
        disc.setAngle(90);
        disc.setMoveSpeed(2);

        double xBefore = disc.getX();
        movement.move(disc);
        double dxPreBounce = disc.getX() - xBefore;

        // Reset position into the tolerance band and bounce.
        disc.setX(5 * UNIT + (UNIT - 1));
        collider.resolve(disc);

        double xAtBounce = disc.getX();
        movement.move(disc);
        double dxPostBounce = disc.getX() - xAtBounce;

        assertThat(dxPostBounce).isCloseTo(-dxPreBounce, within(1e-9));
    }
}
