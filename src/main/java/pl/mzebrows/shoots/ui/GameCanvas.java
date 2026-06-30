// pl/mzebrows/shoots/ui/GameCanvas.java
package pl.mzebrows.shoots.ui;

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
    GameSettings gameSettings;

    // Graphics
    Graphics2D g2d;
    BufferStrategy strategy;
    Graphics graphics;
    Color standardColor;
    int width;
    int height;

    /** Uniform logical-unit -> pixel render scale applied each frame for elastic window scaling (1.0 = none). */
    double renderScale = 1.0;
    private int lastCanvasW = -1;
    private int lastCanvasH = -1;

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
        this.gameSettings = gameSettings;
        animationTime = gameSettings.getAnimationTime();
        this.setBackground(gameSettings.getColorScheme().getBackgroundColor());
        this.addKeyListener(gameSettings.getInputBridge());
        standardColor = gameSettings.getColorScheme().getStandardColor();
        roundTimeInSeconds = gameSettings.getRoundTime();
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
        animationTime = gameSettings.getRoundTime();
    }

    /** Sets the uniform render scale (logical units -> pixels) used by the elastic, scale-to-fit window. */
    public void setRenderScale(double scale) {
        this.renderScale = scale;
    }

    /**
     * Recreates the {@link BufferStrategy} when the canvas pixel size has changed (e.g. after a window
     * resize) so active rendering keeps matching the surface. A no-op until the canvas is displayable and
     * sized, and while the size is unchanged -- so the fixed-size path is byte-identical to before.
     */
    void recreateStrategyIfResized() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0 || (w == lastCanvasW && h == lastCanvasH)) {
            return;
        }
        lastCanvasW = w;
        lastCanvasH = h;
        try {
            createBufferStrategy(2);
            strategy = getBufferStrategy();
        } catch (IllegalStateException | IllegalArgumentException ignored) {
            // not displayable yet; retried on the next frame
        }
    }

    /** Applies the active render scale to a freshly acquired Graphics2D (a no-op at scale 1.0). */
    void applyRenderScale(Graphics2D g) {
        if (renderScale != 1.0 && renderScale > 0.0) {
            g.scale(renderScale, renderScale);
        }
    }

    /**
     * Draws the panel for the given round state.
     * @param roundState the current round phase
     */
    public abstract void drawUpdate(RoundEnum roundState);

    /** Draws the panel while the game is paused. */
    public abstract void drawRoundPaused();

    /** Draws the panel while a round is in progress. */
    public abstract void drawRoundContinues();

    /** Draws the panel during the round-begin phase. */
    public abstract void drawRoundBegining();

    /** Draws the panel during the round-end phase. */
    public abstract void drawRoundEnding();

    /** Initialises the panel's layout/geometry. */
    public abstract void initializeLayout();

}
