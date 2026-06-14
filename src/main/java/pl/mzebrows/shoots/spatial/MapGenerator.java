// src/main/java/pl/mzebrows/shoots/spatial/MapGenerator.java
package pl.mzebrows.shoots.spatial;

import java.util.Random;
import pl.mzebrows.shoots.config.GridConfig;

/**
 * AWT-free map generator: produces a {@link TileType} grid (border walls, scattered blocks, capture
 * points, and player bases) consumed directly by {@link UniformGridCollider}. Replaces the legacy
 * {@code MapMatrix} int-grid builder and its stray {@code ColisionPoint} allocations.
 *
 * <p>Placement is randomised but seedable: pass a fixed-seed {@link Random} for deterministic tests.
 * Geometry (tile count) comes from {@link GridConfig}; tile semantics from {@link TileType}.
 */
public final class MapGenerator {

    private final GridConfig grid;
    private final Random random;
    private final int size;

    public MapGenerator(GridConfig grid, Random random) {
        this.grid = grid;
        this.random = random;
        this.size = grid.tableSize();
    }

    /** Convenience constructor with a fresh, time-seeded {@link Random}. */
    public MapGenerator(GridConfig grid) {
        this(grid, new Random());
    }

    /**
     * Builds a fresh map for {@code playerNumber} players (1..4). The grid is square per
     * {@link GridConfig}; every call returns a new array, leaving the generator reusable.
     */
    public TileType[][] generate(int playerNumber) {
        if (playerNumber < 1 || playerNumber > 4) {
            throw new IllegalArgumentException("playerNumber must be in [1,4]: " + playerNumber);
        }
        var tiles = new TileType[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                tiles[i][j] = TileType.EMPTY;
            }
        }
        addBorderWalls(tiles);
        addCornerBlocks(tiles);
        addBlocks(tiles);
        addCapturePoints(tiles);
        addPlayerBases(tiles, playerNumber);
        return tiles;
    }

    private void addBorderWalls(TileType[][] tiles) {
        for (int i = 0; i < size; i++) {
            tiles[i][0] = TileType.WALL;
            tiles[0][i] = TileType.WALL;
            tiles[size - 1][i] = TileType.WALL;
            tiles[i][size - 1] = TileType.WALL;
        }
    }

    private void addCornerBlocks(TileType[][] tiles) {
        tiles[1][1] = TileType.WALL;
        tiles[size - 2][1] = TileType.WALL;
        tiles[1][size - 2] = TileType.WALL;
        tiles[size - 2][size - 2] = TileType.WALL;
    }

    private void addBlocks(TileType[][] tiles) {
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                insertBlock(tiles, i, j);
            }
        }
    }

    private void addCapturePoints(TileType[][] tiles) {
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                if (isCapturePointCell(i, j)) {
                    insertCapturePoint(tiles, i, j);
                }
            }
        }
    }

    /** Mirrors the legacy point-field mask: the four edge-midpoints of the 4x4 macro-grid are skipped. */
    private boolean isCapturePointCell(int i, int j) {
        return !((i == 0 && j == 1) || (i == 0 && j == 2)
                || (i == 1 && j == 0) || (i == 1 && j == 3)
                || (i == 2 && j == 0) || (i == 2 && j == 3)
                || (i == 3 && j == 1) || (i == 3 && j == 2));
    }

    private void insertBlock(TileType[][] tiles, int macroX, int macroY) {
        while (true) {
            int x = macroX * 3 + random.nextInt(3);
            int y = macroY * 3 + random.nextInt(3);
            if (!touchesWall(tiles, x, y) && x != 0 && x != size && y != 0 && y != size - 1) {
                tiles[x][y] = TileType.WALL;
                return;
            }
        }
    }

    private void insertCapturePoint(TileType[][] tiles, int macroX, int macroY) {
        while (true) {
            int x = macroX * 6 + random.nextInt(6);
            int y = macroY * 6 + random.nextInt(6);
            if (tiles[x][y] == TileType.EMPTY && touchesWallInterior(tiles, x, y)) {
                tiles[x][y] = TileType.CAPTURE_POINT;
                return;
            }
        }
    }

    private boolean touchesWall(TileType[][] tiles, int x, int y) {
        return (x != 0 && tiles[x - 1][y] == TileType.WALL)
                || (x != size - 1 && tiles[x + 1][y] == TileType.WALL)
                || (y != 0 && tiles[x][y - 1] == TileType.WALL)
                || (y != size - 1 && tiles[x][y + 1] == TileType.WALL);
    }

    private boolean touchesWallInterior(TileType[][] tiles, int x, int y) {
        return (x != 0 && tiles[x - 1][y] == TileType.WALL && (x - 1) != 0)
                || (x != size - 1 && tiles[x + 1][y] == TileType.WALL && (x + 1) != (size - 1))
                || (y != 0 && tiles[x][y - 1] == TileType.WALL && (y - 1) != 0)
                || (y != size - 1 && tiles[x][y + 1] == TileType.WALL && (y + 1) != (size - 1));
    }

    private void addPlayerBases(TileType[][] tiles, int playerNumber) {
        if (playerNumber >= 1) {
            carve(tiles, 9, 21, 6, 3);
            tiles[9][23] = TileType.WALL;
            tiles[14][23] = TileType.WALL;
            tiles[12][23] = TileType.PLAYER_BASE;
        }
        if (playerNumber >= 2) {
            carve(tiles, 9, 1, 6, 3);
            tiles[9][1] = TileType.WALL;
            tiles[14][1] = TileType.WALL;
            tiles[12][3] = TileType.PLAYER_BASE;
        }
        if (playerNumber >= 3) {
            carve(tiles, 1, 9, 3, 6);
            tiles[1][9] = TileType.WALL;
            tiles[1][14] = TileType.WALL;
            tiles[3][12] = TileType.PLAYER_BASE;
        }
        if (playerNumber >= 4) {
            carve(tiles, 21, 9, 3, 6);
            tiles[23][9] = TileType.WALL;
            tiles[23][14] = TileType.WALL;
            tiles[23][12] = TileType.PLAYER_BASE;
        }
    }

    private void carve(TileType[][] tiles, int x, int y, int height, int width) {
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                tiles[x + i][y + j] = TileType.EMPTY;
            }
        }
    }
}
