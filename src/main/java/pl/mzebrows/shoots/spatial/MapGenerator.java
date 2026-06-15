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

    /**
     * Tiles, by first index, where each player base centre sits: P1 bottom, P2 top, P3 left, P4 right.
     * Indexing is {@code tiles[centreX][centreY]}. Bases are placed AFTER blocks so the carve below
     * removes any random block that landed on the base, its two flanking blocks, or its firing lane.
     */
    private static final int[][] BASE_CENTRES = {
            {12, 23}, // P1 bottom, fires up   (1 tile from the bottom border, row 24)
            {12, 1},  // P2 top, fires down    (1 tile from the top border, row 0)
            {1, 12},  // P3 left, fires right  (1 tile from the left border, col 0)
            {23, 12}, // P4 right, fires left  (1 tile from the right border, col 24)
    };

    private void addPlayerBases(TileType[][] tiles, int playerNumber) {
        // Two passes so overlapping lanes (P1/P2 share column 12, P3/P4 share row 12) cannot clear
        // another base or its flanks: first carve every base box + forward firing lane, THEN stamp the
        // bases and flanking blocks.
        for (int p = 0; p < playerNumber; p++) {
            carveBaseArea(tiles, BASE_CENTRES[p][0], BASE_CENTRES[p][1], p >= 2);
        }
        for (int p = 0; p < playerNumber; p++) {
            stampBase(tiles, BASE_CENTRES[p][0], BASE_CENTRES[p][1], p >= 2);
        }
    }

    /**
     * Clears only the small box around the base spawn so no stray block traps the player. The firing
     * lane is intentionally NOT cleared across the map: indirect bounce shots off blocks are the core
     * mechanic, so the path ahead of the base must keep its blocks. {@code horizontalLane} is unused
     * now but kept for symmetry with {@link #stampBase}.
     */
    private void carveBaseArea(TileType[][] tiles, int cx, int cy, boolean horizontalLane) {
        clearBox(tiles, cx, cy);
    }

    /** Distance (tiles) from base centre to its flanking blocks. */
    private static final int FLANK_OFFSET = 2;

    /** Stamps the base tile and a flanking block two tiles to each side, perpendicular to the lane. */
    private void stampBase(TileType[][] tiles, int cx, int cy, boolean horizontalLane) {
        tiles[cx][cy] = TileType.PLAYER_BASE;
        if (horizontalLane) {
            setWallIfInBounds(tiles, cx, cy - FLANK_OFFSET);
            setWallIfInBounds(tiles, cx, cy + FLANK_OFFSET);
        } else {
            setWallIfInBounds(tiles, cx - FLANK_OFFSET, cy);
            setWallIfInBounds(tiles, cx + FLANK_OFFSET, cy);
        }
    }

    private void clearBox(TileType[][] tiles, int cx, int cy) {
        for (int x = cx - 2; x <= cx + 2; x++) {
            for (int y = cy - 2; y <= cy + 2; y++) {
                clearIfInBounds(tiles, x, y);
            }
        }
    }

    private void clearIfInBounds(TileType[][] tiles, int x, int y) {
        if (x > 0 && y > 0 && x < size - 1 && y < size - 1) {
            tiles[x][y] = TileType.EMPTY;
        }
    }

    private void setWallIfInBounds(TileType[][] tiles, int x, int y) {
        if (x > 0 && y > 0 && x < size - 1 && y < size - 1) {
            tiles[x][y] = TileType.WALL;
        }
    }
}
