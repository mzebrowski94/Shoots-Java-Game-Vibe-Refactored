// src/test/java/pl/mzebrows/shoots/spatial/MapGeneratorTest.java
package pl.mzebrows.shoots.spatial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.mzebrows.shoots.config.GridConfig;

/** Deterministic, graphics-free tests for map generation geometry and seed stability. */
class MapGeneratorTest {

    private static final int UNIT = 36;
    private static final int SIZE = 25;
    private final GridConfig grid = new GridConfig(UNIT, SIZE);

    private MapGenerator seeded() {
        return new MapGenerator(grid, new Random(42));
    }

    @Test
    void gridIsSquareAndFullySized() {
        TileType[][] map = seeded().generate(2);

        assertThat(map).hasDimensions(SIZE, SIZE);
    }

    @Test
    void outerBorderIsAllWalls() {
        TileType[][] map = seeded().generate(2);

        for (int i = 0; i < SIZE; i++) {
            assertThat(map[i][0]).isEqualTo(TileType.WALL);
            assertThat(map[0][i]).isEqualTo(TileType.WALL);
            assertThat(map[SIZE - 1][i]).isEqualTo(TileType.WALL);
            assertThat(map[i][SIZE - 1]).isEqualTo(TileType.WALL);
        }
    }

    @Test
    void placesExpectedPlayerBasesPerPlayerCount() {
        assertThat(countTiles(seeded().generate(1), TileType.PLAYER_BASE)).isEqualTo(1);
        assertThat(countTiles(seeded().generate(2), TileType.PLAYER_BASE)).isEqualTo(2);
        assertThat(countTiles(seeded().generate(3), TileType.PLAYER_BASE)).isEqualTo(3);
        assertThat(countTiles(seeded().generate(4), TileType.PLAYER_BASE)).isEqualTo(4);
    }

    @Test
    void basesSitAtTheirFixedTiles() {
        TileType[][] map = seeded().generate(4);

        assertThat(map[12][23]).isEqualTo(TileType.PLAYER_BASE);
        assertThat(map[12][3]).isEqualTo(TileType.PLAYER_BASE);
        assertThat(map[3][12]).isEqualTo(TileType.PLAYER_BASE);
        assertThat(map[23][12]).isEqualTo(TileType.PLAYER_BASE);
    }

    @Test
    void producesCapturePoints() {
        assertThat(countTiles(seeded().generate(2), TileType.CAPTURE_POINT)).isGreaterThan(0);
    }

    @Test
    void sameSeedProducesIdenticalMap() {
        TileType[][] a = new MapGenerator(grid, new Random(7)).generate(4);
        TileType[][] b = new MapGenerator(grid, new Random(7)).generate(4);

        assertThat(b).isDeepEqualTo(a);
    }

    @Test
    void generateIsRepeatableOnSameInstance() {
        var gen = new MapGenerator(grid, new Random(1));
        gen.generate(2);
        TileType[][] second = gen.generate(2); // must not throw / corrupt despite shared Random
        assertThat(second).hasDimensions(SIZE, SIZE);
    }

    @Test
    void rejectsInvalidPlayerCount() {
        assertThatThrownBy(() -> seeded().generate(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> seeded().generate(5)).isInstanceOf(IllegalArgumentException.class);
    }

    private int countTiles(TileType[][] map, TileType type) {
        int count = 0;
        for (TileType[] row : map) {
            for (TileType tile : row) {
                if (tile == type) {
                    count++;
                }
            }
        }
        return count;
    }
}
