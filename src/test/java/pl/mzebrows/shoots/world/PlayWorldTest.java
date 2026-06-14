// src/test/java/pl/mzebrows/shoots/world/PlayWorldTest.java
package pl.mzebrows.shoots.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.mzebrows.shoots.config.CollisionConfig;
import pl.mzebrows.shoots.config.ColorPalette;
import pl.mzebrows.shoots.config.DiscConfig;
import pl.mzebrows.shoots.config.GameConfig;
import pl.mzebrows.shoots.config.GridConfig;
import pl.mzebrows.shoots.config.RgbColor;
import pl.mzebrows.shoots.config.RoundConfig;
import pl.mzebrows.shoots.spatial.TileType;

/** Verifies the headless wiring of aiming, the per-player fire cap, disc retirement, and capture. */
class PlayWorldTest {

    private static final long SEED = 42L;

    private GameConfig config(int players) {
        var palette = new ColorPalette(
                new RgbColor(95, 99, 104, 255), new RgbColor(25, 25, 25, 255),
                new RgbColor(68, 74, 80, 255), new RgbColor(102, 0, 102, 255),
                new RgbColor(102, 75, 102, 255), new RgbColor(192, 192, 192, 255),
                new RgbColor(68, 74, 80, 255), new RgbColor(35, 35, 35, 10),
                java.util.List.of(
                        new RgbColor(124, 252, 0, 255), new RgbColor(48, 213, 200, 255),
                        new RgbColor(252, 3, 0, 255), new RgbColor(237, 26, 116, 255)));
        return new GameConfig(players,
                new GridConfig(36, 25),
                new DiscConfig(18, 10, 2.0, 7),
                new CollisionConfig(4),
                new RoundConfig(15, 2, 2, 1),
                palette);
    }

    private PlayWorld world(int players) {
        return new PlayWorld(config(players), new Random(SEED));
    }

    @Test
    void registersCapturePointsFromGeneratedMap() {
        var world = world(2);
        int capturePointTiles = 0;
        TileType[][] tiles = world.tiles();
        for (TileType[] row : tiles) {
            for (TileType t : row) {
                if (t == TileType.CAPTURE_POINT) {
                    capturePointTiles++;
                }
            }
        }
        assertThat(capturePointTiles).isPositive();
        assertThat(world.scoring().points()).hasSize(capturePointTiles);
    }

    @Test
    void locatesOneBasePerPlayer() {
        var world = world(2);
        assertThat(world.playerCount()).isEqualTo(2);
        assertThat(world.baseOf(0)).isNotNull();
        assertThat(world.baseOf(1)).isNotNull();
        assertThat(world.baseOf(0).shootDirection()).isEqualTo(180);
        assertThat(world.baseOf(1).shootDirection()).isEqualTo(0);
    }

    @Test
    void firingAddsAnActiveDiscUpToTheCap() {
        var world = world(1);
        assertThat(world.totalActiveDiscs()).isZero();

        for (int i = 0; i < 5; i++) {
            world.fire(0);
        }
        // Cap is 3 concurrent discs per player.
        assertThat(world.activeDiscs(0)).isEqualTo(3);
        assertThat(world.discs()).hasSize(3);
    }

    @Test
    void aimInputRotatesTheController() {
        var world = world(1);
        double base = world.aimOf(0).currentAngle();

        world.applyInput(0, PlayWorld.AimInput.RIGHT, false);
        double afterRight = world.aimOf(0).currentAngle();

        assertThat(afterRight).isNotEqualTo(base);
    }

    @Test
    void spentDiscIsRetiredAndFreesItsSlotAfterStepping() {
        var world = world(1);
        world.fire(0);
        assertThat(world.activeDiscs(0)).isEqualTo(1);

        // Force the only disc to its bounce budget, then step: DiscSystem should retire it,
        // the sink frees the player's slot, and the tracked list drops it.
        var disc = world.discs().get(0);
        disc.setBounces(config(1).disc().maxBounces());

        world.step();

        assertThat(world.activeDiscs(0)).isZero();
        assertThat(world.discs()).isEmpty();
    }

    @Test
    void resetRoundClearsDiscsAndCaptureState() {
        var world = world(1);
        world.fire(0);
        world.applyInput(0, PlayWorld.AimInput.LEFT, false);
        assertThat(world.totalActiveDiscs()).isEqualTo(1);

        world.resetRound();

        assertThat(world.totalActiveDiscs()).isZero();
        assertThat(world.discs()).isEmpty();
        assertThat(world.aimOf(0).getRotation()).isZero();
    }

    @Test
    void capturePointHitIsScoredThroughTheSink() {
        var world = world(1);
        // Find a capture point tile and drop a disc onto it, then step.
        TileType[][] tiles = world.tiles();
        int cpX = -1;
        int cpY = -1;
        for (int i = 0; i < tiles.length && cpX < 0; i++) {
            for (int j = 0; j < tiles[i].length; j++) {
                if (tiles[i][j] == TileType.CAPTURE_POINT) {
                    cpX = i;
                    cpY = j;
                    break;
                }
            }
        }
        assertThat(cpX).isGreaterThanOrEqualTo(0);

        world.fire(0);
        var disc = world.discs().get(0);
        int unit = 36;
        // Place the disc squarely inside the capture-point tile and aim so it stays there one step.
        disc.setX(cpX * unit + unit / 2.0);
        disc.setY(cpY * unit + unit / 2.0);
        disc.setAngle(0);
        disc.setMoveSpeed(0); // no movement, so it remains on the capture tile

        world.step();

        assertThat(world.scoring().at(cpX, cpY).isCaptured()).isTrue();
        assertThat(world.scoring().at(cpX, cpY).getOwnerId()).isEqualTo(0);
    }

    @Test
    void stepSyncsRoundScoresFromCaptureState() {
        var world = world(1);
        // Drop a disc on a capture point so player 0 controls it, then step.
        TileType[][] tiles = world.tiles();
        int cpX = -1, cpY = -1;
        for (int i = 0; i < tiles.length && cpX < 0; i++) {
            for (int j = 0; j < tiles[i].length; j++) {
                if (tiles[i][j] == TileType.CAPTURE_POINT) { cpX = i; cpY = j; break; }
            }
        }
        world.fire(0);
        var disc = world.discs().get(0);
        disc.setX(cpX * 36 + 18.0);
        disc.setY(cpY * 36 + 18.0);
        disc.setAngle(0);
        disc.setMoveSpeed(0);

        world.step();

        assertThat(world.matchFlow().scoreOf(0).getCurrentPoints())
                .isEqualTo(world.scoring().pointsFor(0));
    }

    @Test
    void finishRoundAndMatchOverDriveFromTheNewScorer() {
        var world = world(2);
        // roundLimit is 2 in the test config.
        assertThat(world.isMatchOver()).isFalse();
        world.finishRound();
        assertThat(world.isMatchOver()).isFalse();
        world.finishRound();
        assertThat(world.isMatchOver()).isTrue();
        // Winners resolvable without throwing; one of the two players is flagged or tie.
        assertThat(world.resolveMatchWinners()).isNotEmpty();
    }

    @Test
    void resetMatchClearsScoresAndRoundCounter() {
        var world = world(2);
        world.matchFlow().scoreOf(0).setCurrentPoints(4);
        world.finishRound();
        assertThat(world.matchFlow().roundsPlayed()).isEqualTo(1);

        world.resetMatch();

        assertThat(world.matchFlow().roundsPlayed()).isZero();
        assertThat(world.matchFlow().scoreOf(0).getTotalPoints()).isZero();
        assertThat(world.totalActiveDiscs()).isZero();
    }
}
