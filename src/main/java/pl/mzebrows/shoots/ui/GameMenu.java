// pl/mzebrows/shoots/ui/GameMenu.java
package pl.mzebrows.shoots.ui;

import pl.mzebrows.shoots.app.GameSettings;
import pl.mzebrows.shoots.app.Round;

import pl.mzebrows.shoots.input.GameAction;
import pl.mzebrows.shoots.world.PlayWorld;
import pl.mzebrows.shoots.input.InputBridge;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;

/** The game menu: renders the menu UI, applies setting changes, handles menu input, and shows final results. */
public class GameMenu {

    int width;
    int height;
    int textPosition;
    int nextLine;
    int menuHeight;
    int menuScoreHigh;
    MenuEnum menuOption;
    GameSettings gameSettings;
    Font menuFont;
    int iterator = 50;
    boolean textBrighter = true;
    Color winTextColor;

    int roundTime = 20;
    int roundTimeLimit = 60;

    int playerLimit = 4;
    int playerNumber = 2;

    int roundNumber = 4;
    int roundNumberLimit = 20;

    String stringContinue = "   [    CONTINUE    ]";
    String stringNewGame = "[ START NEW GAME ]";
    String stringPlayerNumberText = "    - Player Number: -";
    String stringRoundLimitText = "      - Round Limit: - ";
    String stringRoundTimeText = "      - Round Time: - ";
    String stringQuit = "      [     QUIT     ]";
    String stringPlayerNumber = "            < " + playerNumber + " >";
    String stringRoundNumber = "            < " + roundNumber + " >";
    String stringRoundTime = "           < " + roundTime + " >";

    GameMenu(GameSettings gameSettings) {
        menuOption = MenuEnum.START_NEW_GAME;
        this.gameSettings = gameSettings;
        width = gameSettings.getDefaultWidth();
        height = gameSettings.getDefaultHeight();
        nextLine = 50;
        menuHeight = 150;
        menuScoreHigh = 100;
        textPosition = (width / 2) - 50;
        menuFont = gameSettings.getMenuFont().deriveFont(30f);
        winTextColor = gameSettings.getColorScheme().getBackgroundFontColor();
    }

    /**
     * Draws the menu UI.
     *
     * @param g2d the Graphics2D used to draw the elements
     * menu
     */
    public void drawMenu(Graphics2D g2d, PlayWorld world) {
        g2d.setFont(menuFont);

        // UX: anchor the menu on a dark rounded backdrop so the purple/green text keeps consistent
        // contrast regardless of the (now more opaque) game frame behind it.
        drawMenuBackdrop(g2d);

        // Subtle highlight bar behind the currently selected row, for clear focus.
        drawSelectionHighlight(g2d);

        Color purple = gameSettings.getColorScheme().getDeadLineColor();
        shadowString(g2d, stringNewGame, textPosition, menuHeight + 3 * nextLine, purple);
        shadowString(g2d, stringPlayerNumberText, textPosition, menuHeight + 4 * nextLine, purple);
        shadowString(g2d, stringPlayerNumber, textPosition, menuHeight + 5 * nextLine, purple);
        shadowString(g2d, stringRoundLimitText, textPosition, menuHeight + 6 * nextLine, purple);
        shadowString(g2d, stringRoundNumber, textPosition, menuHeight + 7 * nextLine, purple);
        shadowString(g2d, stringRoundTimeText, textPosition, menuHeight + 8 * nextLine, purple);
        shadowString(g2d, stringRoundTime, textPosition, menuHeight + 9 * nextLine, purple);
        shadowString(g2d, stringQuit, textPosition, menuHeight + 12 * nextLine, purple);

        if (gameSettings.getActualRoundNumber() == 0) {
            shadowString(g2d, stringContinue, textPosition, menuHeight, gameSettings.getColorScheme().getBackgroundFontColor());
        } else if (gameSettings.isGameEnd()) {
            drawGameEnd(g2d, world);
        } else {
            shadowString(g2d, stringContinue, textPosition, menuHeight, purple);
        }

        drawChoosenMenuOption(g2d);
    }

    /** Horizontal padding between the menu panel edge and its content. */
    private static final int PANEL_PAD_X = 28;
    /** Minimum gap kept between the panel and the window edges. */
    private static final int PANEL_SCREEN_MARGIN = 16;

    /** Max players the scoreboard ever shows; the game-end panel is always sized for this many columns. */
    private static final int MAX_PLAYERS = 4;
    /** Width reserved for the "Rounds:/Points:" row labels at the left of the scoreboard. */
    private static final int SCORE_LABEL_WIDTH = 150;

    /** Widest menu row (measured) plus padding on both sides -- the base panel width. */
    private int menuRowsWidth(Graphics2D g2d) {
        FontMetrics fm = g2d.getFontMetrics(menuFont);
        int widest = 0;
        for (String row : new String[]{stringContinue, stringNewGame, stringPlayerNumberText,
                stringRoundLimitText, stringRoundTimeText, stringQuit, "       WINNER(s) !     "}) {
            widest = Math.max(widest, fm.stringWidth(row));
        }
        return widest + 2 * PANEL_PAD_X;
    }

    /**
     * Panel width. On the game-end screen it is widened to hold the label column plus all
     * {@value #MAX_PLAYERS} score columns; otherwise it is just the widest menu row. Always clamped to
     * fit on-screen.
     */
    private int panelWidth(Graphics2D g2d) {
        int w = menuRowsWidth(g2d);
        if (gameSettings.isGameEnd()) {
            FontMetrics fm = g2d.getFontMetrics(menuFont);
            int scoreboard = SCORE_LABEL_WIDTH + MAX_PLAYERS * (fm.stringWidth("00") + 44)
                    + 2 * PANEL_PAD_X;
            w = Math.max(w, scoreboard);
        }
        return Math.min(w, width - 2 * PANEL_SCREEN_MARGIN);
    }

    /** Left edge of the menu panel: centred on the (left-aligned) menu text block, clamped on-screen. */
    private int panelLeft(Graphics2D g2d) {
        FontMetrics fm = g2d.getFontMetrics(menuFont);
        int menuCentre = textPosition + fm.stringWidth(stringNewGame) / 2;
        int w = panelWidth(g2d);
        int x = menuCentre - w / 2;
        int maxX = width - PANEL_SCREEN_MARGIN - w;
        return Math.max(PANEL_SCREEN_MARGIN, Math.min(x, maxX));
    }

    /** Dark, slightly transparent rounded panel behind the menu items to lift text contrast. */
    private void drawMenuBackdrop(Graphics2D g2d) {
        int pad = 24;
        int top = menuHeight - 3 * nextLine;            // above the WINNER/CONTINUE line
        int bottom = menuHeight + 12 * nextLine + pad;  // below QUIT
        int x = panelLeft(g2d);
        int w = panelWidth(g2d);
        int h = bottom - top;
        g2d.setColor(new Color(15, 15, 20, 150));
        g2d.fillRoundRect(x, top, w, h, 28, 28);
        g2d.setColor(new Color(0, 0, 0, 90));
        g2d.drawRoundRect(x, top, w, h, 28, 28);
    }

    /** Draws a soft drop shadow then the coloured text; never changes the requested text colour. */
    private void shadowString(Graphics2D g2d, String text, int x, int y, Color color) {
        g2d.setColor(new Color(0, 0, 0, 160));
        g2d.drawString(text, x + 2, y + 2);
        g2d.setColor(color);
        g2d.drawString(text, x, y);
    }

    /** Soft filled bar behind the selected row so the green/yellow choice reads as focused. */
    private void drawSelectionHighlight(Graphics2D g2d) {
        int row = selectedRow();
        if (row < 0) {
            return;
        }
        FontMetrics fm = g2d.getFontMetrics(menuFont);
        int y = menuHeight + row * nextLine;
        int barX = panelLeft(g2d) + 10;
        int barW = panelWidth(g2d) - 20;
        int barY = y - fm.getAscent();
        int barH = fm.getHeight() + 6;
        g2d.setColor(new Color(255, 255, 255, 28));
        g2d.fillRoundRect(barX, barY, barW, barH, 14, 14);
    }

    /** Row index (in nextLine units from menuHeight) of the selected option, or -1 if none. */
    private int selectedRow() {
        return switch (menuOption) {
            case CONTINUE -> 0;
            case START_NEW_GAME -> 3;
            case PLAYER_NUMBER_OPTION -> 5;
            case ROUND_NUMBER_OPTION -> 7;
            case ROUND_TIME_OPTION -> 9;
            case QUIT -> 12;
            default -> -1;
        };
    }

    /**
     * Draws the end-of-game results.
     *
     * @param g2d the Graphics2D used to draw the elements
     */
    public void drawGameEnd(Graphics2D g2d, PlayWorld world) {
        // Lay the scoreboard out INSIDE the centred panel: the row labels sit at the panel\'s left padding
        // and the player columns are distributed evenly across the remaining width, so every column
        // (including P4) stays within the backdrop regardless of player count.
        int panelX = panelLeft(g2d);
        int panelW = panelWidth(g2d);
        int labelX = panelX + PANEL_PAD_X;
        int colsLeft = labelX + SCORE_LABEL_WIDTH;
        int colsRight = panelX + panelW - PANEL_PAD_X;

        Color labelColor = gameSettings.getColorScheme().getBackgroundFontColor();
        shadowString(g2d, "Rounds: ", labelX, menuScoreHigh + 50, labelColor);
        shadowString(g2d, "Points: ", labelX, menuScoreHigh + 100, labelColor);

        if (world == null) {
            return;
        }

        // Advance the WINNER pulse once per frame (not once per player).
        if (textBrighter) {
            winTextColor = new Color(iterator / 2, 2 * iterator, iterator / 2);
            iterator++;
            if (iterator == 100) {
                textBrighter = false;
            }
        } else {
            winTextColor = new Color(iterator / 2, 2 * iterator, iterator / 2);
            iterator--;
            if (iterator == 50) {
                textBrighter = true;
            }
        }
        // Animated green "WINNER" colour preserved; shadow added for legibility; centred over the panel.
        FontMetrics fm = g2d.getFontMetrics(menuFont);
        String winner = "WINNER(s) !";
        int winnerX = panelX + (panelW - fm.stringWidth(winner)) / 2;
        shadowString(g2d, winner, winnerX, menuHeight - 2 * nextLine, winTextColor);

        // Distribute up to MAX_PLAYERS columns evenly across [colsLeft, colsRight]; draw only active ones.
        int players = world.playerCount();
        int slotWidth = (colsRight - colsLeft) / MAX_PLAYERS;
        for (int i = 0; i < players; i++) {
            var score = world.matchFlow().scoreOf(i);
            int colCentre = colsLeft + slotWidth * i + slotWidth / 2;
            Color scoreColor = score.isWinner() ? winTextColor : gameSettings.getColorScheme().getDeadLineColor();
            drawScoreCell(g2d, "P" + (i + 1), colCentre, menuScoreHigh, scoreColor, fm);
            drawScoreCell(g2d, "" + score.getRoundsWon(), colCentre, menuScoreHigh + 50, scoreColor, fm);
            drawScoreCell(g2d, "" + score.getTotalPoints(), colCentre, menuScoreHigh + 100, scoreColor, fm);
        }
    }

    /** Draws a scoreboard cell centred horizontally on {@code centreX}. */
    private void drawScoreCell(Graphics2D g2d, String text, int centreX, int y, Color color, FontMetrics fm) {
        shadowString(g2d, text, centreX - fm.stringWidth(text) / 2, y, color);
    }

    /**
     * Reads menu navigation from the input bridge and returns the chosen action.
     * Game settings (roundTime, playerNumber, roundLimit) are applied directly here
     * when START_NEW_GAME is confirmed.
     */
    public MenuEnum checkMenuInput(InputBridge input) {
        var choosenOption = MenuEnum.NO_OPTION;

        if (input.isJustPressed(GameAction.NAVIGATE_DOWN)) {
            changeMenuOptionDown();
        } else if (input.isJustPressed(GameAction.NAVIGATE_UP)) {
            changeMenuOptionUp();
        }

        if (menuOption == MenuEnum.CONTINUE) {
            if (input.isJustPressed(GameAction.CONFIRM)) {
                choosenOption = MenuEnum.CONTINUE;
            }
        } else if (menuOption == MenuEnum.START_NEW_GAME) {
            if (input.isJustPressed(GameAction.CONFIRM)) {
                choosenOption = MenuEnum.START_NEW_GAME;
                gameSettings.setRoundTime(roundTime);
                gameSettings.setPlayerNumber(playerNumber);
                gameSettings.setRoundLimit(roundNumber);
            }
        } else if (menuOption == MenuEnum.PLAYER_NUMBER_OPTION) {
            playerNumber = changeNumber(input, playerNumber, playerLimit, 1);
            stringPlayerNumber = "            < " + playerNumber + " >";
            choosenOption = MenuEnum.PLAYER_NUMBER_OPTION;
        } else if (menuOption == MenuEnum.ROUND_TIME_OPTION) {
            roundTime = changeNumber(input, roundTime, roundTimeLimit, 5);
            stringRoundTime = "           < " + roundTime + " >";
            choosenOption = MenuEnum.ROUND_TIME_OPTION;
        } else if (menuOption == MenuEnum.ROUND_NUMBER_OPTION) {
            roundNumber = changeNumber(input, roundNumber, roundNumberLimit, 4);
            stringRoundNumber = "            < " + roundNumber + " >";
            choosenOption = MenuEnum.ROUND_NUMBER_OPTION;
        } else if (menuOption == MenuEnum.QUIT) {
            if (input.isJustPressed(GameAction.CONFIRM)) {
                choosenOption = MenuEnum.QUIT;
            }
        }

        return choosenOption;
    }

    /** Current player count selected in the menu. */
    public int getPlayerNumber() { return playerNumber; }

    /** Current round limit selected in the menu. */
    public int getRoundNumber() { return roundNumber; }

    /** Current round time (seconds) selected in the menu. */
    public int getRoundTime() { return roundTime; }

    /** Adjusts a numeric menu option left/right and wraps at the limits. */
    public int changeNumber(InputBridge input, int defaultValue, int limit, int multiply) {
        if (input.isJustPressed(GameAction.NAVIGATE_LEFT)) {
            defaultValue -= multiply;
            if (defaultValue <= 0) {
                defaultValue = limit;
            }
        } else if (input.isJustPressed(GameAction.NAVIGATE_RIGHT)) {
            defaultValue += multiply;
            if (defaultValue > limit) {
                defaultValue = multiply;
            }
        }
        return defaultValue;
    }

    /** Moves the menu selection to the next option. */
    public void changeMenuOptionDown() {

        if (menuOption == MenuEnum.CONTINUE) {
            menuOption = MenuEnum.START_NEW_GAME;
        } else if (menuOption == MenuEnum.START_NEW_GAME) {
            menuOption = MenuEnum.PLAYER_NUMBER_OPTION;
        } else if (menuOption == MenuEnum.PLAYER_NUMBER_OPTION) {
            menuOption = MenuEnum.ROUND_NUMBER_OPTION;
        } else if (menuOption == MenuEnum.ROUND_NUMBER_OPTION) {
            menuOption = MenuEnum.ROUND_TIME_OPTION;
        } else if (menuOption == MenuEnum.ROUND_TIME_OPTION) {
            menuOption = MenuEnum.QUIT;
        } else if (menuOption == MenuEnum.QUIT) {
            if (gameSettings.getActualRoundNumber() != 0 && !gameSettings.isGameEnd()) {
                menuOption = MenuEnum.CONTINUE;
            } else {
                menuOption = MenuEnum.START_NEW_GAME;
            }
        }

    }

    /** Moves the menu selection to the previous option. */
    public void changeMenuOptionUp() {

        if (menuOption == MenuEnum.CONTINUE) {
            menuOption = MenuEnum.QUIT;
        } else if (menuOption == MenuEnum.START_NEW_GAME) {
            if (gameSettings.getActualRoundNumber() != 0 && !gameSettings.isGameEnd()) {
                menuOption = MenuEnum.CONTINUE;
            } else {
                menuOption = MenuEnum.QUIT;
            }
        } else if (menuOption == MenuEnum.PLAYER_NUMBER_OPTION) {
            menuOption = MenuEnum.START_NEW_GAME;
        } else if (menuOption == MenuEnum.ROUND_NUMBER_OPTION) {
            menuOption = MenuEnum.PLAYER_NUMBER_OPTION;
        } else if (menuOption == MenuEnum.ROUND_TIME_OPTION) {
            menuOption = MenuEnum.ROUND_NUMBER_OPTION;
        } else if (menuOption == MenuEnum.QUIT) {
            menuOption = MenuEnum.ROUND_TIME_OPTION;
        }

    }

    /**
     * Highlights the currently selected menu option.
     *
     * @param g2d the Graphics2D used to draw the elements
     */
    public void drawChoosenMenuOption(Graphics2D g2d) {
        // Keep the same green/yellow selection colours; only add the shared drop shadow for legibility.
        if (menuOption == MenuEnum.CONTINUE) {
            shadowString(g2d, stringContinue, textPosition, menuHeight, Color.green);
        } else if (menuOption == MenuEnum.START_NEW_GAME) {
            shadowString(g2d, stringNewGame, textPosition, menuHeight + 3 * nextLine, Color.green);
        } else if (menuOption == MenuEnum.PLAYER_NUMBER_OPTION) {
            shadowString(g2d, stringPlayerNumber, textPosition, menuHeight + 5 * nextLine, Color.yellow);
        } else if (menuOption == MenuEnum.ROUND_NUMBER_OPTION) {
            shadowString(g2d, stringRoundNumber, textPosition, menuHeight + 7 * nextLine, Color.yellow);
        } else if (menuOption == MenuEnum.ROUND_TIME_OPTION) {
            shadowString(g2d, stringRoundTime, textPosition, menuHeight + 9 * nextLine, Color.yellow);
        } else if (menuOption == MenuEnum.QUIT) {
            shadowString(g2d, stringQuit, textPosition, menuHeight + 12 * nextLine, Color.green);
        }
    }
}
