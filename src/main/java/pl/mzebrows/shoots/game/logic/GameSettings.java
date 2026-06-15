
package pl.mzebrows.shoots.game.logic;

import pl.mzebrows.shoots.input.InputBridge;

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
public class GameSettings {

    int playerNumber;
    InputBridge inputBridge;
    Font gameFont;
    Font menuFont;

    //rozmary okienek
    final int DEFAULT_WIDTH = PSConst.UNIT.getValue() * PSConst.WINDOWWIDTH.getValue();
    final int DEFAULT_HIGHT = PSConst.UNIT.getValue() * PSConst.WINDOWWIDTH.getValue();
    final int DEFAULT_COUNTER_HIGHT = PSConst.UNIT.getValue() * 2;
    final int DEFAULT_POINTER_WIGHT = PSConst.UNIT.getValue() * 4;
    final int DEFAULT_COUNTER_WIDTH = DEFAULT_WIDTH + DEFAULT_POINTER_WIGHT;
    final int DEFAULT_POINTER_HIGHT = PSConst.UNIT.getValue() * PSConst.WINDOWWIDTH.getValue();

    //jednostki
    final int SIZE = PSConst.TABLESIZE.getValue();
    final int UNIT = PSConst.UNIT.getValue();

    int roundTime;
    ArrayList<Round> roundList;
    int actualRoundNumber;
    boolean playerKeyboardAvailable;
    ColorScheme colorScheme;
    int animationTime;
    int roundEndDelay;
    int roundLimit;
    Round actualRound, previousRound;
    boolean gameEnd;

    /** Constructs default settings and loads fonts; the live model is created separately by PlayingState. */
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

    /** Loads the bundled game/menu fonts, registering them with the graphics environment. */
    public void initializeFont() {
        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            File loadedGameFont = new File("src/main/resources/fonts/13_Misa.TTF");
            gameFont = Font.createFont(Font.TRUETYPE_FONT, loadedGameFont).deriveFont(12f);
            File loadedMenuFont = new File("src/main/resources/fonts/GeosansLight.ttf");
            menuFont = Font.createFont(Font.TRUETYPE_FONT, loadedMenuFont).deriveFont(25f);
            ge.registerFont(Font.createFont(Font.TRUETYPE_FONT, loadedGameFont));
            ge.registerFont(Font.createFont(Font.TRUETYPE_FONT, loadedMenuFont));
        } catch (IOException | FontFormatException e) {
            e.printStackTrace();
        }
    }

    /**
     * Starts a new round: bumps the round number and tracks the new {@link Round} for timing. The map,
     * discs, and scoring are owned by the live model and reset there ({@code PlayWorld.resetRound()}).
     */
    public void startNewRound(GameScreen gameScreen) {
        actualRoundNumber++;
        Round newRound = new Round(this, actualRoundNumber);
        if (roundList != null && !roundList.isEmpty()) {
            previousRound = roundList.get(roundList.size() - 1);
        }
        roundList.add(newRound);
        actualRound = newRound;
    }

    /** Resets match bookkeeping for a new game; the live model is reset by PlayingState. */
    public void restartGame() {
        actualRoundNumber = 0;
        roundList.clear();
        previousRound = null;
    }

    public InputBridge getInputBridge() {
        return inputBridge;
    }

    public void setInputBridge(InputBridge inputBridge) {
        this.inputBridge = inputBridge;
    }

    public int getPlayerNumber() {
        return playerNumber;
    }

    public void setPlayerNumber(int playerNumber) {
        this.playerNumber = playerNumber;
    }

    public int getDEFAULT_WIDTH() {
        return DEFAULT_WIDTH;
    }

    public int getDEFAULT_HIGHT() {
        return DEFAULT_HIGHT;
    }

    public int getDEFAULT_COUNTER_HIGHT() {
        return DEFAULT_COUNTER_HIGHT;
    }

    public int getDEFAULT_POINTER_WIGHT() {
        return DEFAULT_POINTER_WIGHT;
    }

    public int getDEFAULT_POINTER_HIGHT() {
        return DEFAULT_POINTER_HIGHT;
    }

    public int getDEFAULT_COUNTER_WIDTH() {
        return DEFAULT_COUNTER_WIDTH;
    }

    public int getSIZE() {
        return SIZE;
    }

    public int getUNIT() {
        return UNIT;
    }

    public int getRoundTime() {
        return roundTime;
    }

    public void setRoundTime(int roundTime) {
        this.roundTime = roundTime;
    }

    public Font getGameFont() {
        return gameFont;
    }

    public void setGameFont(Font gameFont) {
        this.gameFont = gameFont;
    }

    public int getActualRoundNumber() {
        return actualRoundNumber;
    }

    public void setActualRoundNumber(int actualRoundNumber) {
        this.actualRoundNumber = actualRoundNumber;
    }

    public boolean isPlayerKeyboardAvailable() {
        return playerKeyboardAvailable;
    }

    public void setPlayerKeyboardAvailable(boolean playerKeyboardAvailable) {
        this.playerKeyboardAvailable = playerKeyboardAvailable;
    }

    public ColorScheme getColorScheme() {
        return colorScheme;
    }

    public void setColorScheme(ColorScheme colorScheme) {
        this.colorScheme = colorScheme;
    }

    public int getAnimationTime() {
        return animationTime;
    }

    public void setAnimationTime(int animationTime) {
        this.animationTime = animationTime;
    }

    public int getRoundEndDelay() {
        return roundEndDelay;
    }

    public void setRoundEndDelay(int roundEndDelay) {
        this.roundEndDelay = roundEndDelay;
    }

    public Round getActualRound() {
        return actualRound;
    }

    public Round getPreviousRound() {
        return previousRound;
    }

    public Font getMenuFont() {
        return menuFont;
    }

    public void setMenuFont(Font menuFont) {
        this.menuFont = menuFont;
    }

    public int getRoundLimit() {
        return roundLimit;
    }

    public void setRoundLimit(int roundLimit) {
        this.roundLimit = roundLimit;
    }

    public ArrayList<Round> getRoundList() {
        return roundList;
    }

    public void setRoundList(ArrayList<Round> roundList) {
        this.roundList = roundList;
    }

    public boolean isGameEnd() {
        return gameEnd;
    }

    public void setGameEnd(boolean gameEnd) {
        this.gameEnd = gameEnd;
    }
}
