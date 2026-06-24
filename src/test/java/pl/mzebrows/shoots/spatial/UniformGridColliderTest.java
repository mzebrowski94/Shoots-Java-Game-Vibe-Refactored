// src/test/java/pl/mzebrows/shoots/spatial/UniformGridColliderTest.java
package pl.mzebrows.shoots.spatial;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.mzebrows.shoots.config.CollisionConfig;
import pl.mzebrows.shoots.config.GridConfig;

/** Tile-content queries for the uniform grid (reflection geometry lives in {@link GridPathTracer}). */
class UniformGridColliderTest {

    private static final int UNIT = 36;
    private static final int SIZE = 25; // tiles 0..24, border at 0 and 24

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

    @Test
    void outOfBoundsTileReportsWall() {
        var collider = new UniformGridCollider(emptyField(), grid, collision);

        assertThat(collider.tileAt(-1, 5)).isEqualTo(TileType.WALL);
        assertThat(collider.tileAt(SIZE, 5)).isEqualTo(TileType.WALL);
        assertThat(collider.tileAt(5, -1)).isEqualTo(TileType.WALL);
        assertThat(collider.tileAt(5, SIZE)).isEqualTo(TileType.WALL);
        assertThat(collider.tileAt(5, 5)).isEqualTo(TileType.EMPTY);
    }

    @Test
    void bordersAreWallAndInteriorIsEmpty() {
        var collider = new UniformGridCollider(emptyField(), grid, collision);

        assertThat(collider.tileAt(0, 0)).isEqualTo(TileType.WALL);
        assertThat(collider.tileAt(SIZE - 1, SIZE - 1)).isEqualTo(TileType.WALL);
        assertThat(collider.tileAt(12, 12)).isEqualTo(TileType.EMPTY);
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
}
