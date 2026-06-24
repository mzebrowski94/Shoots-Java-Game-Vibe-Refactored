// src/test/java/pl/mzebrows/shoots/config/GameConfigLoaderTest.java
package pl.mzebrows.shoots.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;

class GameConfigLoaderTest {

    @Test
    void defaultsMirrorLegacyValues() {
        var config = GameConfigLoader.defaults();

        assertThat(config.playerNumber()).isEqualTo(2);
        assertThat(config.grid().unit()).isEqualTo(36);
        assertThat(config.grid().tableSize()).isEqualTo(25);
        assertThat(config.grid().playfieldPixels()).isEqualTo(900);
        assertThat(config.disc().moveSpeed()).isEqualTo(2.0);
        assertThat(config.disc().maxBounces()).isEqualTo(7);
        assertThat(config.collision().ballCollisionSize()).isEqualTo(4);
        assertThat(config.round().roundTimeSeconds()).isEqualTo(15);
        assertThat(config.palette().players()).hasSize(4);
    }

    @Test
    void loadingMissingResourceFallsBackToDefaults() {
        var config = GameConfigLoader.load("does-not-exist.properties");
        var defaults = GameConfigLoader.defaults();

        // Seed is resolved fresh each call, so normalise it before comparing the rest.
        assertThat(config.withSeed(0L)).isEqualTo(defaults.withSeed(0L));
    }

    @Test
    void loadsBundledPropertiesFromClasspath() {
        var config = GameConfigLoader.load();

        assertThat(config.grid().unit()).isEqualTo(36);
        assertThat(config.disc().bigRadius()).isEqualTo(18);
        // All four player colours come straight from game.properties (color.player1..4).
        assertThat(config.palette().playerColor(1)).isEqualTo(new RgbColor(124, 252, 0));
        assertThat(config.palette().playerColor(2)).isEqualTo(new RgbColor(48, 213, 200));
        assertThat(config.palette().playerColor(3)).isEqualTo(new RgbColor(252, 3, 0));
        assertThat(config.palette().playerColor(4)).isEqualTo(new RgbColor(237, 26, 116));
    }

    @Test
    void propertiesOverrideDefaultsAndParseColours() {
        var props = new Properties();
        props.setProperty("grid.unit", "40");
        props.setProperty("disc.moveSpeed", "3.5");
        props.setProperty("color.player1", "10,20,30,40");

        var config = GameConfigLoader.fromProperties(props);

        assertThat(config.grid().unit()).isEqualTo(40);
        assertThat(config.disc().moveSpeed()).isEqualTo(3.5);
        assertThat(config.palette().playerColor(1)).isEqualTo(new RgbColor(10, 20, 30, 40));
        // unset keys keep defaults
        assertThat(config.grid().tableSize()).isEqualTo(25);
    }

    @Test
    void invalidNumericAndColourValuesFallBackToDefaults() {
        var props = new Properties();
        props.setProperty("grid.unit", "not-a-number");
        props.setProperty("color.background", "999,0,0"); // out of range -> rejected

        var config = GameConfigLoader.fromProperties(props);

        assertThat(config.grid().unit()).isEqualTo(36);
        assertThat(config.palette().background()).isEqualTo(new RgbColor(95, 99, 104));
    }

    @Test
    void blankSeedResolvesToAFreshValueEachLoad() {
        var props = new Properties(); // no game.seed

        long first = GameConfigLoader.fromProperties(props).seed();
        long second = GameConfigLoader.fromProperties(props).seed();

        // Time-based; extremely unlikely to collide across two consecutive resolutions.
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void explicitSeedIsUsedVerbatimAndIsReproducible() {
        var props = new Properties();
        props.setProperty("game.seed", "123456789");

        assertThat(GameConfigLoader.fromProperties(props).seed()).isEqualTo(123456789L);
        assertThat(GameConfigLoader.fromProperties(props).seed()).isEqualTo(123456789L);
    }

    @Test
    void invalidSeedFallsBackToAFreshValue() {
        var props = new Properties();
        props.setProperty("game.seed", "not-a-long");

        // Falls back to a time seed rather than throwing.
        assertThat(GameConfigLoader.fromProperties(props).seed()).isNotEqualTo(0L);
    }

    @Test
    void defaultsIncludeDiscPerShotAndAiBlock() {
        var d = GameConfigLoader.defaults();

        assertThat(d.disc().maxPerShot()).isEqualTo(3);
        assertThat(d.ai().scanAngles()).isEqualTo(24);
        assertThat(d.ai().scanBudgetPerFrame()).isEqualTo(4);
        assertThat(d.ai().skillsEnabled()).isTrue();
    }

    @Test
    void bundledPropertiesProvideDiscPerShotAndAiBlock() {
        var config = GameConfigLoader.load();

        assertThat(config.disc().maxPerShot()).isEqualTo(3);
        assertThat(config.ai().scanAngles()).isEqualTo(24);
        assertThat(config.ai().scanBudgetPerFrame()).isEqualTo(4);
        assertThat(config.ai().skillsEnabled()).isTrue();
    }

    @Test
    void aiAndDiscPerShotKeysOverrideDefaults() {
        var props = new Properties();
        props.setProperty("disc.maxPerShot", "2");
        props.setProperty("ai.scanAngles", "40");
        props.setProperty("ai.scanBudgetPerFrame", "2");
        props.setProperty("ai.skillsEnabled", "false");

        var config = GameConfigLoader.fromProperties(props);

        assertThat(config.disc().maxPerShot()).isEqualTo(2);
        assertThat(config.ai().scanAngles()).isEqualTo(40);
        assertThat(config.ai().scanBudgetPerFrame()).isEqualTo(2);
        assertThat(config.ai().skillsEnabled()).isFalse();
    }

    @Test
    void absentAiKeysFallBackPerKey() {
        var props = new Properties();
        props.setProperty("ai.scanAngles", "30"); // only one key set

        var config = GameConfigLoader.fromProperties(props);

        assertThat(config.ai().scanAngles()).isEqualTo(30);
        // the others keep defaults
        assertThat(config.ai().scanBudgetPerFrame()).isEqualTo(4);
        assertThat(config.ai().skillsEnabled()).isTrue();
    }

    @Test
    void discAccelerationAndPowerShotDefaultsAreLoaded() {
        var config = GameConfigLoader.load();

        // Feature #1: per-bounce acceleration is on by default and capped.
        assertThat(config.disc().bounceSpeedGain()).isGreaterThan(1.0);
        assertThat(config.disc().maxSpeedFactor()).isGreaterThanOrEqualTo(1.0);
        // Feature #3/#4: power shot + AI power-shot tunables come from game.properties.
        assertThat(config.power().enabled()).isTrue();
        assertThat(config.power().chargeSeconds()).isGreaterThan(0.0);
        assertThat(config.power().speedFactor()).isGreaterThanOrEqualTo(1.0);
        assertThat(config.power().maxBounces()).isGreaterThan(config.disc().maxBounces());
        assertThat(config.power().captureStrength()).isGreaterThanOrEqualTo(1);
        assertThat(config.ai().powerShotEnabled()).isTrue();
        assertThat(config.ai().powerShotMinBounces()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void powerShotKeysOverrideDefaults() {
        var props = new Properties();
        props.setProperty("disc.bounceSpeedGain", "1.10");
        props.setProperty("disc.maxSpeedFactor", "2.0");
        props.setProperty("power.enabled", "false");
        props.setProperty("power.chargeSeconds", "1.25");
        props.setProperty("power.speedFactor", "2.5");
        props.setProperty("power.maxBounces", "30");
        props.setProperty("power.captureStrength", "4");
        props.setProperty("ai.powerShotEnabled", "false");
        props.setProperty("ai.powerShotMinBounces", "5");

        var config = GameConfigLoader.fromProperties(props);

        assertThat(config.disc().bounceSpeedGain()).isEqualTo(1.10);
        assertThat(config.disc().maxSpeedFactor()).isEqualTo(2.0);
        assertThat(config.power().enabled()).isFalse();
        assertThat(config.power().chargeSeconds()).isEqualTo(1.25);
        assertThat(config.power().speedFactor()).isEqualTo(2.5);
        assertThat(config.power().maxBounces()).isEqualTo(30);
        assertThat(config.power().captureStrength()).isEqualTo(4);
        assertThat(config.ai().powerShotEnabled()).isFalse();
        assertThat(config.ai().powerShotMinBounces()).isEqualTo(5);
    }
}
