// pl/mzebrows/shoots/ui/GameCounter.java
package pl.mzebrows.shoots.ui;

import pl.mzebrows.shoots.app.GameSettings;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

/** Extends {@link GameCanvas}: the top panel that displays the round-time bar. */
public class GameCounter extends GameCanvas {

    //LayoutSizes
    int longWidth;
    int borderSize = 6;
    int size = 16;
    int borderSpace = 8;
    int timeBarWidth;
    int timeBarHalfWidth;
    int timeBarHeight;
    int timeBarLeftWidth;
    int timeBarUpperHeight;

    Color deadLineColor;
    Color deadLineBackgroundColor;

    //Layout
    Rectangle counterBorder;
    Rectangle leftDeadLine;
    Rectangle rightDeadLine;
    Rectangle leftDeadLineBackground;
    Rectangle rightDeadLineBackground;

    RoundEnum actualRoundState;

    GameCounter(GameSettings gameSettings) {
        super(gameSettings);

        this.width = gS.getDEFAULT_WIDTH();
        this.height = gS.getDEFAULT_COUNTER_HEIGHT();
        this.longWidth = gS.getDEFAULT_COUNTER_WIDTH();
        this.timeBarWidth = (int) (0.75 * width);
        this.timeBarHalfWidth = (int) (0.5 * timeBarWidth);
        this.timeBarHeight = (int) (0.25 * height);
        this.timeBarUpperHeight = (int) (0.5 * height - timeBarHeight * 0.5);
        this.timeBarLeftWidth = (int) (0.5 * width - 0.5 * timeBarWidth);

        setPreferredSize(new Dimension(gS.getDEFAULT_WIDTH(), gS.getDEFAULT_COUNTER_HEIGHT()));
        deadLineColor = gS.getColorScheme().getDeadLineColor();
        deadLineBackgroundColor = gS.getColorScheme().getDeadLineBackgroundColor();
        animatedElementLength = timeBarHalfWidth;
        animationTime = gS.getRoundTime();
    }

    @Override

    public void initializeGraphics() {
        strategy = getBufferStrategy();
        if (strategy == null) {
            this.createBufferStrategy(3);
            strategy = getBufferStrategy();
        }
        graphics = strategy.getDrawGraphics();
        g2d = (Graphics2D) graphics;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_GASP);

        initializeLayout();
    }

    @Override
    public void drawUpdate(RoundEnum roundState) {
        // Active rendering: re-acquire graphics each frame and repeat on lost/restored contents so a
        // window move (which can recreate the surface) does not leave us drawing into a stale context.
        if (strategy == null) {
            return;
        }
        this.actualRoundState = roundState;
        do {
            do {
                g2d = (Graphics2D) strategy.getDrawGraphics();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (roundState == RoundEnum.ROUND_PAUSED) {
                    drawRoundPaused();
                } else {
                    drawRoundContinues();
                }
                g2d.dispose();
            } while (strategy.contentsRestored());
            strategy.show();
        } while (strategy.contentsLost());
    }

    /**
     * Draws the GameCounter panel border.
     *
     * @param g2d        the Graphics2D used to draw the elements
     * @param roundState the current round phase
     */
    public void drawBorder(Graphics2D g2d, RoundEnum roundState) {
        g2d.setColor(standardColor);
        g2d.fillRect(0, 0, borderSize, height);
        g2d.fillRect(longWidth - borderSize, 0, borderSize, height);
        g2d.fillRect(width, 0, borderSize, height);
        g2d.fillRect(0, 0, longWidth, borderSize);
        g2d.fillRect(0, height - borderSize, longWidth, borderSize);

        drawCounterDeadLine(g2d, roundState);
        drawTitle(g2d);

    }

    @Override
    public void tick() {
        timeElapsed += 0.008;
        animatedElementElapsed = (int) (animatedElementLength * (timeElapsed * 1f / animationTime * 1f));
        if (timeElapsed > animationTime) {
            animationEnd = true;
        }
    }

    /**
     * Draws the game title in the top-right corner.
     *
     * @param gd2 the Graphics2D used to draw the elements
     */
    public void drawTitle(Graphics2D gd2) {
        g2d.setColor(deadLineBackgroundColor);
        g2d.drawString("Project", width + fontFreeSpace, 30 + textOffset);
        g2d.drawString(" Shooots!", width + fontFreeSpace, 55 + textOffset);
        g2d.setColor(standardColor);
        g2d.drawString("Project", width + fontFreeSpace, 30);
        g2d.drawString(" Shooots!", width + fontFreeSpace, 55);

    }

    @Override
    public void initializeLayout() {
        //CounterBorder
        counterBorder = new Rectangle(timeBarLeftWidth - borderSpace, timeBarUpperHeight - borderSpace, timeBarWidth + borderSpace + borderSpace, timeBarHeight + borderSpace + borderSpace);
        //DeadLine
        leftDeadLine = new Rectangle(timeBarLeftWidth, timeBarUpperHeight, timeBarHalfWidth, timeBarHeight);
        rightDeadLine = new Rectangle(timeBarLeftWidth + timeBarHalfWidth, timeBarUpperHeight, timeBarHalfWidth, timeBarHeight);
        leftDeadLineBackground = new Rectangle(timeBarLeftWidth, timeBarUpperHeight, timeBarHalfWidth, timeBarHeight);
        rightDeadLineBackground = new Rectangle(timeBarLeftWidth + timeBarHalfWidth, timeBarUpperHeight, timeBarHalfWidth, timeBarHeight);
        //Text
        g2d.setFont(gS.getGameFont().deriveFont(fontSize));
        g2d.setFont(new Font(gS.getGameFont().getFontName(), 100, 25));
    }

    /**
     * Draws the time bar.
     *
     * @param gd2 the Graphics2D used to draw the elements
     */
    private void drawCounterDeadLine(Graphics2D g2d, RoundEnum roundState) {
        //RIGHT SIDE OF BAR - deadlinebackground increasing
        g2d.setColor(standardColor);
        g2d.fill(counterBorder);

        if (!animationEnd) {
            g2d.setColor(deadLineBackgroundColor);
            g2d.fill(rightDeadLineBackground);
            g2d.setColor(deadLineColor);
            g2d.fillRect(timeBarLeftWidth + timeBarHalfWidth, timeBarUpperHeight, timeBarHalfWidth - animatedElementElapsed, timeBarHeight);
            //LEFT SIDE OF BAR - deadline decreasing
            g2d.setColor(deadLineColor);
            g2d.fill(leftDeadLine);
            g2d.setColor(deadLineBackgroundColor);
            g2d.fillRect(timeBarLeftWidth, timeBarUpperHeight, animatedElementElapsed, timeBarHeight);

        } else {
            g2d.setColor(deadLineBackgroundColor);
            g2d.fillRect(timeBarLeftWidth, timeBarUpperHeight, timeBarWidth, timeBarHeight);
        }
        g2d.setColor(standardColor);

        if (roundState == RoundEnum.ROUND_BEGIN) {
            drawRoundBegining();
        } else if (roundState == RoundEnum.ROUND_ENDS) {
            drawRoundEnding();
        }
    }

    @Override
    public void drawRoundPaused() {
        // During pause / win (a round has been played) redraw the normal counter so it shows faintly
        // through the near-transparent menu tint, matching the play-screen translucent look. On a fresh
        // start there is no game behind the menu, so clear to an opaque background instead.
        if (gS.getActualRoundNumber() > 0 || gS.isGameEnd()) {
            drawRoundContinues();
        } else {
            g2d.setColor(gS.getColorScheme().getBackgroundColor());
            g2d.fillRect(0, 0, gS.getDEFAULT_COUNTER_WIDTH(), gS.getDEFAULT_COUNTER_HEIGHT());
        }
        g2d.setColor(gS.getColorScheme().getMenuStandardColor());
        g2d.fillRect(0, 0, gS.getDEFAULT_COUNTER_WIDTH(), gS.getDEFAULT_COUNTER_HEIGHT());
    }

    @Override
    public void drawRoundContinues() {
        // Re-apply the title font every frame: graphics are now re-acquired per frame, so a font set only
        // at init would be lost and the title would fall back to the default AWT font.
        g2d.setFont(new Font(gS.getGameFont().getFontName(), 100, 25));
        g2d.setColor(gS.getColorScheme().getBackgroundColor());
        g2d.fillRect(0, 0, gS.getDEFAULT_COUNTER_WIDTH(), gS.getDEFAULT_COUNTER_HEIGHT());

        drawBorder(g2d, actualRoundState);
    }

    @Override
    public void drawRoundBegining() {
        animationElementEnd = true;
    }

    @Override
    public void drawRoundEnding() {
        animationElementEnd = true;
    }


    public void setRoundTimeInSeconds(int roundTimeInSeconds) {
        this.roundTimeInSeconds = roundTimeInSeconds;
    }

}
