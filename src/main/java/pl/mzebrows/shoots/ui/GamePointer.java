// pl/mzebrows/shoots/ui/GamePointer.java
package pl.mzebrows.shoots.ui;

import pl.mzebrows.shoots.score.CapturePoint;
import pl.mzebrows.shoots.world.PlayWorld;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;

/** Extends {@link GameCanvas}: the side panel that shows match statistics. */
public class GamePointer extends GameCanvas {

    Font roundTextFont;
    Font authorTextFont;

    //LayoutSizes
    int frameWidth = 5;
    int frameHight = 16;
    int borderSize = 6;
    int size = 16;
    int freeSpace = 30;
    int pointBarOffset = 3;
    int statsStartPosiotionWidth = 10;
    int statsStartPosiotionHight = 250;
    int roundPositionWidth = 30;
    int roundPositionHight = 100;
    int pointBarSize;
    double pointPercent;
    int actualHight = 0;
    int actualLeftWidth = 0;
    int actualPlayerIndex;
    int winBlockSize = 30;
    int winBlockPositionHight = 650;
    ArrayList<Integer> playerPointBarsList;
    ArrayList<Integer> playerPointBarElapsed;

    /** Live model pushed by the renderer each frame (null when paused/menu). */
    private PlayWorld world;

    GamePointer(GameSettings gameSettings) {
        super(gameSettings);
        fontSize = 25;
        textOffset = 1;
        this.gameSettings = gameSettings;

        setPreferredSize(new Dimension(gameSettings.getDefaultPointerWidth(), gameSettings.getDefaultPointerHeight()));
        playerPointBarsList = new ArrayList<>();
        playerPointBarElapsed = new ArrayList<>();
        for (int i = 0; i < gameSettings.getPlayerNumber(); i++) {
            playerPointBarsList.add(0);
            playerPointBarElapsed.add(0);
        }

        width = gameSettings.getDefaultPointerWidth();
        height = gameSettings.getDefaultPointerHeight();
        pointBarSize = width - (5 * borderSize) - (statsStartPosiotionWidth + borderSize);
        textFont = gameSettings.getGameFont();
        standardColor = gameSettings.getColorScheme().getStandardColor();
        roundTimeInSeconds = gameSettings.getRoundTime();
    }

    /** Receives the live model for this frame from the renderer. */
    public void setWorld(PlayWorld world) {
        this.world = world;
    }

    /** Number of players to render stats for, from the live model. */
    private int playerCount() {
        return world != null ? world.playerCount() : gameSettings.getPlayerNumber();
    }

    /** Current-round points controlled by a 0-based player. */
    private int currentPoints(int p) {
        return world != null ? world.matchFlow().scoreOf(p).getCurrentPoints() : 0;
    }

    /** Rounds won by a 0-based player. */
    private int roundsWon(int p) {
        return world != null ? world.matchFlow().scoreOf(p).getRoundsWon() : 0;
    }

    /** Previous round's banked points for a 0-based player (total minus current). */
    private int previousPoints(int p) {
        if (world == null) {
            return 0;
        }
        var s = world.matchFlow().scoreOf(p);
        return s.getTotalPoints() - s.getCurrentPoints();
    }

    /** Player display colour for a 0-based player. */
    private Color playerColor(int p) {
        return world != null ? world.playerColor(p) : Color.GRAY;
    }

    /** Player display name for a 0-based player. */
    private String playerName(int p) {
        return "P" + (p + 1);
    }

    /** Bar denominator: total capturable points on the map. */
    private int maxPoints() {
        if (world == null) {
            return 1;
        }
        return Math.max(1, world.scoring().points().size() * CapturePoint.MAX_LEVEL);
    }

    /** Resets the bars that visualise each player's captured points. */
    public void restartGamePointer() {
        playerPointBarsList.clear();
        playerPointBarElapsed.clear();
        for (int i = 0; i < gameSettings.getPlayerNumber(); i++) {
            playerPointBarsList.add(0);
            playerPointBarElapsed.add(0);
        }
    }

    @Override
    public void initializeLayout() {
        //DATA

        //Text
        textFont = new Font(gameSettings.getGameFont().getFontName(), 100, fontSize);
        g2d.setFont(textFont);
        roundTextFont = textFont.deriveFont(80f);
        authorTextFont = textFont.deriveFont(20f);
    }

    @Override
    public void drawUpdate(RoundEnum roundState) {
        // Active rendering: re-acquire graphics each frame and repeat on lost/restored contents. The
        // helper draw methods read the FIELD g2d, so we refresh the field here (no local shadow) -- this
        // is what keeps a window move from leaving the panel drawing into a stale, frozen context.
        if (strategy == null) {
            return;
        }
        recreateStrategyIfResized();
        do {
            do {
                g2d = (Graphics2D) strategy.getDrawGraphics();
                applyRenderScale(g2d);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (roundState == RoundEnum.ROUND_PAUSED) {
                    drawRoundPaused();
                } else {
                    g2d.setColor(gameSettings.getColorScheme().getBackgroundColor());
                    g2d.fillRect(0, 0, gameSettings.getDefaultPointerWidth(), gameSettings.getDefaultPointerHeight());
                    drawBorder(g2d);
                    drawRoundCounter(g2d);
                    if (world != null) {
                        drawPlayerStats(g2d, roundState);
                        drawRoundCounterBlocks(g2d, roundState);
                    }
                    drawAuthor(g2d);
                }
                g2d.dispose();
            } while (strategy.contentsRestored());
            strategy.show();
        } while (strategy.contentsLost());
    }

    /**
     * Draws the author name in the bottom-right corner.
     * @param g2d        the Graphics2D used to draw the elements
     */
    public void drawAuthor(Graphics2D g2d) {
        g2d.setColor(standardColor);
        g2d.setFont(authorTextFont);
        g2d.drawString("By:", borderSize + textOffset, height - freeSpace * 2);
        g2d.drawString("Mateusz", borderSize + textOffset, height - freeSpace - 10);
        g2d.drawString("Zebrowski", borderSize + textOffset, height - freeSpace + 10);
        g2d.setFont(textFont);
    }

    /**
     * Draws the current round number on the panel.
     * @param g2d        the Graphics2D used to draw the elements
     */
    public void drawRoundCounter(Graphics2D g2d) {
        g2d.setColor(gameSettings.getColorScheme().getBackgroundFontColor());
        g2d.drawString("Round", roundPositionWidth + textOffset, roundPositionHight + textOffset);
        g2d.setColor(standardColor);
        g2d.drawString("Round", roundPositionWidth, roundPositionHight);

        g2d.setFont(roundTextFont);
        g2d.setColor(gameSettings.getColorScheme().getBackgroundFontColor());
        g2d.drawString("}", roundPositionWidth + freeSpace + textOffset - 10, roundPositionHight + freeSpace * 2 + textOffset);
        g2d.drawString("" + gameSettings.getActualRoundNumber(), roundPositionWidth + freeSpace - 15 + textOffset, roundPositionHight + freeSpace * 2 + textOffset);
        g2d.setColor(standardColor);
        g2d.drawString("}", roundPositionWidth + freeSpace - 10, roundPositionHight + freeSpace * 2);
        g2d.drawString("" + gameSettings.getActualRoundNumber(), roundPositionWidth + freeSpace - 15, roundPositionHight + freeSpace * 2);
        g2d.setFont(textFont);
    }

    /**
     * Draws the bars visualising points captured in the current round.
     * @param g2d        the Graphics2D used to draw the elements
     * @param roundState the current round phase
     */
    public void drawPlayerStats(Graphics2D g2d, RoundEnum roundState) {
        // Guard: if the world's player count differs from the pre-allocated list size (e.g. the very
        // first round after launch uses a default-config world with a different count than GameSettings),
        // resize the lists in-place rather than crashing.
        int count = playerCount();
        while (playerPointBarsList.size() < count) {
            playerPointBarsList.add(0);
            playerPointBarElapsed.add(0);
        }
        while (playerPointBarsList.size() > count) {
            playerPointBarsList.removeLast();
            playerPointBarElapsed.removeLast();
        }

        for (int i = 0; i < count; i++) {
            actualPlayerIndex = i;
            actualLeftWidth = statsStartPosiotionWidth + borderSize;
            int actualRightWidth = width - (4 * borderSize);
            actualHight = statsStartPosiotionHight + freeSpace + (i * 3 * freeSpace);
            pointPercent = (double) currentPoints(i) / maxPoints();
            playerPointBarsList.set(i, (int) (pointBarSize * pointPercent));

            g2d.setColor(playerColor(i));
            g2d.drawString(playerName(i), statsStartPosiotionWidth + textOffset, actualHight + textOffset);
            g2d.setColor(standardColor);
            g2d.drawString(playerName(i), statsStartPosiotionWidth, actualHight);

            g2d.fillRect(actualLeftWidth, actualHight + freeSpace, frameWidth, 16);
            g2d.fillRect(actualRightWidth, actualHight + freeSpace, frameWidth, 16);

            int prev = previousPoints(i);
            if (prev > 0) {
                double prevoiusPointPercent = (double) prev / maxPoints();
                g2d.setColor(gameSettings.getColorScheme().getBackgroundPointBarColor());
                g2d.fillRect(actualLeftWidth + frameWidth, actualHight + freeSpace + 3, (int) (pointBarSize * prevoiusPointPercent), 10);
            }
            if (roundState == RoundEnum.ROUND_CONTINUES) {
                drawRoundContinues();
            } else if (roundState == RoundEnum.ROUND_BEGIN) {
                drawRoundBegining();
            } else if (roundState == RoundEnum.ROUND_ENDS) {
                drawRoundEnding();
            }

        }
    }

    /**
     * Draws the left and right borders of the point bars.
     * @param g2d        the Graphics2D used to draw the elements
     * @param roundState the current round phase
     */
    public void drawRoundCounterBlocks(Graphics2D g2d, RoundEnum roundState) {

       
            for (int i = 0; i < playerCount(); i++) {

                    g2d.setColor(gameSettings.getColorScheme().getWinBlockColor());
                    g2d.fillRect((width / 2) - winBlockSize / 2, winBlockPositionHight + (i * (freeSpace + 10)), winBlockSize, winBlockSize);
                    g2d.setColor(standardColor);
                    g2d.setStroke(new BasicStroke((float) borderSize / 2));
                    g2d.drawRect((width / 2) - winBlockSize / 2, winBlockPositionHight + (i * (freeSpace + 10)), winBlockSize, winBlockSize);
                    g2d.setColor(playerColor(i).darker());
                    g2d.drawString(""+roundsWon(i), (width / 2) - winBlockSize / 4 , winBlockPositionHight + winBlockSize - 5 + (i*winBlockSize + i*10));
                    
            }
        
    }

    @Override
    public void tick() {
        timeElapsed += 0.012;

        for (int i = 0; i < playerCount(); i++) {
            playerPointBarElapsed.set(i, (int) (playerPointBarsList.get(i) * (timeElapsed * 1f / animationTime * 1f)));
            if (timeElapsed > animationTime) {
                animationEnd = true;
            }
        }
    }

    /**
     * Draws the GamePointer panel border.
     * @param g2d        the Graphics2D used to draw the elements
     */
    public void drawBorder(Graphics2D g2d) {
        g2d.setColor(standardColor);
        g2d.fillRect(0, 0, borderSize, height);
        g2d.fillRect(width - borderSize, 0, borderSize, height);
        g2d.fillRect(0, height - borderSize, width, borderSize);
        g2d.fillRect(0, height - freeSpace * 3, width, borderSize);

    }

    @Override
    public void drawRoundPaused() {
        // During pause / win (a round has been played, world present) redraw the normal stats panel so it
        // shows faintly through the near-transparent menu tint, matching the play-screen translucent look.
        // On a fresh start there is no game behind the menu, so clear to an opaque background instead.
        if (world != null && (gameSettings.getActualRoundNumber() > 0 || gameSettings.isGameEnd())) {
            g2d.setColor(gameSettings.getColorScheme().getBackgroundColor());
            g2d.fillRect(0, 0, gameSettings.getDefaultPointerWidth(), gameSettings.getDefaultPointerHeight());
            drawBorder(g2d);
            drawRoundCounter(g2d);
            drawPlayerStats(g2d, RoundEnum.ROUND_PAUSED);
            drawRoundCounterBlocks(g2d, RoundEnum.ROUND_PAUSED);
            drawAuthor(g2d);
        } else {
            g2d.setColor(gameSettings.getColorScheme().getBackgroundColor());
            g2d.fillRect(0, 0, gameSettings.getDefaultPointerWidth(), gameSettings.getDefaultPointerHeight());
        }
        g2d.setColor(gameSettings.getColorScheme().getMenuStandardColor());
        g2d.fillRect(0, 0, gameSettings.getDefaultPointerWidth(), gameSettings.getDefaultPointerHeight());
    }

    @Override
    public void drawRoundContinues() {
        ///POINT BAR
        g2d.setColor(playerColor(actualPlayerIndex).darker());
        g2d.fillRect(actualLeftWidth + frameWidth, actualHight + freeSpace + 3 + pointBarOffset, playerPointBarsList.get(actualPlayerIndex) + pointBarOffset, 10);
        g2d.setColor(playerColor(actualPlayerIndex));
        g2d.fillRect(actualLeftWidth + frameWidth, actualHight + freeSpace + 3, playerPointBarsList.get(actualPlayerIndex), 10);
    }

    @Override
    public void drawRoundBegining() {
        animationElementEnd = true;
    }

    @Override
    public void drawRoundEnding() {
        g2d.setColor(playerColor(actualPlayerIndex).darker());
        g2d.fillRect(actualLeftWidth + frameWidth, actualHight + freeSpace + 3 + pointBarOffset, playerPointBarsList.get(actualPlayerIndex) - playerPointBarElapsed.get(actualPlayerIndex) + pointBarOffset, 10);
        g2d.setColor(playerColor(actualPlayerIndex));
        g2d.fillRect(actualLeftWidth + frameWidth, actualHight + freeSpace + 3, playerPointBarsList.get(actualPlayerIndex) - playerPointBarElapsed.get(actualPlayerIndex), 10);
    }

}
