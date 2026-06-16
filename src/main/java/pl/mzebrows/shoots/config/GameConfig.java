// src/main/java/pl/mzebrows/shoots/config/GameConfig.java
package pl.mzebrows.shoots.config;

/**
 * Root immutable game configuration aggregating all tunable sub-configs.
 *
 * <p>{@code seed} is the resolved master random seed for a run: it seeds both map generation and
 * (later) AI decision-making, so a given seed reproduces a whole round. The loader resolves a blank
 * {@code game.seed} to a fresh time-based value, so omitting it yields a different run each launch
 * while setting it makes the run reproducible.
 */
public record GameConfig(
        int playerNumber,
        long seed,
        GridConfig grid,
        DiscConfig disc,
        CollisionConfig collision,
        RoundConfig round,
        ColorPalette palette,
        AiConfig ai) {

    public GameConfig {
        if (playerNumber < 1 || playerNumber > 4) {
            throw new IllegalArgumentException("playerNumber must be in [1,4]: " + playerNumber);
        }
    }

    /** Returns a copy with a different master seed, preserving every other tunable. */
    public GameConfig withSeed(long newSeed) {
        return new GameConfig(playerNumber, newSeed, grid, disc, collision, round, palette, ai);
    }

    /** Returns a copy with a different player count, preserving every other tunable (including the seed). */
    public GameConfig withPlayerNumber(int newPlayerNumber) {
        return new GameConfig(newPlayerNumber, seed, grid, disc, collision, round, palette, ai);
    }

    /** Returns a copy with a different round config, preserving every other tunable. */
    public GameConfig withRound(RoundConfig newRound) {
        return new GameConfig(playerNumber, seed, grid, disc, collision, newRound, palette, ai);
    }
}
