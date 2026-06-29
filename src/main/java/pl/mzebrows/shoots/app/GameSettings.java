// pl/mzebrows/shoots/app/GameSettings.java
package pl.mzebrows.shoots.app;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import pl.mzebrows.shoots.ai.AiDifficulty;
import pl.mzebrows.shoots.config.GameConfig;
import pl.mzebrows.shoots.config.GameConfigLoader;
import pl.mzebrows.shoots.config.GameplayLimits;
import pl.mzebrows.shoots.config.GraphicsConfig;
import pl.mzebrows.shoots.config.OnlineConfig;
import pl.mzebrows.shoots.input.InputBridge;
import pl.mzebrows.shoots.ui.ColorScheme;
import pl.mzebrows.shoots.ui.GameScreen;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Game window/config + round-pacing state shared by the AWT panels. The live simulation, scoring, and
 * win logic now live in the {@code world}/{@code score} model ({@code PlayWorld}/{@code MatchFlow});
 * this class retains only window sizing, fonts, colours, the input bridge, and lightweight round
 * bookkeeping (round number + per-round {@link Round} timing) that the panels and {@code PlayingState} read.
 *
 * <p>Every default is sourced from {@link GameConfig} (loaded from {@code game.properties}); the class
 * holds no hard-coded gameplay or window dimensions of its own.
 */
@Slf4j
@Getter
@Setter
public class GameSettings {

    /** Point size of the in-game HUD font (the menu font size is config-driven via {@code menu.fontSize}). */
    private static final float GAME_FONT_SIZE = 12f;

    /** The loaded game configuration backing every default below; the single source of truth. */
    @Setter(AccessLevel.NONE)
    private final GameConfig config;

    /** The loaded rendering configuration (menu chrome + map-object styling) from {@code graphic.properties}. */
    @Setter(AccessLevel.NONE)
    private final GraphicsConfig graphics;

    /**
     * Live, in-memory gameplay tunables edited in the GAMEPLAY OPTIONS menu (round time, disc speed/bounces,
     * laser projections, disruption/grace timings, host IP/port). Seeded from the loaded config; applied when
     * a match is built and propagated host->clients online. Held here so menu, state, and net all share one copy.
     */
    @Setter(AccessLevel.NONE)
    private final GameplayOptions gameplayOptions;

    private int playerNumber;
    /**
     * Number of computer-controlled players (0..playerNumber); they occupy the highest slots.
     */
    private int aiNumber;
    /**
     * Difficulty applied to all AI players for the match.
     */
    private AiDifficulty aiDifficulty = AiDifficulty.NORMAL;
    private InputBridge inputBridge;
    private Font gameFont;
    private Font menuFont;

    // Window sizing (derived from grid + window config; immutable once constructed).
    private final int defaultWidth;
    private final int defaultHeight;
    private final int defaultCounterHeight;
    private final int defaultPointerWidth;
    private final int defaultCounterWidth;
    private final int defaultPointerHeight;

    private final int size;
    private final int unit;

    private int roundTime;
    private ArrayList<Round> roundList;
    private int actualRoundNumber;
    private boolean playerKeyboardAvailable;
    private ColorScheme colorScheme;
    private int animationTime;
    private int roundEndDelay;
    private int roundLimit;

    // Tracked internally by startNewRound(); exposed read-only.
    @Setter(AccessLevel.NONE)
    private Round actualRound;
    @Setter(AccessLevel.NONE)
    private Round previousRound;

    private boolean gameEnd;

    /**
     * Constructs settings from the bundled {@code game.properties} (default config) and loads fonts;
     * the live model is created separately by PlayingState.
     */
    public GameSettings() {
        this(GameConfigLoader.load());
    }

    /**
     * Constructs settings whose every default is sourced from {@code config} -- no hard-coded values.
     * Window sizing is derived from the grid unit and window-tile multipliers; round pacing and the
     * initial player/AI selection come from the round and menu configs.
     */
    public GameSettings(GameConfig config) {
        this.config = config;
        this.graphics = GameConfigLoader.loadGraphics();
        this.gameplayOptions = new GameplayOptions(config, OnlineConfig.load(), GameplayLimits.load());

        var grid = config.grid();
        var window = config.window();
        this.unit = grid.unit();
        this.size = grid.tableSize();
        this.defaultWidth = unit * window.windowTiles();
        this.defaultHeight = unit * window.windowTiles();
        this.defaultCounterHeight = unit * window.counterHeightTiles();
        this.defaultPointerWidth = unit * window.pointerWidthTiles();
        this.defaultPointerHeight = unit * window.windowTiles();
        this.defaultCounterWidth = defaultWidth + defaultPointerWidth;

        this.playerNumber = config.playerNumber();
        this.aiNumber = config.menu().initialAiPlayers();
        this.roundTime = config.round().roundTimeSeconds();
        this.roundLimit = config.round().roundLimit();
        this.roundEndDelay = config.round().roundEndDelay();
        this.animationTime = config.round().animationTime();
        this.colorScheme = new ColorScheme(config.palette());
        this.playerKeyboardAvailable = true;
        this.actualRoundNumber = 0;
        this.roundList = new ArrayList<>();
        this.inputBridge = InputBridge.withDefaultKeyMap();
        initializeFont();
    }

    /**
     * Loads the bundled game/menu fonts, registering them with the graphics environment.
     */
    public void initializeFont() {
        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            File loadedGameFont = loadFont("13_Misa.TTF");
            gameFont = Font.createFont(Font.TRUETYPE_FONT, loadedGameFont).deriveFont(GAME_FONT_SIZE);
            File loadedMenuFont = loadFont("GeosansLight.ttf");
            menuFont = Font.createFont(Font.TRUETYPE_FONT, loadedMenuFont).deriveFont(config.menu().fontSize());
            ge.registerFont(Font.createFont(Font.TRUETYPE_FONT, loadedGameFont));
            ge.registerFont(Font.createFont(Font.TRUETYPE_FONT, loadedMenuFont));
        } catch (IOException | FontFormatException e) {
            log.error("Failed to load bundled fonts; falling back to defaults", e);
        }
    }

    /**
     * Starts a new round: bumps the round number and tracks the new {@link Round} for timing. The map,
     * discs, and scoring are owned by the live model and reset there ({@code PlayWorld.resetRound()}).
     */
    public void startNewRound(GameScreen gameScreen) {
        actualRoundNumber++;
        var newRound = new Round(this, actualRoundNumber);
        if (roundList != null && !roundList.isEmpty()) {
            previousRound = roundList.getLast();
        }
        roundList.add(newRound);
        actualRound = newRound;
    }

    /**
     * Resets match bookkeeping for a new game; the live model is reset by PlayingState.
     */
    public void restartGame() {
        actualRoundNumber = 0;
        roundList.clear();
        previousRound = null;
    }

    /** Whether a match is currently in progress (a round has begun and the match has not ended). Used to
     *  gate mid-game-only behaviour: GAMEPLAY OPTIONS are locked (#5) and QUIT abandons to menu (#6). */
    public boolean isMatchInProgress() {
        return actualRoundNumber != 0 && !gameEnd;
    }

    private static File loadFont(String fileName) throws IOException {
        File font = new File("app/classes/fonts/%s".formatted(fileName));
        if (!font.exists()) {
            log.debug("Font not found, using resources path");
            font = new File("src/main/resources/fonts/%s".formatted(fileName));
        }
        if (!font.exists()) {
            throw new IOException("Font file not found: %s".formatted(fileName));
        }
        return font;
    }
}
