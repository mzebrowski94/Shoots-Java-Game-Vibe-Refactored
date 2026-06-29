// src/test/java/pl/mzebrows/shoots/config/GameConfigLoaderTest.java
package pl.mzebrows.shoots.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Properties;
import org.junit.jupiter.api.Test;

/**
 * The bundled {@code game.properties} + {@code graphic.properties} are the single source of truth: there
 * are no code defaults, so every required key must be present and any missing/invalid one is fatal.
 */
class GameConfigLoaderTest {

    /** A complete, valid property set (the bundled files merged) to mutate in individual tests. */
    private Properties full() {
        return GameConfigLoader.bundledProperties();
    }

    @Test
    void loadsBundledConfigFromClasspath() {
        var config = GameConfigLoader.load();

        // Map geometry is a fixed code constant, not a property.
        assertThat(config.grid().unit()).isEqualTo(GameConfigLoader.GRID_UNIT);
        assertThat(config.grid().tableSize()).isEqualTo(GameConfigLoader.TABLE_SIZE);
        assertThat(config.grid().playfieldPixels()).isEqualTo(900);

        assertThat(config.disc().moveSpeed()).isEqualTo(2.25);
        assertThat(config.disc().maxBounces()).isEqualTo(9);
        assertThat(config.disc().maxPerShot()).isEqualTo(3);
        assertThat(config.round().roundTimeSeconds()).isEqualTo(45);
        assertThat(config.round().roundLimit()).isEqualTo(4);
        assertThat(config.collision().ballCollisionSize()).isEqualTo(4);

        // Colours come from graphic.properties (merged into the game config).
        assertThat(config.palette().players()).hasSize(4);
        assertThat(config.palette().playerColor(1)).isEqualTo(new RgbColor(124, 252, 0));

        // AI block + power/disruption.
        assertThat(config.ai().scanAngles()).isEqualTo(24);
        assertThat(config.ai().skillsEnabled()).isTrue();
        assertThat(config.power().enabled()).isTrue();
        assertThat(config.power().maxBouncesFactor()).isEqualTo(1.5);
        assertThat(config.power().effectiveMaxBounces(config.disc().maxBounces()))
                .isGreaterThan(config.disc().maxBounces());
        assertThat(config.disruption().enabled()).isTrue();
        assertThat(config.disruption().durationSeconds()).isGreaterThan(0.0);
    }

    @Test
    void missingRequiredKeyIsFatal() {
        var props = full();
        props.remove("disc.speed");
        assertThatThrownBy(() -> GameConfigLoader.fromProperties(props))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("disc.speed");
    }

    @Test
    void invalidNumberIsFatal() {
        var props = full();
        props.setProperty("disc.maxBounces", "not-a-number");
        assertThatThrownBy(() -> GameConfigLoader.fromProperties(props))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("disc.maxBounces");
    }

    @Test
    void explicitValuesOverrideAndColoursParse() {
        var props = full();
        props.setProperty("disc.speed", "3.5");
        props.setProperty("color.player1", "10,20,30,40");

        var config = GameConfigLoader.fromProperties(props);

        assertThat(config.disc().moveSpeed()).isEqualTo(3.5);
        assertThat(config.palette().playerColor(1)).isEqualTo(new RgbColor(10, 20, 30, 40));
        // unchanged keys keep their bundled value
        assertThat(config.disc().maxBounces()).isEqualTo(9);
    }

    @Test
    void blankSeedResolvesToAFreshValueEachLoad() {
        var props = full(); // game.seed is blank in the bundle

        long first = GameConfigLoader.fromProperties(props).seed();
        long second = GameConfigLoader.fromProperties(props).seed();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void explicitSeedIsUsedVerbatimAndIsReproducible() {
        var props = full();
        props.setProperty("game.seed", "123456789");

        assertThat(GameConfigLoader.fromProperties(props).seed()).isEqualTo(123456789L);
        assertThat(GameConfigLoader.fromProperties(props).seed()).isEqualTo(123456789L);
    }

    @Test
    void invalidSeedIsFatal() {
        var props = full();
        props.setProperty("game.seed", "not-a-long");

        assertThatThrownBy(() -> GameConfigLoader.fromProperties(props))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("game.seed");
    }

    @Test
    void aiAndDiscPerShotKeysOverride() {
        var props = full();
        props.setProperty("disc.maxPerShot", "2");
        props.setProperty("ai.scanAngles", "40");
        props.setProperty("ai.skillsEnabled", "false");

        var config = GameConfigLoader.fromProperties(props);

        assertThat(config.disc().maxPerShot()).isEqualTo(2);
        assertThat(config.ai().scanAngles()).isEqualTo(40);
        assertThat(config.ai().skillsEnabled()).isFalse();
    }

    @Test
    void baseAttackAndPerSkillTogglesParse() {
        var props = full();
        props.setProperty("ai.baseAttackEnabled", "false");
        props.setProperty("ai.skill.powerShot", "false");

        var config = GameConfigLoader.fromProperties(props);

        assertThat(config.ai().baseAttackEnabled()).isFalse();
        assertThat(config.ai().toggles().powerShot()).isFalse();
        assertThat(config.ai().toggles().accuracy()).isTrue();
    }
}
