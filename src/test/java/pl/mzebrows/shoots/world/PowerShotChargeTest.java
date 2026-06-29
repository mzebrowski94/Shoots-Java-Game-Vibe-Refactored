// src/test/java/pl/mzebrows/shoots/world/PowerShotChargeTest.java
package pl.mzebrows.shoots.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.mzebrows.shoots.config.AiConfig;
import pl.mzebrows.shoots.config.CollisionConfig;
import pl.mzebrows.shoots.config.ColorPalette;
import pl.mzebrows.shoots.config.DiscConfig;
import pl.mzebrows.shoots.config.GameConfig;
import pl.mzebrows.shoots.config.GridConfig;
import pl.mzebrows.shoots.config.PowerShotConfig;
import pl.mzebrows.shoots.config.RgbColor;
import pl.mzebrows.shoots.config.RoundConfig;
import pl.mzebrows.shoots.entity.Entity;

/**
 * Feature #3 / #3.1: the hold-to-charge power shot. Pressing starts charging WITHOUT firing a normal disc;
 * holding fills the bar and auto-releases ONE power disc when full; a release before the bar fills fires a
 * single normal disc on key-up. The power disc's bounce budget scales off the disc's own maxBounces (#2.4).
 */
class PowerShotChargeTest {

    private static final long SEED = 7L;
    private static final int DISC_MAX_BOUNCES = 7;
    // 0.5s charge at 120 steps/s -> 60 ticks; speed x2, bounces x1.5, capture strength 3.
    private static final PowerShotConfig POWER = new PowerShotConfig(true, 0.5, 2.0, 1.5, 3);
    private static final int CHARGE_TICKS = 60;

    private PlayWorld newWorld() {
        var palette = new ColorPalette(
                new RgbColor(95, 99, 104), new RgbColor(25, 25, 25),
                new RgbColor(68, 74, 80), new RgbColor(102, 0, 102),
                new RgbColor(102, 75, 102), new RgbColor(192, 192, 192),
                new RgbColor(68, 74, 80), new RgbColor(35, 35, 35, 10),
                List.of(new RgbColor(124, 252, 0), new RgbColor(48, 213, 200),
                        new RgbColor(252, 3, 0), new RgbColor(237, 26, 116)));
        var base = new GameConfig(2, SEED, new GridConfig(36, 25),
                new DiscConfig(18, 10, 2.0, DISC_MAX_BOUNCES, 4, 3, 4), new CollisionConfig(4),
                new RoundConfig(15, 2, 2, 1), palette, new AiConfig(24, 4, true));
        var cfg = new GameConfig(base.playerNumber(), base.seed(), base.grid(), base.disc(),
                base.collision(), base.round(), base.palette(), base.ai(), base.menu(), base.window(), POWER);
        return new PlayWorld(cfg, new Random(SEED));
    }

    private static long poweredCount(PlayWorld world) {
        return world.discs().stream().filter(Entity::isPowered).count();
    }

    @Test
    void pressDoesNotFireYetAndReleaseFiresOneNormalDisc() {
        var world = newWorld();

        world.applyShoot(0, true);   // press: begin charging, NO disc yet (#3.1)
        assertThat(world.discs()).isEmpty();

        world.applyShoot(0, false);  // release before the bar fills -> one normal disc
        assertThat(world.discs()).hasSize(1);
        assertThat(world.discs().get(0).isPowered()).isFalse();
        assertThat(world.chargeProgress(0)).isZero();
    }

    @Test
    void holdingFillsTheBarThenAutoFiresOnePowerDisc() {
        var world = newWorld();

        world.applyShoot(0, true); // press: begin charge, still no disc
        assertThat(poweredCount(world)).isZero();
        assertThat(world.discs()).isEmpty();

        boolean fired = false;
        double midProgress = 0.0;
        for (int t = 0; t < CHARGE_TICKS * 3 && !fired; t++) {
            world.applyShoot(0, true);
            if (t == CHARGE_TICKS / 3) {
                midProgress = world.chargeProgress(0);
            }
            fired = poweredCount(world) > 0;
        }

        assertThat(midProgress).as("charge ring fills while held").isGreaterThan(0.0);
        assertThat(fired).isTrue();
        assertThat(poweredCount(world)).isEqualTo(1);

        Entity power = world.discs().stream().filter(Entity::isPowered).findFirst().orElseThrow();
        assertThat(power.getMoveSpeed()).isEqualTo(2.0 * POWER.speedFactor());
        assertThat(power.getMaxBounces()).isEqualTo(POWER.effectiveMaxBounces(DISC_MAX_BOUNCES));
        assertThat(power.getCaptureStrength()).isEqualTo(POWER.captureStrength());

        // Only one power disc per hold; charge resets after firing even while still held.
        assertThat(world.chargeProgress(0)).isZero();
        world.applyShoot(0, true);
        assertThat(poweredCount(world)).isEqualTo(1);
    }

    @Test
    void releasingEarlyClearsTheChargeSoItDoesNotAutoFire() {
        var world = newWorld();

        world.applyShoot(0, true);
        for (int t = 0; t < CHARGE_TICKS - 5; t++) {
            world.applyShoot(0, true); // not quite full
        }
        assertThat(poweredCount(world)).isZero();
        world.applyShoot(0, false);    // release: fires a normal disc, clears the charge
        assertThat(world.chargeProgress(0)).isZero();
        assertThat(poweredCount(world)).isZero();

        // Holding again must start from zero, not resume near-full.
        world.applyShoot(0, true);
        assertThat(world.chargeProgress(0)).isZero();
    }
}
