// pl/mzebrows/shoots/ui/GameMenu.java
package pl.mzebrows.shoots.ui;

import pl.mzebrows.shoots.app.GameSettings;
import pl.mzebrows.shoots.app.Round;

import pl.mzebrows.shoots.ai.AiDifficulty;
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
    int playerNumber = 4;

    int roundNumber = 4;
    int roundNumberLimit = 20;

    int aiNumber = 3;
    AiDifficulty aiDifficulty = AiDifficulty.NORMAL;

    String stringContinue = "[ CONTINUE ]";
    String stringNewGame = "[ START NEW GAME ]";
    String stringPlayerNumberText = "- Player Number -";
    String stringRoundLimitText = "- Round Limit -";
    String stringRoundTimeText = "- Round Time -";
    String stringQuit = "[ QUIT ]";
    String stringControls = "[ CONTROLS ]";

    /** When true, the menu shows the all-players controls panel instead of the option list. */
    boolean showingControls = false;
    String stringPlayerNumber = optionValue(playerNumber);
    String stringRoundNumber = optionValue(roundNumber);
    String stringRoundTime = optionValue(roundTime);
    String stringAiNumberText = "- AI Players -";
    String stringAiNumber = optionValue(aiNumber);
    String stringAiDifficultyText = "- AI Difficulty -";
    String stringAiDifficulty = difficultyValue(aiDifficulty.getDisplayName());

    GameMenu(GameSettings gameSettings) {
        menuOption = MenuEnum.START_NEW_GAME;
        this.gameSettings = gameSettings;
        width = gameSettings.getDefaultWidth();
        height = gameSettings.getDefaultHeight();
        nextLine = 46;
        menuHeight = 120;
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

        if (showingControls) {
            drawControls(g2d);
            return;
        }

        // UX: anchor the menu on a dark rounded backdrop so the purple/green text keeps consistent
        // contrast regardless of the (now more opaque) game frame behind it.
        drawMenuBackdrop(g2d);

        // Subtle highlight bar behind the currently selected row, for clear focus.
        drawSelectionHighlight(g2d);

        Color purple = gameSettings.getColorScheme().getDeadLineColor();
        shadowStringCentered(g2d, stringNewGame, menuHeight + 2 * nextLine, purple);
        shadowStringCentered(g2d, stringPlayerNumberText, menuHeight + 3 * nextLine, purple);
        shadowStringCentered(g2d, stringPlayerNumber, menuHeight + 4 * nextLine, purple);
        shadowStringCentered(g2d, stringRoundLimitText, menuHeight + 5 * nextLine, purple);
        shadowStringCentered(g2d, stringRoundNumber, menuHeight + 6 * nextLine, purple);
        shadowStringCentered(g2d, stringRoundTimeText, menuHeight + 7 * nextLine, purple);
        shadowStringCentered(g2d, stringRoundTime, menuHeight + 8 * nextLine, purple);
        shadowStringCentered(g2d, stringAiNumberText, menuHeight + 9 * nextLine, purple);
        shadowStringCentered(g2d, stringAiNumber, menuHeight + 10 * nextLine, purple);
        shadowStringCentered(g2d, stringAiDifficultyText, menuHeight + 11 * nextLine, purple);
        shadowStringCentered(g2d, stringAiDifficulty, menuHeight + 12 * nextLine, purple);
        shadowStringCentered(g2d, stringControls, menuHeight + 14 * nextLine, purple);
        shadowStringCentered(g2d, stringQuit, menuHeight + 15 * nextLine, purple);

        if (gameSettings.getActualRoundNumber() == 0) {
            shadowStringCentered(g2d, stringContinue, menuHeight, gameSettings.getColorScheme().getBackgroundFontColor());
        } else if (gameSettings.isGameEnd()) {
            drawGameEnd(g2d, world);
        } else {
            shadowStringCentered(g2d, stringContinue, menuHeight, purple);
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
        int top = menuHeight - 2 * nextLine;            // a little headroom above CONTINUE
        int bottom = menuHeight + 15 * nextLine + pad;  // below QUIT (row 15)
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

    /** Draws {@code text} horizontally centred on the menu block (so value rows sit under their labels). */
    private void shadowStringCentered(Graphics2D g2d, String text, int y, Color color) {
        FontMetrics fm = g2d.getFontMetrics(menuFont);
        int blockCentre = textPosition + fm.stringWidth(stringNewGame) / 2;
        int x = blockCentre - fm.stringWidth(text) / 2;
        shadowString(g2d, text, x, y, color);
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
            case START_NEW_GAME -> 2;
            case PLAYER_NUMBER_OPTION -> 4;
            case ROUND_NUMBER_OPTION -> 6;
            case ROUND_TIME_OPTION -> 8;
            case AI_NUMBER_OPTION -> 10;
            case AI_DIFFICULTY_OPTION -> 12;
            case CONTROLS -> 14;
            case QUIT -> 15;
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

        // While the controls panel is open it captures all input: CONFIRM or PAUSE/ESC returns to the menu.
        if (showingControls) {
            if (input.isJustPressed(GameAction.CONFIRM) || input.isJustPressed(GameAction.PAUSE)) {
                showingControls = false;
            }
            return MenuEnum.NO_OPTION;
        }

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
                gameSettings.setAiNumber(Math.min(aiNumber, playerNumber));
                gameSettings.setAiDifficulty(aiDifficulty);
            }
        } else if (menuOption == MenuEnum.PLAYER_NUMBER_OPTION) {
            playerNumber = changeNumber(input, playerNumber, playerLimit, 1);
            stringPlayerNumber = optionValue(playerNumber);
            choosenOption = MenuEnum.PLAYER_NUMBER_OPTION;
        } else if (menuOption == MenuEnum.ROUND_TIME_OPTION) {
            roundTime = changeNumber(input, roundTime, roundTimeLimit, 5);
            stringRoundTime = optionValue(roundTime);
            choosenOption = MenuEnum.ROUND_TIME_OPTION;
        } else if (menuOption == MenuEnum.ROUND_NUMBER_OPTION) {
            roundNumber = changeNumber(input, roundNumber, roundNumberLimit, 4);
            stringRoundNumber = optionValue(roundNumber);
            choosenOption = MenuEnum.ROUND_NUMBER_OPTION;
        } else if (menuOption == MenuEnum.AI_NUMBER_OPTION) {
            aiNumber = changeAiNumber(input, aiNumber);
            stringAiNumber = optionValue(aiNumber);
            choosenOption = MenuEnum.AI_NUMBER_OPTION;
        } else if (menuOption == MenuEnum.AI_DIFFICULTY_OPTION) {
            aiDifficulty = changeAiDifficulty(input, aiDifficulty);
            stringAiDifficulty = difficultyValue(aiDifficulty.getDisplayName());
            choosenOption = MenuEnum.AI_DIFFICULTY_OPTION;
        } else if (menuOption == MenuEnum.CONTROLS) {
            if (input.isJustPressed(GameAction.CONFIRM)) {
                showingControls = true;
                choosenOption = MenuEnum.CONTROLS;
            }
        } else if (menuOption == MenuEnum.QUIT) {
            if (input.isJustPressed(GameAction.CONFIRM)) {
                choosenOption = MenuEnum.QUIT;
            }
        }

        return choosenOption;
    }

    /** Whether the controls overlay panel is currently shown instead of the option list. */
    public boolean isShowingControls() { return showingControls; }

    /** Pre-selects CONTINUE and closes any controls panel; used when pausing an in-progress game. */
    public void selectContinue() {
        menuOption = MenuEnum.CONTINUE;
        showingControls = false;
    }

    /** The currently highlighted menu option (exposed for state wiring/tests). */
    public MenuEnum getMenuOption() { return menuOption; }

    /** Current player count selected in the menu. */
    public int getPlayerNumber() { return playerNumber; }

    /** Current round limit selected in the menu. */
    public int getRoundNumber() { return roundNumber; }

    /** Current round time (seconds) selected in the menu. */
    public int getRoundTime() { return roundTime; }

    /** Current AI-player count selected in the menu. */
    public int getAiNumber() { return aiNumber; }

    /** Current AI difficulty selected in the menu. */
    public AiDifficulty getAiDifficulty() { return aiDifficulty; }

    /** Width (chars) the DIFFICULTY name is centred within so its chevrons stay symmetric as it changes. */
    private static final int DIFFICULTY_VALUE_WIDTH = 9;

    /** Tight {@code < value >} for numeric options (chevrons close to the number). */
    private String optionValue(int value) {
        return "< " + value + " >";
    }

    /** Difficulty name centred between chevrons so {@code <} / {@code >} stay symmetric across names. */
    private String difficultyValue(String value) {
        int total = Math.max(0, DIFFICULTY_VALUE_WIDTH - value.length());
        int left = total / 2;
        int right = total - left;
        return "< " + " ".repeat(left) + value + " ".repeat(right) + " >";
    }

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

    /** Adjusts the AI-player count in [0, playerNumber], wrapping at the ends. */
    public int changeAiNumber(InputBridge input, int value) {
        if (input.isJustPressed(GameAction.NAVIGATE_LEFT)) {
            value = value <= 0 ? playerNumber : value - 1;
        } else if (input.isJustPressed(GameAction.NAVIGATE_RIGHT)) {
            value = value >= playerNumber ? 0 : value + 1;
        }
        return Math.min(value, playerNumber);
    }

    /** Cycles the AI difficulty left/right through {@link AiDifficulty} values. */
    public AiDifficulty changeAiDifficulty(InputBridge input, AiDifficulty value) {
        AiDifficulty[] all = AiDifficulty.values();
        int idx = value.ordinal();
        if (input.isJustPressed(GameAction.NAVIGATE_LEFT)) {
            idx = (idx - 1 + all.length) % all.length;
        } else if (input.isJustPressed(GameAction.NAVIGATE_RIGHT)) {
            idx = (idx + 1) % all.length;
        }
        return all[idx];
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
            menuOption = MenuEnum.AI_NUMBER_OPTION;
        } else if (menuOption == MenuEnum.AI_NUMBER_OPTION) {
            menuOption = MenuEnum.AI_DIFFICULTY_OPTION;
        } else if (menuOption == MenuEnum.AI_DIFFICULTY_OPTION) {
            menuOption = MenuEnum.CONTROLS;
        } else if (menuOption == MenuEnum.CONTROLS) {
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
        } else if (menuOption == MenuEnum.AI_NUMBER_OPTION) {
            menuOption = MenuEnum.ROUND_TIME_OPTION;
        } else if (menuOption == MenuEnum.AI_DIFFICULTY_OPTION) {
            menuOption = MenuEnum.AI_NUMBER_OPTION;
        } else if (menuOption == MenuEnum.CONTROLS) {
            menuOption = MenuEnum.AI_DIFFICULTY_OPTION;
        } else if (menuOption == MenuEnum.QUIT) {
            menuOption = MenuEnum.CONTROLS;
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
            shadowStringCentered(g2d, stringContinue, menuHeight, Color.green);
        } else if (menuOption == MenuEnum.START_NEW_GAME) {
            shadowStringCentered(g2d, stringNewGame, menuHeight + 2 * nextLine, Color.green);
        } else if (menuOption == MenuEnum.PLAYER_NUMBER_OPTION) {
            shadowStringCentered(g2d, stringPlayerNumber, menuHeight + 4 * nextLine, Color.yellow);
        } else if (menuOption == MenuEnum.ROUND_NUMBER_OPTION) {
            shadowStringCentered(g2d, stringRoundNumber, menuHeight + 6 * nextLine, Color.yellow);
        } else if (menuOption == MenuEnum.ROUND_TIME_OPTION) {
            shadowStringCentered(g2d, stringRoundTime, menuHeight + 8 * nextLine, Color.yellow);
        } else if (menuOption == MenuEnum.AI_NUMBER_OPTION) {
            shadowStringCentered(g2d, stringAiNumber, menuHeight + 10 * nextLine, Color.yellow);
        } else if (menuOption == MenuEnum.AI_DIFFICULTY_OPTION) {
            shadowStringCentered(g2d, stringAiDifficulty, menuHeight + 12 * nextLine, Color.yellow);
        } else if (menuOption == MenuEnum.CONTROLS) {
            shadowStringCentered(g2d, stringControls, menuHeight + 14 * nextLine, Color.green);
        } else if (menuOption == MenuEnum.QUIT) {
            shadowStringCentered(g2d, stringQuit, menuHeight + 15 * nextLine, Color.green);
        }
    }
    /** The four players' rotate-left / rotate-right / shoot actions, in player order. */
    private static final GameAction[][] CONTROL_ACTIONS = {
            {GameAction.P1_ROTATE_LEFT, GameAction.P1_ROTATE_RIGHT, GameAction.P1_SHOOT},
            {GameAction.P2_ROTATE_LEFT, GameAction.P2_ROTATE_RIGHT, GameAction.P2_SHOOT},
            {GameAction.P3_ROTATE_LEFT, GameAction.P3_ROTATE_RIGHT, GameAction.P3_SHOOT},
            {GameAction.P4_ROTATE_LEFT, GameAction.P4_ROTATE_RIGHT, GameAction.P4_SHOOT},
    };

    /**
     * Draws the controls panel: a title, one row per player showing the rotate-left / rotate-right /
     * shoot keys (read live from the {@link InputBridge} so they match the real bindings), and a hint to
     * return. Reuses the menu backdrop style for a consistent look.
     */
    private void drawControls(Graphics2D g2d) {
        g2d.setFont(menuFont);
        drawMenuBackdrop(g2d);

        Color purple = gameSettings.getColorScheme().getDeadLineColor();
        Color label = gameSettings.getColorScheme().getBackgroundFontColor();
        InputBridge input = gameSettings.getInputBridge();

        int x = textPosition - 20;
        int y = menuHeight - nextLine;
        shadowString(g2d, "        CONTROLS", x, y, label);

        shadowString(g2d, " Player   Left   Right   Shoot", x, y + 2 * nextLine, label);
        for (int p = 0; p < CONTROL_ACTIONS.length; p++) {
            String left = input.keyNameFor(CONTROL_ACTIONS[p][0]);
            String right = input.keyNameFor(CONTROL_ACTIONS[p][1]);
            String shoot = input.keyNameFor(CONTROL_ACTIONS[p][2]);
            String row = String.format("  P%d     %-6s %-6s %-6s", p + 1, left, right, shoot);
            shadowString(g2d, row, x, y + (3 + p) * nextLine, purple);
        }

        shadowString(g2d, "   [ ENTER / ESC to return ]", x, y + 8 * nextLine, label);
    }
}
