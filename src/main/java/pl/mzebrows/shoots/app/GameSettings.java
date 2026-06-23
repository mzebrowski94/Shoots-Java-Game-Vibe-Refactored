// pl/mzebrows/shoots/app/GameSettings.java
package pl.mzebrows.shoots.app;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import pl.mzebrows.shoots.ai.AiDifficulty;
import pl.mzebrows.shoots.input.InputBridge;
import pl.mzebrows.shoots.ui.ColorScheme;
import pl.mzebrows.shoots.ui.GameDimensions;
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
 */
@Slf4j
@Getter
@Setter
public class GameSettings {

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

    // Window sizing (derived from GameDimensions; immutable once constructed).
    private final int defaultWidth = GameDimensions.UNIT.getValue() * GameDimensions.WINDOW_TILES.getValue();
    private final int defaultHeight = GameDimensions.UNIT.getValue() * GameDimensions.WINDOW_TILES.getValue();
    private final int defaultCounterHeight = GameDimensions.UNIT.getValue() * 2;
    private final int defaultPointerWidth = GameDimensions.UNIT.getValue() * 4;
    private final int defaultCounterWidth = defaultWidth + defaultPointerWidth;
    private final int defaultPointerHeight = GameDimensions.UNIT.getValue() * GameDimensions.WINDOW_TILES.getValue();

    private final int size = GameDimensions.TABLE_SIZE.getValue();
    private final int unit = GameDimensions.UNIT.getValue();

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
     * Constructs default settings and loads fonts; the live model is created separately by PlayingState.
     */
    public GameSettings() {
        this.playerNumber = 2;
        this.roundLimit = 2;
        this.roundEndDelay = 2;
        this.animationTime = 1;
        this.colorScheme = new ColorScheme();
        this.playerKeyboardAvailable = true;
        this.actualRoundNumber = 0;
        this.roundTime = 15;
        this.roundList = new ArrayList<>();
        inputBridge = InputBridge.withDefaultKeyMap();
        initializeFont();
    }

    /**
     * Loads the bundled game/menu fonts, registering them with the graphics environment.
     */
    public void initializeFont() {
        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            File loadedGameFont = loadFont("13_Misa.TTF");
            gameFont = Font.createFont(Font.TRUETYPE_FONT, loadedGameFont).deriveFont(12f);
            File loadedMenuFont = loadFont("GeosansLight.ttf");
            menuFont = Font.createFont(Font.TRUETYPE_FONT, loadedMenuFont).deriveFont(25f);
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
