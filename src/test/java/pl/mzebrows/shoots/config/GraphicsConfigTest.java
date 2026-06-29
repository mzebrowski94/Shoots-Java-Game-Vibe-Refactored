package pl.mzebrows.shoots.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Properties;
import org.junit.jupiter.api.Test;

class GraphicsConfigTest {

    private Properties full() {
        return GameConfigLoader.bundledProperties();
    }

    @Test
    void bundledGraphicsLoadFromClasspath() {
        var g = GameConfigLoader.loadGraphics();
        assertThat(g.objects().baseRingBig()).isEqualTo(25);
        assertThat(g.objects().baseRingSmall()).isEqualTo(15);
        assertThat(g.objects().discCoreRadius()).isEqualTo(3);
        assertThat(g.menu().panelArc()).isEqualTo(28);
        assertThat(g.menu().label()).isEqualTo(new RgbColor(200, 160, 255));
        assertThat(g.menu().separator()).isEqualTo(new RgbColor(255, 200, 80, 200));
        assertThat(g.menu().panelFill()).isEqualTo(new RgbColor(20, 15, 40, 185));
    }

    @Test
    void loadMergesGraphicPropertiesIntoGameConfig() {
        // disc.bigRadius / smallRadius / laser falloff live in graphic.properties: load() must merge both files.
        var config = GameConfigLoader.load();
        assertThat(config.disc().bigRadius()).isEqualTo(18);
        assertThat(config.disc().smallRadius()).isEqualTo(10);
        assertThat(config.disc().laserBounceAlphaFalloff()).isEqualTo(0.75);
    }

    @Test
    void graphicsKeysOverrideTheBundledValue() {
        var props = full();
        props.setProperty("object.base.ringBig", "40");
        props.setProperty("menu.theme.label", "1,2,3,4");

        var g = GameConfigLoader.graphicsFromProperties(props);

        assertThat(g.objects().baseRingBig()).isEqualTo(40);
        assertThat(g.menu().label()).isEqualTo(new RgbColor(1, 2, 3, 4));
        // unchanged keys keep their bundled value
        assertThat(g.objects().baseRingSmall()).isEqualTo(15);
        assertThat(g.menu().panelArc()).isEqualTo(28);
    }

    @Test
    void missingGraphicsKeyIsFatal() {
        var props = full();
        props.remove("menu.theme.panelArc");
        assertThatThrownBy(() -> GameConfigLoader.graphicsFromProperties(props))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("menu.theme.panelArc");
    }

    @Test
    void objectStyleRejectsInvalidValues() {
        assertThatThrownBy(() -> new ObjectStyle(0, 15, 3, 0.5, 2.0, 0.8))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ObjectStyle(25, 15, 3, 0.5, 2.0, 1.5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
