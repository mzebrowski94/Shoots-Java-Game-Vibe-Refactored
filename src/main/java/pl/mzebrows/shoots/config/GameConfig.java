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
        AiConfig ai,
        MenuConfig menu,
        WindowConfig window,
        PowerShotConfig power,
        DisruptionConfig disruption) {

    public GameConfig {
        if (playerNumber < 1 || playerNumber > 4) {
            throw new IllegalArgumentException("playerNumber must be in [1,4]: " + playerNumber);
        }
    }

    /**
     * Convenience constructor for callers (mainly tests) that don't care about the menu/window layout:
     * fills {@code menu}, {@code window} and {@code power} from the built-in defaults so only the
     * gameplay-relevant sub-configs need to be supplied.
     */
    public GameConfig(int playerNumber, long seed, GridConfig grid, DiscConfig disc,
                      CollisionConfig collision, RoundConfig round, ColorPalette palette, AiConfig ai) {
        this(playerNumber, seed, grid, disc, collision, round, palette, ai,
                DEFAULT_MENU, DEFAULT_WINDOW, DEFAULT_POWER, DEFAULT_DISRUPTION);
    }

    /** Back-compatible constructor without an explicit power-shot config (uses the built-in default). */
    public GameConfig(int playerNumber, long seed, GridConfig grid, DiscConfig disc,
                      CollisionConfig collision, RoundConfig round, ColorPalette palette, AiConfig ai,
                      MenuConfig menu, WindowConfig window) {
        this(playerNumber, seed, grid, disc, collision, round, palette, ai, menu, window,
                DEFAULT_POWER, DEFAULT_DISRUPTION);
    }

    /** Back-compatible constructor without an explicit disruption config (uses the built-in default). */
    public GameConfig(int playerNumber, long seed, GridConfig grid, DiscConfig disc,
                      CollisionConfig collision, RoundConfig round, ColorPalette palette, AiConfig ai,
                      MenuConfig menu, WindowConfig window, PowerShotConfig power) {
        this(playerNumber, seed, grid, disc, collision, round, palette, ai, menu, window, power,
                DEFAULT_DISRUPTION);
    }

    /** Default menu layout used when a caller supplies only the gameplay sub-configs. */
    private static final MenuConfig DEFAULT_MENU =
            new MenuConfig(2, 4, 2, 20, 4, 15, 60, 5, 0, 30f, 46, 28, 16);
    /** Default window sizing used when a caller supplies only the gameplay sub-configs. */
    private static final WindowConfig DEFAULT_WINDOW = new WindowConfig(25, 2, 4);
    /** Default charged-shot tuning used when a caller supplies only the gameplay sub-configs. */
    private static final PowerShotConfig DEFAULT_POWER = new PowerShotConfig(true, 0.6, 1.8, 14, 2);
    /** Default base-disruption tuning used when a caller supplies only the gameplay sub-configs. */
    private static final DisruptionConfig DEFAULT_DISRUPTION = new DisruptionConfig(true, 4.0, 2.0);

    /** Returns a copy with a different master seed, preserving every other tunable. */
    public GameConfig withSeed(long newSeed) {
        return new GameConfig(playerNumber, newSeed, grid, disc, collision, round, palette, ai, menu, window, power, disruption);
    }

    /** Returns a copy with a different player count, preserving every other tunable (including the seed). */
    public GameConfig withPlayerNumber(int newPlayerNumber) {
        return new GameConfig(newPlayerNumber, seed, grid, disc, collision, round, palette, ai, menu, window, power, disruption);
    }

    /** Returns a copy with a different round config, preserving every other tunable. */
    public GameConfig withRound(RoundConfig newRound) {
        return new GameConfig(playerNumber, seed, grid, disc, collision, newRound, palette, ai, menu, window, power, disruption);
    }
}
