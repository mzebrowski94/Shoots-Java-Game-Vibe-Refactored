// src/test/java/pl/mzebrows/shoots/world/PlayWorldDeterminismTest.java
package pl.mzebrows.shoots.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.shoots.config.AiConfig;
import pl.mzebrows.shoots.config.CollisionConfig;
import pl.mzebrows.shoots.config.ColorPalette;
import pl.mzebrows.shoots.config.DiscConfig;
import pl.mzebrows.shoots.config.GameConfig;
import pl.mzebrows.shoots.config.GridConfig;
import pl.mzebrows.shoots.config.RgbColor;
import pl.mzebrows.shoots.config.RoundConfig;
import pl.mzebrows.shoots.entity.Entity;
import pl.mzebrows.shoots.score.CapturePoint;

/**
 * The lockstep invariant for online play (see {@code OnlineMode.md}, cluster F): two {@link PlayWorld}s
 * built from the SAME seed and fed an IDENTICAL input stream must stay bit-identical tick for tick.
 * This is the foundation the input-sync netcode relies on -- if it ever fails, lockstep peers would
 * desync. The scripted input is a pure function of (player, tick) so there is no hidden randomness.
 */
class PlayWorldDeterminismTest {

    private static final long SEED = 42L;
    private static final int PLAYERS = 2;
    private static final int TICKS = 600; // 5 s at 120 steps/s

    private GameConfig config() {
        var palette = new ColorPalette(
                new RgbColor(95, 99, 104, 255), new RgbColor(25, 25, 25, 255),
                new RgbColor(68, 74, 80, 255), new RgbColor(102, 0, 102, 255),
                new RgbColor(102, 75, 102, 255), new RgbColor(192, 192, 192, 255),
                new RgbColor(68, 74, 80, 255), new RgbColor(35, 35, 35, 10),
                List.of(
                        new RgbColor(124, 252, 0, 255), new RgbColor(48, 213, 200, 255),
                        new RgbColor(252, 3, 0, 255), new RgbColor(237, 26, 116, 255)));
        return new GameConfig(PLAYERS, SEED,
                new GridConfig(36, 25),
                new DiscConfig(18, 10, 2.0, 7, 3, 3, 4),
                new CollisionConfig(4),
                new RoundConfig(15, 2, 2, 1),
                palette,
                new AiConfig(24, 4, true));
    }

    /** Aim intent for player {@code p} at tick {@code t} -- a fixed, RNG-free script. */
    private PlayWorld.AimInput aim(int p, int t) {
        boolean left = ((t / 15) + p) % 2 == 0;
        return left ? PlayWorld.AimInput.LEFT : PlayWorld.AimInput.RIGHT;
    }

    /** Whether player {@code p} fires at tick {@code t} (staggered so several discs bounce at once). */
    private boolean shoot(int p, int t) {
        return t % 37 == (p * 11) % 37;
    }

    /** Applies one tick of the shared script to {@code world} and advances the simulation one step. */
    private void advance(PlayWorld world, int t) {
        for (int p = 0; p < PLAYERS; p++) {
            world.applyInput(p, aim(p, t), shoot(p, t));
        }
        world.step();
    }

    /** A full snapshot of everything that must be identical across peers, as a comparable string. */
    private String fingerprint(PlayWorld world) {
        var sb = new StringBuilder();
        sb.append("active=").append(world.totalActiveDiscs());
        List<Entity> discs = world.discs();
        sb.append(" discs=").append(discs.size());
        for (Entity d : discs) {
            sb.append('|').append(d.getOwnerId())
              .append(',').append(d.getX())
              .append(',').append(d.getY())
              .append(',').append(d.getAngle())
              .append(',').append(d.getBounces())
              .append(',').append(d.isParked());
        }
        for (int p = 0; p < world.playerCount(); p++) {
            sb.append(" aim").append(p).append('=').append(world.aimOf(p).currentAngle());
        }
        for (CapturePoint cp : world.scoring().points()) {
            sb.append(" cp").append(cp.getTileX()).append(':').append(cp.getTileY())
              .append('=').append(cp.getOwnerId()).append('/').append(cp.getLevel());
        }
        return sb.toString();
    }

    @Test
    void sameSeedAndInputsStayBitIdenticalEveryTick() {
        PlayWorld a = new PlayWorld(config());
        PlayWorld b = new PlayWorld(config());

        // Maps + initial state must match before any input is applied.
        assertThat(fingerprint(a)).isEqualTo(fingerprint(b));

        for (int t = 0; t < TICKS; t++) {
            advance(a, t);
            advance(b, t);
            assertThat(fingerprint(a))
                    .as("worlds diverged at tick %d", t)
                    .isEqualTo(fingerprint(b));
        }
    }

    @Test
    void simulationActuallyProgresses() {
        // Guards against a trivially-passing determinism test (e.g. if no discs ever spawned or moved).
        PlayWorld w = new PlayWorld(config());
        String initial = fingerprint(w);
        boolean sawDiscs = false;
        for (int t = 0; t < TICKS; t++) {
            advance(w, t);
            if (w.totalActiveDiscs() > 0) {
                sawDiscs = true;
            }
        }
        assertThat(sawDiscs).as("expected discs to spawn during the script").isTrue();
        assertThat(fingerprint(w)).as("expected world state to evolve").isNotEqualTo(initial);
    }
}
