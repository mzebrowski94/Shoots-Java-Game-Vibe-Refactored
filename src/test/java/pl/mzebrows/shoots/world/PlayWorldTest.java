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
        return new GameConfig(players, SEED,
                new GridConfig(36, 25),
                new DiscConfig(18, 10, 2.0, 7, 3, 3, 4),
                new CollisionConfig(4),
                new RoundConfig(15, 2, 2, 1),
                palette,
                new pl.mzebrows.shoots.config.AiConfig(24, 4, true));
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
    void fourPlayerWorldHasFourDistinctBases() {
        var world = world(4);
        assertThat(world.playerCount()).isEqualTo(4);
        for (int p = 0; p < 4; p++) {
            assertThat(world.baseOf(p)).isNotNull();
        }
        // Each player can fire its own disc -> P3/P4 actually spawn, not just P1/P2.
        for (int p = 0; p < 4; p++) {
            assertThat(world.fire(p)).isTrue();
            assertThat(world.activeDiscs(p)).isEqualTo(1);
        }
    }

    @Test
    void baseSpawnPixelIsTheCentreOfTheBaseTile() {
        // The disc/laser origin (pixelX/pixelY) must be the CENTRE of the base tile so it lines up with
        // the drawn base rings. Regression for the earlier X/Y swap that spawned discs at the wrong tile.
        var world = world(2);
        int unit = world.unit();
        for (int p = 0; p < world.playerCount(); p++) {
            var base = world.baseOf(p);
            assertThat(base.pixelX()).isEqualTo(base.tileX() * unit + unit / 2);
            assertThat(base.pixelY()).isEqualTo(base.tileY() * unit + unit / 2);
        }
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
        var disc = world.discs().getFirst();
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
    void wallBounceRegistersABlockHitFlash() {
        var world = world(1);
        assertThat(world.blockHits()).isEmpty();

        // Drive a disc into the outer border so the collider reports a WALL hit. Heading -X with the
        // disc near the left edge, one step pushes its tile index to 0 (the border), which the
        // collider treats as a wall bounce -> onWallHit -> a block-hit flash is registered.
        world.fire(0);
        var disc = world.discs().getFirst();
        int unit = world.unit();
        disc.setX(1.2 * unit);          // inside tile column 1, near the left border (column 0)
        disc.setY(12 * unit + unit / 2.0);
        disc.setAngle(90);              // disc step: X = dirX*speed*sin(-90) = -speed -> -X
        disc.setDirectionX(1);
        disc.setDirectionY(1);
        disc.setMoveSpeed(2 * unit);    // one step crosses into the border tile (index <= 0)

        world.step();

        assertThat(world.blockHits()).isNotEmpty();
        assertThat(world.blockHits().getFirst().ownerId()).isEqualTo(0);
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
        var disc = world.discs().getFirst();
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
        var disc = world.discs().getFirst();
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

    @Test
    void exposesRenderHelpersForTheRenderLayer() {
        var world = world(2);
        assertThat(world.unit()).isEqualTo(36);
        assertThat(world.config()).isNotNull();
        // Player colours come from the configured palette (0-based id -> 1-based palette entry).
        assertThat(world.playerColor(0)).isEqualTo(new java.awt.Color(124, 252, 0, 255));
        assertThat(world.playerColor(1)).isEqualTo(new java.awt.Color(48, 213, 200, 255));
    }

    @Test
    void sameSeedProducesIdenticalMaps() {
        var config = config(2);
        var a = new PlayWorld(config, 7L);
        var b = new PlayWorld(config, 7L);

        TileType[][] ta = a.tiles();
        TileType[][] tb = b.tiles();
        assertThat(ta.length).isEqualTo(tb.length);
        for (int i = 0; i < ta.length; i++) {
            assertThat(ta[i]).containsExactly(tb[i]);
        }
    }

    @Test
    void differentSeedsProduceDifferentMaps() {
        var config = config(2);
        TileType[][] a = new PlayWorld(config, 1L).tiles();
        TileType[][] b = new PlayWorld(config, 2L).tiles();

        boolean anyDifference = false;
        outer:
        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < a[i].length; j++) {
                if (a[i][j] != b[i][j]) {
                    anyDifference = true;
                    break outer;
                }
            }
        }
        assertThat(anyDifference).isTrue();
    }

    @Test
    void seedConstructorRecordsTheSeed() {
        assertThat(new PlayWorld(config(2), 99L).seed()).isEqualTo(99L);
    }

    @Test
    void aimArcSpansTheFullLegacyRangeEachWay() {
        var world = new PlayWorld(config(2), SEED);
        double base = world.aimOf(0).getShootDirection();

        // Rotate hard left for many ticks: should reach base + 110 (the legacy limit), not base + 90.
        for (int i = 0; i < 500; i++) {
            world.applyInput(0, PlayWorld.AimInput.LEFT, false);
        }
        assertThat(world.aimOf(0).currentAngle()).isEqualTo(base + 110.0);

        world.aimOf(0).reset();
        for (int i = 0; i < 500; i++) {
            world.applyInput(0, PlayWorld.AimInput.RIGHT, false);
        }
        assertThat(world.aimOf(0).currentAngle()).isEqualTo(base - 110.0);
    }

    @Test
    void playerSpawnSideIsIndependentOfPlayerCount() {
        // P0 must be bottom (12,23), P1 top (12,1), P2 left (1,12), P3 right (23,12) for ANY player count.
        for (int players = 1; players <= 4; players++) {
            var world = new PlayWorld(config(players), SEED);
            for (int p = 0; p < players; p++) {
                var base = world.baseOf(p);
                int[] expected = pl.mzebrows.shoots.spatial.MapGenerator.baseCentre(p);
                assertThat(base.tileX()).as("player %d tileX (count=%d)", p, players).isEqualTo(expected[0]);
                assertThat(base.tileY()).as("player %d tileY (count=%d)", p, players).isEqualTo(expected[1]);
            }
        }
    }

    @Test
    void p0AlwaysBottomP1TopP2LeftP3Right() {
        var world = new PlayWorld(config(4), SEED);
        // bottom = largest Y, top = smallest Y, left = smallest X, right = largest X.
        assertThat(world.baseOf(0).tileY()).isEqualTo(23); // bottom
        assertThat(world.baseOf(1).tileY()).isEqualTo(1);  // top
        assertThat(world.baseOf(2).tileX()).isEqualTo(1);  // left
        assertThat(world.baseOf(3).tileX()).isEqualTo(23); // right
    }

    @Test
    void neutralAimPointsTowardTheMapCentreForEveryBase() {
        var world = new PlayWorld(config(4), SEED);
        int unit = world.unit();
        double centreX = (world.tiles().length * unit) / 2.0;
        double centreY = (world.tiles()[0].length * unit) / 2.0;

        for (int p = 0; p < 4; p++) {
            var base = world.baseOf(p);
            double angle = Math.toRadians(-world.aimOf(p).currentAngle());
            double dirX = Math.sin(angle);
            double dirY = Math.cos(angle);
            // Vector from base toward map centre.
            double toCx = centreX - base.pixelX();
            double toCy = centreY - base.pixelY();
            double len = Math.hypot(toCx, toCy);
            // The neutral firing direction should align with the base->centre direction (dot product > 0,
            // and strongly so since both are axis-aligned here).
            double dot = (dirX * toCx + dirY * toCy) / len;
            assertThat(dot).as("player %d aims toward centre", p).isGreaterThan(0.9);
        }
    }

    @Test
    void aimDirectionIsIndependentOfPlayerCount() {
        // The same player id keeps the same neutral shoot angle whether 2 or 4 players are present.
        var two = new PlayWorld(config(2), SEED);
        var four = new PlayWorld(config(4), SEED);
        for (int p = 0; p < 2; p++) {
            assertThat(two.aimOf(p).currentAngle()).isEqualTo(four.aimOf(p).currentAngle());
            assertThat(two.baseOf(p).shootDirection()).isEqualTo(four.baseOf(p).shootDirection());
        }
    }

    private static TileType[][] copyTiles(PlayWorld world) {
        TileType[][] src = world.tiles();
        TileType[][] out = new TileType[src.length][];
        for (int i = 0; i < src.length; i++) {
            out[i] = src[i].clone();
        }
        return out;
    }

    private static boolean sameTiles(TileType[][] a, TileType[][] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < a[i].length; j++) {
                if (a[i][j] != b[i][j]) {
                    return false;
                }
            }
        }
        return true;
    }

    @Test
    void eachRoundRegeneratesADifferentMap() {
        var world = new PlayWorld(config(2), SEED);

        world.resetRound();
        TileType[][] round1 = copyTiles(world);
        world.resetRound();
        TileType[][] round2 = copyTiles(world);
        world.resetRound();
        TileType[][] round3 = copyTiles(world);

        assertThat(sameTiles(round1, round2)).as("round1 vs round2").isFalse();
        assertThat(sameTiles(round2, round3)).as("round2 vs round3").isFalse();
    }

    @Test
    void roundMapSequenceIsReproducibleFromTheMasterSeed() {
        var a = new PlayWorld(config(2), SEED);
        var b = new PlayWorld(config(2), SEED);

        for (int r = 0; r < 4; r++) {
            a.resetRound();
            b.resetRound();
            assertThat(sameTiles(copyTiles(a), copyTiles(b)))
                    .as("round %d identical for same seed", r).isTrue();
        }
    }

    @Test
    void differentMasterSeedsGiveDifferentRoundSequences() {
        var a = new PlayWorld(config(2), 1L);
        var b = new PlayWorld(config(2), 2L);
        a.resetRound();
        b.resetRound();
        assertThat(sameTiles(copyTiles(a), copyTiles(b))).isFalse();
    }

    @Test
    void resetMatchRestartsTheRoundMapSequence() {
        var world = new PlayWorld(config(2), SEED);
        world.resetRound();
        TileType[][] firstRoundMap = copyTiles(world);
        world.resetRound();
        world.resetRound();

        // resetMatch() restarts the sequence AND performs the first round's resetRound() internally,
        // so the world's current map should already match the original first round.
        world.resetMatch();
        assertThat(sameTiles(copyTiles(world), firstRoundMap))
                .as("first round map after resetMatch matches the original first round").isTrue();
    }

    @Test
    void capturePointsAndBasesStayValidAfterRegeneration() {
        var world = new PlayWorld(config(4), SEED);
        for (int r = 0; r < 3; r++) {
            world.resetRound();
            // Bases still at their canonical tiles and the scoring registry is non-empty.
            for (int p = 0; p < 4; p++) {
                int[] expected = pl.mzebrows.shoots.spatial.MapGenerator.baseCentre(p);
                assertThat(world.baseOf(p).tileX()).isEqualTo(expected[0]);
                assertThat(world.baseOf(p).tileY()).isEqualTo(expected[1]);
            }
            assertThat(world.scoring().points()).isNotEmpty();
        }
    }
}
