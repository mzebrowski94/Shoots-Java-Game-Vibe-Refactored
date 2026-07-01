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

    private final Random random;
    private final int size;

    public MapGenerator(GridConfig grid, Random random) {
        this.random = random;
        this.size = MapSize.fromTableSize(grid.tableSize()).tableSize();
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
        addDiagonalWalls(tiles);
        addBlocks(tiles);
        addPlayerBases(tiles, playerNumber);
        addCenterWall(tiles);
        addCapturePoints(tiles); // LAST: no later step may bury or erase a capture point
        return tiles;
    }

    /**
     * Stamps a wall block at the map centre so two opposing bases can never shoot each other on a
     * straight, unbroken line of sight (a problem on maps where the central lane stayed open). Applied
     * after all random block placement so it overrides whatever tile landed there; capture points are
     * placed later still, and since they require an EMPTY tile they can never be buried by it. A base
     * tile is never overwritten -- bases sit at the borders, far from the centre. The block size is a
     * single tile today but is expressed via {@link #CENTER_WALL_HALF} so a larger central obstacle is a
     * one-line change should a future map want one.
     */
    private void addCenterWall(TileType[][] tiles) {
        int cx = size / 2;
        int cy = size / 2;
        for (int x = cx - CENTER_WALL_HALF; x <= cx + CENTER_WALL_HALF; x++) {
            for (int y = cy - CENTER_WALL_HALF; y <= cy + CENTER_WALL_HALF; y++) {
                if (x > 0 && y > 0 && x < size - 1 && y < size - 1 && tiles[x][y] != TileType.PLAYER_BASE) {
                    tiles[x][y] = TileType.WALL;
                }
            }
        }
    }

    /** Half-extent (in tiles) of the central wall block: {@code 0} = a single tile, {@code 1} = 3x3, etc. */
    private static final int CENTER_WALL_HALF = 0;

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

    /** Tile extent of one block macro-cell: blocks are placed one per 3x3 cell of an 8x8 macro grid. */
    private static final int BLOCK_MACRO = 3;

    /**
     * Fixed wall tiles that break the straight line of sight between DIAGONAL base pairs (e.g. P1
     * bottom vs P3 left), mirroring what {@link #addCenterWall} does for opposing pairs. One per map
     * quarter, midway along the base-to-base line. Tile choice (authored for {@link MapSize#NORMAL}):
     * each diagonal line between base centres crosses these tiles exactly corner-to-corner, so any
     * disc (positive radius) flying that line hits them. Placed BEFORE the random blocks so the
     * {@link #touchesWall} rule keeps random blocks off their sides (diagonal contact only), and their
     * macro-cells are skipped in {@link #addBlocks} so the total block count stays unchanged.
     */
    private static final int[][] DIAGONAL_WALLS = {
            {6, 7},   // between P2 (top)    and P3 (left)
            {18, 7},  // between P2 (top)    and P4 (right)
            {6, 17},  // between P1 (bottom) and P3 (left)
            {18, 17}, // between P1 (bottom) and P4 (right)
    };

    private void addDiagonalWalls(TileType[][] tiles) {
        for (int[] wall : DIAGONAL_WALLS) {
            tiles[wall[0]][wall[1]] = TileType.WALL;
        }
    }

    /** True when the given block macro-cell already holds one of the fixed {@link #DIAGONAL_WALLS}. */
    private static boolean holdsDiagonalWall(int macroX, int macroY) {
        for (int[] wall : DIAGONAL_WALLS) {
            if (wall[0] / BLOCK_MACRO == macroX && wall[1] / BLOCK_MACRO == macroY) {
                return true;
            }
        }
        return false;
    }

    /**
     * One block per 3x3 macro-cell of an 8x8 macro grid, so blocks are evenly distributed: each map
     * quarter gets exactly 16 (4x4 macro-cells), one of which is its fixed diagonal wall -- those
     * cells are skipped here to keep the total block count unchanged.
     */
    private void addBlocks(TileType[][] tiles) {
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (!holdsDiagonalWall(i, j)) {
                    insertBlock(tiles, i, j);
                }
            }
        }
    }

    /**
     * One capture point per unmasked 6x6 macro-cell of a 4x4 macro grid: the mask keeps 8 cells, 2 in
     * each map quarter, so capture points are evenly distributed. Runs LAST in {@link #generate} so no
     * wall, base, or centre-wall stamp can bury or erase one (it also only ever claims EMPTY tiles).
     */
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
            int x = macroX * BLOCK_MACRO + random.nextInt(BLOCK_MACRO);
            int y = macroY * BLOCK_MACRO + random.nextInt(BLOCK_MACRO);
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
            {12, 23}, // P1 bottom, fires up   (1 tile from the bottom border so the base touches it)
            {12, 1},  // P2 top, fires down    (1 tile from the top border)
            {1, 12},  // P3 left, fires right  (1 tile from the left border)
            {23, 12}, // P4 right, fires left  (1 tile from the right border)
    };

    /**
     * Canonical base-centre tile for a 0-based {@code playerId}, independent of player count:
     * P0 bottom, P1 top, P2 left, P3 right. Returned as {@code [tileX, tileY]}. The renderer and
     * {@code PlayWorld} use this so a given player ALWAYS spawns on the same side (and aims at centre)
     * regardless of how many players are in the match.
     */
    public static int[] baseCentre(int playerId) {
        int[] c = BASE_CENTRES[playerId];
        return new int[]{c[0], c[1]};
    }

    private void addPlayerBases(TileType[][] tiles, int playerNumber) {
        // Two passes so overlapping lanes (P1/P2 share column 12, P3/P4 share row 12) cannot clear
        // another base or its flanks: first carve every base box + forward firing lane, THEN stamp the
        // bases and flanking blocks.
        for (int p = 0; p < playerNumber; p++) {
            carveBaseArea(tiles, BASE_CENTRES[p][0], BASE_CENTRES[p][1]);
        }
        for (int p = 0; p < playerNumber; p++) {
            stampBase(tiles, BASE_CENTRES[p][0], BASE_CENTRES[p][1], p >= 2);
        }
    }

    /**
     * Clears only the small box around the base spawn so no stray block traps the player. The firing
     * lane is intentionally NOT cleared across the map: indirect bounce shots off blocks are the core
     * mechanic, so the path ahead of the base must keep its blocks.
     */
    private void carveBaseArea(TileType[][] tiles, int cx, int cy) {
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
