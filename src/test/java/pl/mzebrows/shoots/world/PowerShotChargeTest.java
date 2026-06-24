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
 * Feature #3: the hold-to-charge power shot. A press fires a normal disc immediately; holding fills
 * the charge bar and auto-releases ONE power disc when full; releasing clears the charge.
 */
class PowerShotChargeTest {

    private static final long SEED = 7L;
    // 0.5s charge at 120 steps/s -> 60 ticks; speed x2, more bounces, capture strength 3.
    private static final PowerShotConfig POWER = new PowerShotConfig(true, 0.5, 2.0, 20, 3);
    private static final int CHARGE_TICKS = 60;

    private PlayWorld newWorld() {
        var palette = new ColorPalette(
                new RgbColor(95, 99, 104), new RgbColor(25, 25, 25),
                new RgbColor(68, 74, 80), new RgbColor(102, 0, 102),
                new RgbColor(102, 75, 102), new RgbColor(192, 192, 192),
                new RgbColor(68, 74, 80), new RgbColor(35, 35, 35, 10),
                List.of(new RgbColor(124, 252, 0), new RgbColor(48, 213, 200),
                        new RgbColor(252, 3, 0), new RgbColor(237, 26, 116)));
        // Build via the 8-arg gameplay convenience (defaults menu/window), then re-create with our
        // explicit power config via the canonical constructor, reusing the defaulted menu/window.
        var base = new GameConfig(2, SEED, new GridConfig(36, 25),
                new DiscConfig(18, 10, 2.0, 7, 4, 3, 4), new CollisionConfig(4),
                new RoundConfig(15, 2, 2, 1), palette, new AiConfig(24, 4, true));
        var cfg = new GameConfig(base.playerNumber(), base.seed(), base.grid(), base.disc(),
                base.collision(), base.round(), base.palette(), base.ai(), base.menu(), base.window(), POWER);
        return new PlayWorld(cfg, new Random(SEED));
    }

    private static long poweredCount(PlayWorld world) {
        return world.discs().stream().filter(Entity::isPowered).count();
    }

    @Test
    void tapFiresOnlyANormalShot() {
        var world = newWorld();

        world.applyShoot(0, true);   // press: immediate normal disc + start charging
        world.applyShoot(0, false);  // release before the bar fills

        assertThat(world.discs()).hasSize(1);
        assertThat(world.discs().get(0).isPowered()).isFalse();
        assertThat(world.chargeProgress(0)).isZero();
    }

    @Test
    void holdingFillsTheBarThenAutoFiresOnePowerDisc() {
        var world = newWorld();

        world.applyShoot(0, true); // press: normal disc, begin charge
        assertThat(poweredCount(world)).isZero();
        assertThat(world.discs()).hasSize(1);

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
        assertThat(power.getMaxBounces()).isEqualTo(POWER.maxBounces());
        assertThat(power.getCaptureStrength()).isEqualTo(POWER.captureStrength());

        // Only one power disc per hold; charge resets after firing even while still held.
        assertThat(world.chargeProgress(0)).isZero();
        world.applyShoot(0, true);
        assertThat(poweredCount(world)).isEqualTo(1);
    }

    @Test
    void releasingClearsTheChargeSoItDoesNotAutoFire() {
        var world = newWorld();

        world.applyShoot(0, true);
        for (int t = 0; t < CHARGE_TICKS - 5; t++) {
            world.applyShoot(0, true); // not quite full
        }
        assertThat(poweredCount(world)).isZero();
        world.applyShoot(0, false);    // release resets the charge
        assertThat(world.chargeProgress(0)).isZero();

        // Holding again must start from zero, not resume near-full.
        world.applyShoot(0, true);
        assertThat(world.chargeProgress(0)).isZero();
    }
}
