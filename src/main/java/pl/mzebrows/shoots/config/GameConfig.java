// src/main/java/pl/mzebrows/shoots/config/GameConfig.java
package pl.mzebrows.shoots.config;

/** Root immutable game configuration aggregating all tunable sub-configs. */
public record GameConfig(
        int playerNumber,
        GridConfig grid,
        DiscConfig disc,
        CollisionConfig collision,
        RoundConfig round,
        ColorPalette palette) {

    public GameConfig {
        if (playerNumber < 1 || playerNumber > 4) {
            throw new IllegalArgumentException("playerNumber must be in [1,4]: " + playerNumber);
        }
    }
}
