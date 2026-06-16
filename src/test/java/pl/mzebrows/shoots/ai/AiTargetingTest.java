// src/test/java/pl/mzebrows/shoots/ai/AiTargetingTest.java
package pl.mzebrows.shoots.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.mzebrows.shoots.config.CollisionConfig;
import pl.mzebrows.shoots.config.GridConfig;
import pl.mzebrows.shoots.spatial.TileType;
import pl.mzebrows.shoots.spatial.UniformGridCollider;

class AiTargetingTest {

    private static final int UNIT = 36;
    private static final int SIZE = 9;

    /** Builds a SIZE x SIZE map ringed with walls, otherwise empty. */
    private static TileType[][] emptyWalledMap() {
        TileType[][] t = new TileType[SIZE][SIZE];
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                boolean border = i == 0 || j == 0 || i == SIZE - 1 || j == SIZE - 1;
                t[i][j] = border ? TileType.WALL : TileType.EMPTY;
            }
        }
        return t;
    }

    private static AiTargeting targeting(TileType[][] tiles, int maxBounces) {
        var collider = new UniformGridCollider(tiles, new GridConfig(UNIT, SIZE), new CollisionConfig(4));
        return new AiTargeting(collider, UNIT, maxBounces);
    }

    private static double centreOf(int tile) {
        return tile * UNIT + UNIT / 2.0;
    }

    @Test
    void reachesACapturePointOnADirectHorizontalLine() {
        TileType[][] tiles = emptyWalledMap();
        tiles[6][4] = TileType.CAPTURE_POINT; // to the right of a base at column 2, same row
        var targeting = targeting(tiles, 5);

        // Fire straight right (angle 0 per the disc convention uses sin/cos; angle 0 moves +Y, so use the
        // direction that walks +X). The base disc convention: shootDirection 0 fires +X for player 2.
        // We verify by scanning both axes-aligned angles and asserting the point is reached from one.
        var rightHit = targeting.reach(centreOf(2), centreOf(4), 90, 2.0);
        var leftHit = targeting.reach(centreOf(2), centreOf(4), -90, 2.0);

        assertThat(rightHit.reached() || leftHit.reached()).isTrue();
        var hit = rightHit.reached() ? rightHit : leftHit;
        assertThat(hit.tileX()).isEqualTo(6);
        assertThat(hit.tileY()).isEqualTo(4);
    }

    @Test
    void reportsNoReachWhenNoCapturePointOnThePath() {
        var targeting = targeting(emptyWalledMap(), 3);

        var hit = targeting.reach(centreOf(4), centreOf(4), 0, 2.0);

        assertThat(hit.reached()).isFalse();
        assertThat(hit).isEqualTo(AiTargeting.Reach.NONE);
    }

    @Test
    void recordsBounceCountForAnIndirectPath() {
        // A capture point that can only be reached after at least one wall bounce: place it behind the
        // shooter's straight line so a reflection is required.
        TileType[][] tiles = emptyWalledMap();
        tiles[2][2] = TileType.CAPTURE_POINT;
        var targeting = targeting(tiles, 6);

        boolean anyMultiBounce = false;
        for (int deg = -180; deg < 180; deg += 5) {
            var hit = targeting.reach(centreOf(4), centreOf(4), deg, 2.0);
            if (hit.reached() && hit.bounces() >= 1) {
                anyMultiBounce = true;
                break;
            }
        }
        assertThat(anyMultiBounce).isTrue();
    }

    @Test
    void firstWallTilePacksAValidTileKey() {
        var targeting = targeting(emptyWalledMap(), 4);

        long packed = targeting.firstWallTile(centreOf(4), centreOf(4), 0, 2.0);
        // Decode and check the tile is within the map bounds (it must hit a border wall).
        int tx = (int) (packed >> 32);
        int ty = (int) (packed & 0xFFFFFFFFL);
        assertThat(tx).isBetween(0, SIZE - 1);
        assertThat(ty).isBetween(0, SIZE - 1);
    }
}
