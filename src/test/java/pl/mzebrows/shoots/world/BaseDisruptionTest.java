// src/test/java/pl/mzebrows/shoots/world/BaseDisruptionTest.java
package pl.mzebrows.shoots.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.mzebrows.shoots.config.AiConfig;
import pl.mzebrows.shoots.config.CollisionConfig;
import pl.mzebrows.shoots.config.ColorPalette;
import pl.mzebrows.shoots.config.DisruptionConfig;
import pl.mzebrows.shoots.config.DiscConfig;
import pl.mzebrows.shoots.config.GameConfig;
import pl.mzebrows.shoots.config.GridConfig;
import pl.mzebrows.shoots.config.RgbColor;
import pl.mzebrows.shoots.config.RoundConfig;

/**
 * Task 1.1: base disruption. Hitting an opponent's base parks the attacking disc on it and disables that
 * opponent's shooting and laser for a configured duration, after which the victim gets a short immunity
 * (grace) window during which they may shoot but cannot be re-disrupted.
 *
 * <p>Uses a fixed seed whose generated map leaves a clear straight (0-bounce) lane up shared column 12
 * from P0's base (bottom) to P1's base (top), so the disruption can be triggered deterministically by
 * aiming P0 up and firing.
 */
class BaseDisruptionTest {

    private static final long SEED = 42L;
    /** Aim angle (deg) that sends a P0 disc straight up column 12 into P1's base on this seed's map. */
    private static final double STRAIGHT_UP = 179.0;
    /** 1.0s disruption + 0.5s grace at 120 steps/s -> 120 and 60 ticks. */
    private static final DisruptionConfig DISRUPT = new DisruptionConfig(true, 1.0, 0.5);
    private static final int DISRUPT_TICKS = 120;

    private static ColorPalette palette() {
        return new ColorPalette(
                new RgbColor(95, 99, 104), new RgbColor(25, 25, 25),
                new RgbColor(68, 74, 80), new RgbColor(102, 0, 102),
                new RgbColor(102, 75, 102), new RgbColor(192, 192, 192),
                new RgbColor(68, 74, 80), new RgbColor(35, 35, 35, 10),
                List.of(new RgbColor(124, 252, 0), new RgbColor(48, 213, 200),
                        new RgbColor(252, 3, 0), new RgbColor(237, 26, 116)));
    }

    private static GameConfig config(DisruptionConfig disruption) {
        var base = new GameConfig(2, SEED, new GridConfig(36, 25),
                new DiscConfig(18, 10, 2.0, 7, 4, 3, 4), new CollisionConfig(4),
                new RoundConfig(15, 2, 2, 1), palette(), new AiConfig(24, 4, true));
        return new GameConfig(base.playerNumber(), base.seed(), base.grid(), base.disc(),
                base.collision(), base.round(), base.palette(), base.ai(), base.menu(), base.window(),
                base.power(), disruption);
    }

    private static PlayWorld world(DisruptionConfig disruption) {
        return new PlayWorld(config(disruption), new Random(SEED));
    }

    /** Aims P0 to {@code STRAIGHT_UP}, fires one disc, and steps until P1 is disrupted (or gives up). */
    private static int fireAndDisrupt(PlayWorld world) {
        aimP0Up(world);
        assertThat(world.fire(0)).isTrue();
        for (int t = 0; t < 600; t++) {
            world.step();
            if (world.isDisrupted(1)) {
                return t;
            }
        }
        return -1;
    }

    private static void aimP0Up(PlayWorld world) {
        var aim = world.aimOf(0);
        for (int s = 0; s < 500 && Math.abs(aim.currentAngle() - STRAIGHT_UP) > 0.4; s++) {
            world.applyInput(0, aim.currentAngle() < STRAIGHT_UP
                    ? PlayWorld.AimInput.LEFT : PlayWorld.AimInput.RIGHT, false);
        }
    }

    @Test
    void hittingAnOpponentBaseDisruptsThem() {
        var world = world(DISRUPT);
        assertThat(world.isDisrupted(1)).isFalse();

        int when = fireAndDisrupt(world);

        assertThat(when).as("P1 gets disrupted by P0's disc").isGreaterThanOrEqualTo(0);
        assertThat(world.isDisrupted(1)).isTrue();
        assertThat(world.disruptionProgress(1)).isGreaterThan(0.0);
        // The attacker (P0) is not disrupted by its own shot.
        assertThat(world.isDisrupted(0)).isFalse();
    }

    @Test
    void disruptedPlayerCannotShootOrShowLaser() {
        var world = world(DISRUPT);
        fireAndDisrupt(world);
        assertThat(world.isDisrupted(1)).isTrue();

        // Shooting is fully blocked while disrupted.
        assertThat(world.fire(1)).isFalse();
        assertThat(world.firePower(1)).isFalse();
        // The aiming laser goes dark too.
        int[] xs = new int[16];
        int[] ys = new int[16];
        assertThat(world.predictLaser(1, xs, ys)).isZero();
    }

    @Test
    void attackerDiscParksOnTheBaseForTheWholeDisruption() {
        var world = world(DISRUPT);
        fireAndDisrupt(world);

        // While disrupted the attacker's disc is held on the base, so P0's disc slot stays occupied
        // (the cost of being aggressive) and the disc is still tracked in the world.
        assertThat(world.activeDiscs(0)).isEqualTo(1);
        assertThat(world.discs()).anyMatch(d -> d.isParked());
    }

    @Test
    void disruptionEndsIntoGraceThenTheDiscIsFreed() {
        var world = world(DISRUPT);
        fireAndDisrupt(world);
        assertThat(world.isDisrupted(1)).isTrue();

        // Step out the disruption window; it should flip into the immunity (grace) window.
        for (int t = 0; t < DISRUPT_TICKS + 5 && world.isDisrupted(1); t++) {
            world.step();
        }
        assertThat(world.isDisrupted(1)).isFalse();
        assertThat(world.isImmune(1)).isTrue();
        assertThat(world.graceProgress(1)).isGreaterThan(0.0);

        // The parked disc is freed once the disruption ends, releasing the attacker's slot.
        assertThat(world.activeDiscs(0)).isZero();
        assertThat(world.discs()).noneMatch(d -> d.isParked());
    }

    @Test
    void duringGraceTheVictimMayShootButCannotBeReDisrupted() {
        var world = world(DISRUPT);
        fireAndDisrupt(world);
        for (int t = 0; t < DISRUPT_TICKS + 5 && world.isDisrupted(1); t++) {
            world.step();
        }
        assertThat(world.isImmune(1)).isTrue();

        // The victim can shoot again during grace.
        assertThat(world.fire(1)).isTrue();

        // A second attacker disc that reaches the base during grace must NOT re-disrupt.
        aimP0Up(world);
        world.fire(0);
        boolean reDisrupted = false;
        for (int t = 0; t < 600 && world.isImmune(1); t++) {
            world.step();
            if (world.isDisrupted(1)) {
                reDisrupted = true;
                break;
            }
        }
        assertThat(reDisrupted).as("immune base is not re-disrupted during grace").isFalse();
    }

    @Test
    void disruptionDisabledLetsDiscsPassThroughBases() {
        var world = world(new DisruptionConfig(false, 1.0, 0.5));
        aimP0Up(world);
        world.fire(0);
        for (int t = 0; t < 600; t++) {
            world.step();
        }
        // With the feature off, no one is ever disrupted and no disc parks.
        assertThat(world.isDisrupted(1)).isFalse();
        assertThat(world.discs()).noneMatch(d -> d.isParked());
    }

    @Test
    void resetRoundClearsDisruptionState() {
        var world = world(DISRUPT);
        fireAndDisrupt(world);
        assertThat(world.isDisrupted(1)).isTrue();

        world.resetRound();

        assertThat(world.isDisrupted(1)).isFalse();
        assertThat(world.isImmune(1)).isFalse();
        assertThat(world.discs()).isEmpty();
        // Both players can shoot again after the reset.
        assertThat(world.fire(0)).isTrue();
    }
}
