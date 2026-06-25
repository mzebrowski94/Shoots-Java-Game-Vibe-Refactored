// src/test/java/pl/mzebrows/shoots/net/LockstepLoopbackTest.java
package pl.mzebrows.shoots.net;

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
import pl.mzebrows.shoots.world.PlayWorld;

/**
 * Proves the F1 routing invariant: feeding inputs through the {@link LockstepCoordinator} (assembled
 * from per-slot submissions, even out of order) and applying the released {@link InputFrame}s drives a
 * world BIT-IDENTICALLY to applying the same inputs directly. This is what lets the online loop run the
 * sim through the lockstep gate without changing the result the offline path would produce.
 */
class LockstepLoopbackTest {

    private static final long SEED = 42L;
    private static final int PLAYERS = 2;
    private static final int FRAMES = 150;          // command frames
    private static final int STEPS_PER_FRAME = 4;   // 120 Hz sim / 30 Hz command frame

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

    /** A fixed, RNG-free per-(slot,frame) input script so both paths receive identical input. */
    private TickInput scripted(int slot, long frame) {
        PlayWorld.AimInput aim = ((frame / 4) + slot) % 2 == 0
                ? PlayWorld.AimInput.LEFT : PlayWorld.AimInput.RIGHT;
        boolean shoot = frame % 9 == (slot * 3) % 9;
        return new TickInput(aim, shoot);
    }

    @Test
    void coordinatorRoutedInputMatchesDirectApplicationEveryFrame() {
        PlayWorld direct = new PlayWorld(config());
        PlayWorld looped = new PlayWorld(config());
        var coord = new LockstepCoordinator(PLAYERS, 0);

        assertThat(fingerprint(looped)).isEqualTo(fingerprint(direct));

        for (long f = 0; f < FRAMES; f++) {
            // Direct path: assemble the frame and apply it straight to the world.
            TickInput[] bySlot = new TickInput[PLAYERS];
            for (int s = 0; s < PLAYERS; s++) {
                bySlot[s] = scripted(s, f);
            }
            LockstepApplier.apply(direct, new InputFrame(f, bySlot), STEPS_PER_FRAME);

            // Looped path: submit each slot to the coordinator IN REVERSE (exercise out-of-order),
            // release the in-order frame, and apply that.
            for (int s = PLAYERS - 1; s >= 0; s--) {
                coord.submit(s, f, scripted(s, f));
            }
            InputFrame released = coord.tryRelease();
            assertThat(released).as("frame %d should be releasable", f).isNotNull();
            assertThat(released.frame()).isEqualTo(f);
            LockstepApplier.apply(looped, released, STEPS_PER_FRAME);

            assertThat(fingerprint(looped))
                    .as("looped path diverged from direct at command frame %d", f)
                    .isEqualTo(fingerprint(direct));
        }
    }

    /** Compact snapshot of the gameplay-authoritative state (the slice that must match across peers). */
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
              .append(',').append(d.getBounces());
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
}
