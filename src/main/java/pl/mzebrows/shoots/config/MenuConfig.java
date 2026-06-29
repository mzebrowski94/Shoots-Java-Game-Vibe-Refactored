// src/main/java/pl/mzebrows/shoots/config/MenuConfig.java
package pl.mzebrows.shoots.config;

/**
 * Tunables for the menu UI, externalised from {@code game.properties}. Groups the selectable-option
 * ranges/steps (the values the player cycles through in the menu) with the menu's visual layout
 * (font size, row spacing, and panel padding) so the menu carries no hard-coded magic numbers.
 *
 * @param maxPlayers         largest selectable player count (also the scoreboard column count)
 * @param initialRoundLimit  round-limit value pre-selected when the menu first opens
 * @param maxRoundLimit      largest selectable round limit
 * @param roundLimitStep     increment applied when cycling the round-limit option
 * @param initialRoundTime   round-time (seconds) pre-selected when the menu first opens
 * @param maxRoundTime       largest selectable round time (seconds)
 * @param roundTimeStep      increment applied when cycling the round-time option
 * @param initialAiPlayers   AI-player count pre-selected when the menu first opens
 * @param fontSize           point size the menu font is rendered at
 * @param rowSpacing         vertical gap (px) between consecutive menu rows
 * @param panelPadX          horizontal padding (px) between the panel edge and its content
 * @param panelMargin        minimum gap (px) kept between the panel and the window edges
 */
public record MenuConfig(
        int maxPlayers,
        int initialRoundLimit,
        int maxRoundLimit,
        int roundLimitStep,
        int initialRoundTime,
        int maxRoundTime,
        int roundTimeStep,
        int initialAiPlayers,
        float fontSize,
        int rowSpacing,
        int panelPadX,
        int panelMargin) {

    public MenuConfig {
        if (maxPlayers < 1) {
            throw new IllegalArgumentException("maxPlayers must be >= 1: " + maxPlayers);
        }
        if (roundLimitStep < 1) {
            throw new IllegalArgumentException("roundLimitStep must be >= 1: " + roundLimitStep);
        }
        if (maxRoundLimit < roundLimitStep) {
            throw new IllegalArgumentException("maxRoundLimit must be >= roundLimitStep: " + maxRoundLimit);
        }
        if (roundTimeStep < 1) {
            throw new IllegalArgumentException("roundTimeStep must be >= 1: " + roundTimeStep);
        }
        if (maxRoundTime < roundTimeStep) {
            throw new IllegalArgumentException("maxRoundTime must be >= roundTimeStep: " + maxRoundTime);
        }
        if (initialAiPlayers < 0 || initialAiPlayers > maxPlayers) {
            throw new IllegalArgumentException("initialAiPlayers must be in [0, maxPlayers]: " + initialAiPlayers);
        }
        if (fontSize <= 0f) {
            throw new IllegalArgumentException("fontSize must be positive: " + fontSize);
        }
        if (rowSpacing <= 0) {
            throw new IllegalArgumentException("rowSpacing must be positive: " + rowSpacing);
        }
        if (panelPadX < 0) {
            throw new IllegalArgumentException("panelPadX must be non-negative: " + panelPadX);
        }
        if (panelMargin < 0) {
            throw new IllegalArgumentException("panelMargin must be non-negative: " + panelMargin);
        }
    }
}
