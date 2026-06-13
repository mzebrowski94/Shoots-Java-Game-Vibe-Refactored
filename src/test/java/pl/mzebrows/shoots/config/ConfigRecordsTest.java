// src/test/java/pl/mzebrows/shoots/config/ConfigRecordsTest.java
package pl.mzebrows.shoots.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigRecordsTest {

    @Test
    void rgbColorRejectsOutOfRangeChannels() {
        assertThatThrownBy(() -> new RgbColor(256, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RgbColor(0, -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rgbColorConvertsToAwtPreservingChannels() {
        var awt = new RgbColor(10, 20, 30, 40).toAwt();

        assertThat(awt.getRed()).isEqualTo(10);
        assertThat(awt.getGreen()).isEqualTo(20);
        assertThat(awt.getBlue()).isEqualTo(30);
        assertThat(awt.getAlpha()).isEqualTo(40);
    }

    @Test
    void gridConfigDerivesPlayfieldAndMaxIndex() {
        var grid = new GridConfig(36, 25);

        assertThat(grid.playfieldPixels()).isEqualTo(900);
        assertThat(grid.maxIndex()).isEqualTo(24);
    }

    @Test
    void discConfigRejectsSmallRadiusNotLessThanBig() {
        assertThatThrownBy(() -> new DiscConfig(10, 10, 2.0, 7))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void paletteWrapsPlayerNumberAroundAvailableColours() {
        var palette = new ColorPalette(
                new RgbColor(0, 0, 0), new RgbColor(0, 0, 0), new RgbColor(0, 0, 0),
                new RgbColor(0, 0, 0), new RgbColor(0, 0, 0), new RgbColor(0, 0, 0),
                new RgbColor(0, 0, 0), new RgbColor(0, 0, 0),
                List.of(new RgbColor(1, 1, 1), new RgbColor(2, 2, 2)));

        assertThat(palette.playerColor(1)).isEqualTo(new RgbColor(1, 1, 1));
        assertThat(palette.playerColor(2)).isEqualTo(new RgbColor(2, 2, 2));
        assertThat(palette.playerColor(3)).isEqualTo(new RgbColor(1, 1, 1)); // wraps
    }

    @Test
    void gameConfigRejectsInvalidPlayerCount() {
        var d = GameConfigLoader.defaults();
        assertThatThrownBy(() -> new GameConfig(0, d.grid(), d.disc(), d.collision(), d.round(), d.palette()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GameConfig(5, d.grid(), d.disc(), d.collision(), d.round(), d.palette()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
