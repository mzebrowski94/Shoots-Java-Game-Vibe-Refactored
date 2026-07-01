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

        assertThat(map[12][23]).isEqualTo(TileType.PLAYER_BASE); // P1 bottom, 1 from border
        assertThat(map[12][1]).isEqualTo(TileType.PLAYER_BASE);  // P2 top, 1 from border
        assertThat(map[1][12]).isEqualTo(TileType.PLAYER_BASE);  // P3 left, 1 from border
        assertThat(map[23][12]).isEqualTo(TileType.PLAYER_BASE); // P4 right, 1 from border
    }

    @Test
    void eachBaseHasAFlankingBlockTwoTilesToEachSide() {
        // Per the intended layout (and the reference picture): a wall sits exactly two tiles to each
        // side of the base centre, perpendicular to its firing direction.
        TileType[][] map = seeded().generate(4);

        // P1 bottom (12,23) and P2 top (12,1) fire vertically -> flanks left/right (x +-2).
        assertThat(map[10][23]).isEqualTo(TileType.WALL);
        assertThat(map[14][23]).isEqualTo(TileType.WALL);
        assertThat(map[10][1]).isEqualTo(TileType.WALL);
        assertThat(map[14][1]).isEqualTo(TileType.WALL);
        // P3 left (1,12) and P4 right (23,12) fire horizontally -> flanks top/bottom (y +-2).
        assertThat(map[1][10]).isEqualTo(TileType.WALL);
        assertThat(map[1][14]).isEqualTo(TileType.WALL);
        assertThat(map[23][10]).isEqualTo(TileType.WALL);
        assertThat(map[23][14]).isEqualTo(TileType.WALL);
    }

    @Test
    void tilesImmediatelyAroundEachBaseAreClearOfBlocks() {
        // The 3x3 ring directly around the base centre must be free so the player can shoot out.
        int[][] centres = {{12, 23}, {12, 1}, {1, 12}, {23, 12}};
        TileType[][] map = seeded().generate(4);
        for (int[] c : centres) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) {
                        continue; // the base tile itself
                    }
                    int x = c[0] + dx;
                    int y = c[1] + dy;
                    if (x == 0 || y == 0 || x == SIZE - 1 || y == SIZE - 1) {
                        continue; // outer border is always wall; bases sit one tile in
                    }
                    assertThat(map[x][y]).isNotEqualTo(TileType.WALL);
                }
            }
        }
    }

    @Test
    void noBlockSitsOnABaseTileAcrossManySeeds() {
        int[][] centres = {{12, 23}, {12, 1}, {1, 12}, {23, 12}};
        for (long seed = 0; seed < 100; seed++) {
            TileType[][] map = new MapGenerator(grid, new Random(seed)).generate(4);
            for (int[] c : centres) {
                assertThat(map[c[0]][c[1]]).isEqualTo(TileType.PLAYER_BASE);
            }
        }
    }

    @Test
    void onlyTheSpawnAreaAheadOfEachBaseIsCleared() {
        // The immediate spawn area (the cleared box, +-2 tiles) directly ahead of the base is free, but
        // the firing path is NOT cleared across the map -- bounce shots off blocks are the core mechanic.
        for (long seed = 0; seed < 100; seed++) {
            TileType[][] map = new MapGenerator(grid, new Random(seed)).generate(2);
            for (int d = 1; d <= 2; d++) {
                assertThat(map[12][23 - d]).isNotEqualTo(TileType.WALL); // P1 fires up, within box
                assertThat(map[12][1 + d]).isNotEqualTo(TileType.WALL);  // P2 fires down, within box
            }
        }
    }

    @Test
    void threeAndFourPlayerMapsPlaceTheExtraBases() {
        // Regression: P3/P4 must actually appear for 3/4-player games (the live world must be built
        // with the selected player count, not the 2 from game.properties).
        TileType[][] three = seeded().generate(3);
        assertThat(three[1][12]).isEqualTo(TileType.PLAYER_BASE);  // P3 left
        assertThat(countTiles(three, TileType.PLAYER_BASE)).isEqualTo(3);

        TileType[][] four = seeded().generate(4);
        assertThat(four[23][12]).isEqualTo(TileType.PLAYER_BASE);  // P4 right
        assertThat(countTiles(four, TileType.PLAYER_BASE)).isEqualTo(4);
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

    @Test
    void centralWallBlocksTheLaneBetweenOpposingBases() {
        // Feature #3: a wall sits at the map centre on every seed so two opposing bases (P1/P2 share
        // column 12, P3/P4 share row 12) can never shoot each other on a straight, unbroken line.
        int c = SIZE / 2;
        for (long seed = 0; seed < 50; seed++) {
            TileType[][] map = new MapGenerator(grid, new Random(seed)).generate(4);
            assertThat(map[c][c]).as("centre tile is a wall (seed %d)", seed).isEqualTo(TileType.WALL);
        }
    }

    @Test
    void diagonalWallsBlockTheLaneBetweenDiagonalBases() {
        // Feature: a fixed wall sits midway on each straight line between DIAGONAL base pairs (one per
        // map quarter) so e.g. P1 (bottom) and P3 (left) can never shoot each other directly.
        int[][] walls = {{6, 7}, {18, 7}, {6, 17}, {18, 17}};
        for (long seed = 0; seed < 50; seed++) {
            TileType[][] map = new MapGenerator(grid, new Random(seed)).generate(4);
            for (int[] w : walls) {
                assertThat(map[w[0]][w[1]]).as("diagonal wall (%d,%d), seed %d", w[0], w[1], seed)
                        .isEqualTo(TileType.WALL);
            }
        }
    }

    @Test
    void diagonalWallCentresLieExactlyOnTheDiagonalShootingLines() {
        // Geometry check (no RNG): each wall tile's centre is collinear with the two base centres it
        // separates, i.e. it really intersects the direct shooting line.
        int[][][] pairs = { // {wall, baseA, baseB}
                {{6, 7}, {12, 1}, {1, 12}},    // P2-P3
                {{18, 7}, {12, 1}, {23, 12}},  // P2-P4
                {{6, 17}, {12, 23}, {1, 12}},  // P1-P3
                {{18, 17}, {12, 23}, {23, 12}} // P1-P4
        };
        for (int[][] p : pairs) {
            double wx = p[0][0] + 0.5, wy = p[0][1] + 0.5;
            double ax = p[1][0] + 0.5, ay = p[1][1] + 0.5;
            double bx = p[2][0] + 0.5, by = p[2][1] + 0.5;
            double cross = (bx - ax) * (wy - ay) - (by - ay) * (wx - ax);
            assertThat(cross).as("wall (%d,%d) collinear with its base pair", p[0][0], p[0][1]).isZero();
        }
    }

    @Test
    void noRandomBlockSitsSideBySideWithADiagonalWall() {
        // Blocks may only touch diagonally, never side by side -- the fixed diagonal walls must obey
        // the same rule, so their four orthogonal neighbours stay wall-free on every seed.
        int[][] walls = {{6, 7}, {18, 7}, {6, 17}, {18, 17}};
        for (long seed = 0; seed < 50; seed++) {
            TileType[][] map = new MapGenerator(grid, new Random(seed)).generate(4);
            for (int[] w : walls) {
                assertThat(map[w[0] - 1][w[1]]).isNotEqualTo(TileType.WALL);
                assertThat(map[w[0] + 1][w[1]]).isNotEqualTo(TileType.WALL);
                assertThat(map[w[0]][w[1] - 1]).isNotEqualTo(TileType.WALL);
                assertThat(map[w[0]][w[1] + 1]).isNotEqualTo(TileType.WALL);
            }
        }
    }

    @Test
    void capturePointsAreEvenlyDistributedAcrossQuarters() {
        // 8 capture points total, exactly 2 per map quarter, on every seed. Placing them LAST in the
        // pipeline also means no base/centre-wall stamp can ever bury one (count would drop below 8).
        for (long seed = 0; seed < 50; seed++) {
            TileType[][] map = new MapGenerator(grid, new Random(seed)).generate(4);
            int[] quarters = new int[4]; // TL, TR, BL, BR (split at tile 12)
            for (int x = 0; x < SIZE; x++) {
                for (int y = 0; y < SIZE; y++) {
                    if (map[x][y] == TileType.CAPTURE_POINT) {
                        quarters[(x < 12 ? 0 : 1) + (y < 12 ? 0 : 2)]++;
                    }
                }
            }
            assertThat(quarters).as("capture points per quarter, seed %d", seed).containsOnly(2);
        }
    }

    @Test
    void rejectsUnsupportedMapSize() {
        assertThatThrownBy(() -> new MapGenerator(new GridConfig(UNIT, 21), new Random(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported map size");
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
