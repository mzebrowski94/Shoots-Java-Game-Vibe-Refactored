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

        assertThat(config).isEqualTo(GameConfigLoader.defaults());
    }

    @Test
    void loadsBundledPropertiesFromClasspath() {
        var config = GameConfigLoader.load();

        assertThat(config.grid().unit()).isEqualTo(36);
        assertThat(config.disc().bigRadius()).isEqualTo(18);
        assertThat(config.palette().playerColor(1)).isEqualTo(new RgbColor(124, 252, 0));
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
}
