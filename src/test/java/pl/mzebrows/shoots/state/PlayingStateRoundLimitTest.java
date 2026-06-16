// src/test/java/pl/mzebrows/shoots/state/PlayingStateRoundLimitTest.java
package pl.mzebrows.shoots.state;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.mzebrows.shoots.config.RoundConfig;

/**
 * Regression: matches always lasted 2 rounds because the menu's selected round limit was written to
 * GameSettings but never applied to the PlayWorld's RoundConfig. These tests pin the wiring helper that
 * overlays the menu selection onto the loaded defaults.
 */
class PlayingStateRoundLimitTest {

    private static final RoundConfig DEFAULTS = new RoundConfig(60, 2, 2, 1);

    @Test
    void selectedRoundLimitOverridesTheLoadedDefault() {
        RoundConfig result = PlayingState.applySelectedRoundSettings(DEFAULTS, 4, 0);

        assertThat(result.roundLimit()).isEqualTo(4);
        // unchanged fields are preserved
        assertThat(result.roundTimeSeconds()).isEqualTo(DEFAULTS.roundTimeSeconds());
        assertThat(result.roundEndDelay()).isEqualTo(DEFAULTS.roundEndDelay());
        assertThat(result.animationTime()).isEqualTo(DEFAULTS.animationTime());
    }

    @Test
    void selectedRoundTimeIsAlsoApplied() {
        RoundConfig result = PlayingState.applySelectedRoundSettings(DEFAULTS, 6, 30);

        assertThat(result.roundLimit()).isEqualTo(6);
        assertThat(result.roundTimeSeconds()).isEqualTo(30);
    }

    @Test
    void nonPositiveSelectionsFallBackToDefaults() {
        RoundConfig result = PlayingState.applySelectedRoundSettings(DEFAULTS, 0, 0);

        assertThat(result.roundLimit()).isEqualTo(DEFAULTS.roundLimit());
        assertThat(result.roundTimeSeconds()).isEqualTo(DEFAULTS.roundTimeSeconds());
    }

    @Test
    void identicalSelectionReturnsTheSameConfigInstance() {
        RoundConfig result = PlayingState.applySelectedRoundSettings(DEFAULTS, 2, 60);

        assertThat(result).isSameAs(DEFAULTS);
    }
}
