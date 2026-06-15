// pl/mzebrows/shoots/ui/GameCanvas.java
package pl.mzebrows.shoots.ui;

import pl.mzebrows.shoots.app.GameSettings;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferStrategy;

import lombok.Getter;
import lombok.Setter;

/** Abstract base for the game's AWT panels: shared parameters plus the active-rendering scaffolding. */
public abstract class GameCanvas extends Canvas {

    // Game settings/config
    GameSettings gS;

    // Graphics
    Graphics2D g2d;
    BufferStrategy strategy;
    Graphics graphics;
    Color standardColor;
    int width;
    int height;

    // Timing
    @Getter @Setter boolean animationEnd = false;
    @Getter @Setter boolean animationElementEnd = false;
    int animationTime = 0;
    int animatedElementElapsed = 0;
    int animatedElementLength = 0;
    long tickTime = 0;
    @Getter @Setter double timeElapsed = 0;
    int roundTimeInSeconds = 0;

    // Text
    Font textFont;
    int fontSize = 80;
    int fontFreeSpace = 6;
    int textOffset = 3;
    int playerNamesTextSize = 30;

    GameCanvas(GameSettings gameSettings) {
        gS = gameSettings;
        animationTime = gS.getAnimationTime();
        this.setBackground(gS.getColorScheme().getBackgroundColor());
        this.addKeyListener(gS.getInputBridge());
        standardColor = gS.getColorScheme().getStandardColor();
        roundTimeInSeconds = gS.getRoundTime();
        this.setFocusable(true);
    }

    /** Sets up the BufferStrategy and its Graphics2D for active rendering, then lays out the panel. */
    public void initializeGraphics() {
        strategy = getBufferStrategy();
        if (strategy == null) {
            this.createBufferStrategy(3);
            strategy = getBufferStrategy();
        }
        graphics = strategy.getDrawGraphics();
        g2d = (Graphics2D) graphics;

        initializeLayout();
    }

    /** Advances the menu-element animation by one frame. */
    public void tick() {
        timeElapsed += 0.012;
        animatedElementElapsed = (int) (animatedElementLength * (timeElapsed * 1f / animationTime * 1f));
        if (timeElapsed > animationTime) {
            animationEnd = true;
        }
    }

    /** Restarts the menu-element animation. */
    public void restartAnimation() {
        timeElapsed = 0;
        animatedElementElapsed = 0;
        animationEnd = false;
        animationElementEnd = false;
    }

    /** Restarts the menu-element animation timer. */
    public void restartAnimationTime() {
        animationTime = gS.getRoundTime();
    }

    /**
     * Draws the panel for the given round state.
     * @param roundState the current round phase
     */
    abstract public void drawUpdate(RoundEnum roundState);

    /** Draws the panel while the game is paused. */
    abstract public void drawRoundPaused();

    /** Draws the panel while a round is in progress. */
    abstract public void drawRoundContinues();

    /** Draws the panel during the round-begin phase. */
    abstract public void drawRoundBegining();

    /** Draws the panel during the round-end phase. */
    abstract public void drawRoundEnding();

    /** Initialises the panel's layout/geometry. */
    abstract public void initializeLayout();

}
