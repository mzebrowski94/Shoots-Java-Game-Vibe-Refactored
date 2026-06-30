// src/test/java/pl/mzebrows/shoots/app/GameplayOptionsTest.java
package pl.mzebrows.shoots.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unit tests for the in-memory GAMEPLAY OPTIONS holder: clamping, IP validation, and config overlay. */
class GameplayOptionsTest {

    private GameplayOptions options() {
        return new GameplayOptions(GameConfigLoader.load(), OnlineConfig.load(), GameplayLimits.load());
    }

    @Test
    void adjustingClampsToTheConfiguredCaps() {
        GameplayOptions o = options();
        GameplayLimits lim = GameplayLimits.load();

        for (int i = 0; i < 1000; i++) {
            o.adjustDiscBounces(1);
            o.adjustDiscSpeed(1);
            o.adjustLaserBounces(1);
        }
        assertThat(o.getMaxDiscBounces()).isEqualTo(lim.discBounces().max());
        assertThat(o.getDiscSpeed()).isEqualTo(lim.discSpeed().max());
        assertThat(o.getMaxLaserBounces()).isEqualTo(lim.laserBounces().max());

        for (int i = 0; i < 1000; i++) {
            o.adjustDiscBounces(-1);
            o.adjustDiscSpeed(-1);
            o.adjustLaserBounces(-1);
        }
        assertThat(o.getMaxDiscBounces()).isEqualTo(lim.discBounces().min());
        assertThat(o.getDiscSpeed()).isEqualTo(lim.discSpeed().min());
        assertThat(o.getMaxLaserBounces()).isEqualTo(lim.laserBounces().min());
    }

    @Test
    void portStaysWithinTheConfiguredRange() {
        GameplayOptions o = options();
        GameplayLimits lim = GameplayLimits.load();
        o.setHostPort(70000);
        assertThat(o.getHostPort()).isEqualTo(lim.maxPort());
        o.setHostPort(1);
        assertThat(o.getHostPort()).isEqualTo(lim.minPort());
    }

    @Test
    void validatesDottedQuadIpAddresses() {
        GameplayOptions o = options();
        o.setHostIp("192.168.1.10");
        assertThat(o.isHostIpValid()).isTrue();
        o.setHostIp("127.0.0.1");
        assertThat(o.isHostIpValid()).isTrue();
        o.setHostIp("256.1.1.1");
        assertThat(o.isHostIpValid()).isFalse();
        o.setHostIp("1.2.3");
        assertThat(o.isHostIpValid()).isFalse();
        o.setHostIp("abc");
        assertThat(o.isHostIpValid()).isFalse();
    }

    @Test
    void applyToOverlaysDiscDisruptionAndRoundTime() {
        GameplayOptions o = options();
        while (o.getDiscSpeed() < 5.0) {
            o.adjustDiscSpeed(1);
        }
        while (o.getMaxDiscBounces() < 12) {
            o.adjustDiscBounces(1);
        }
        while (o.getMaxLaserBounces() > 3) {
            o.adjustLaserBounces(-1);
        }

        GameConfig base = GameConfigLoader.load();
        GameConfig applied = o.applyTo(base);

        assertThat(applied.disc().moveSpeed()).isEqualTo(o.getDiscSpeed());
        assertThat(applied.disc().maxBounces()).isEqualTo(o.getMaxDiscBounces());
        assertThat(applied.disc().laserMaxBounces()).isEqualTo(o.getMaxLaserBounces());
        assertThat(applied.disruption().durationSeconds()).isEqualTo(o.getDisruptionSeconds());
        assertThat(applied.disruption().graceSeconds()).isEqualTo(o.getGraceSeconds());
        assertThat(applied.round().roundTimeSeconds()).isEqualTo(o.getRoundTimeSeconds());
        assertThat(applied.disc().maxPerPlayer()).isEqualTo(base.disc().maxPerPlayer());
        assertThat(applied.power()).isEqualTo(base.power());
    }
}
