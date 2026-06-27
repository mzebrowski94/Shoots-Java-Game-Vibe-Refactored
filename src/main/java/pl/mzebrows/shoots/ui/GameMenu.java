// pl/mzebrows/shoots/ui/GameMenu.java
package pl.mzebrows.shoots.ui;

import pl.mzebrows.shoots.app.GameSettings;

import pl.mzebrows.shoots.ai.AiDifficulty;
import pl.mzebrows.shoots.config.GameConfig;
import pl.mzebrows.shoots.config.MenuConfig;
import pl.mzebrows.shoots.config.OnlineConfig;
import pl.mzebrows.shoots.config.RoundConfig;
import pl.mzebrows.shoots.input.GameAction;
import pl.mzebrows.shoots.net.DiscoveredMatch;
import pl.mzebrows.shoots.net.LanSearch;
import pl.mzebrows.shoots.net.LobbyJoiner;
import pl.mzebrows.shoots.net.OnlineLobby;
import pl.mzebrows.shoots.net.OnlineSession;
import pl.mzebrows.shoots.world.PlayWorld;
import pl.mzebrows.shoots.input.InputBridge;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.io.IOException;
import java.net.SocketException;

/** The game menu: renders the menu UI, applies setting changes, handles menu input, and shows final results. */
public class GameMenu {

    private final int width;
    private final int height;
    /** Vertical gap between consecutive menu rows; the layout unit for the whole menu (from config). */
    private final int nextLine;
    /** Top of the option block this frame; recomputed each draw so the panel stays vertically centred. */
    private int menuHeight;
    MenuEnum menuOption;
    private final GameSettings gameSettings;
    private final MenuConfig menuConfig;
    private final Font menuFont;

    // Menu chrome colours + corner radius, sourced from the rendering config (graphic.properties).
    private final Color menuLabelColor;
    private final Color menuSublabelColor;
    private final Color menuValueColor;
    private final Color menuSeparatorColor;
    private final Color menuPanelFill;
    private final Color menuPanelBorder;
    private final Color menuPanelGlow;
    private final Color menuHighlightFill;
    private final Color menuHighlightBorder;
    private final Color menuShadow;
    private final int panelArc;
    int iterator = WIN_PULSE_MIN;
    boolean textBrighter = true;
    Color winTextColor;

    private int roundTime;
    private final int roundTimeLimit;
    private final int roundTimeStep;

    private final int playerLimit;
    private int playerNumber;

    private int roundNumber;
    private final int roundNumberLimit;
    private final int roundNumberStep;

    private int aiNumber;
    AiDifficulty aiDifficulty = AiDifficulty.NORMAL;

    String stringContinue = "[ CONTINUE ]";
    String stringNewGame = "[ START NEW GAME ]";
    String stringPlayOnline = "[ PLAY ONLINE ]";
    String stringPlayerNumberText = "- Player Number -";
    String stringRoundLimitText = "- Round Limit -";
    String stringRoundTimeText = "- Round Time -";
    String stringQuit = "[ QUIT ]";
    String stringControls = "[ CONTROLS ]";

    /** When true, the menu shows the all-players controls panel instead of the option list. */
    boolean showingControls = false;

    /** Which online sub-screen is showing (NONE = the normal option list / no online overlay). */
    OnlineScreen onlineScreen = OnlineScreen.NONE;
    /** Highlighted row in the connect sub-screen: 0 HOST, 1 JOIN LAN, 2 JOIN ONLINE. */
    int connectIndex = 0;
    /** Connection defaults (port + default JOIN ONLINE IP) read from {@code game.properties}. */
    private final OnlineConfig onlineConfig = OnlineConfig.load();
    /** Local player display name advertised to peers. */
    private final String playerName = System.getProperty("user.name", "Player");
    /** Live host/client waiting room (null until HOST/JOIN selected); LAN search + background connect handles. */
    private OnlineLobby lobby;
    private LanSearch lanSearch;
    private LobbyJoiner joiner;
    /** Set once the match begins; consumed by {@link #takeStartedSession()} as the menu hands off to play. */
    private OnlineSession startedSession;
    /** Transient message shown on the online screens (e.g. a failed connect). */
    private String onlineError;
    /** Frame counter driving the search spinner sweep (advanced each draw). */
    private int spinnerFrame;

    String stringPlayerNumber;
    String stringRoundNumber;
    String stringRoundTime;
    String stringAiNumberText = "- AI Players -";
    String stringAiNumber;
    String stringAiDifficultyText = "- AI Difficulty -";
    String stringAiDifficulty;

    GameMenu(GameSettings gameSettings) {
        this.menuOption = MenuEnum.START_NEW_GAME;
        this.gameSettings = gameSettings;
        this.menuConfig = gameSettings.getConfig().menu();
        var theme = gameSettings.getGraphics().menu();
        this.menuLabelColor = theme.label().toAwt();
        this.menuSublabelColor = theme.sublabel().toAwt();
        this.menuValueColor = theme.value().toAwt();
        this.menuSeparatorColor = theme.separator().toAwt();
        this.menuPanelFill = theme.panelFill().toAwt();
        this.menuPanelBorder = theme.panelBorder().toAwt();
        this.menuPanelGlow = theme.panelGlow().toAwt();
        this.menuHighlightFill = theme.highlightFill().toAwt();
        this.menuHighlightBorder = theme.highlightBorder().toAwt();
        this.menuShadow = theme.shadow().toAwt();
        this.panelArc = theme.panelArc();
        this.width = gameSettings.getDefaultWidth();
        this.height = gameSettings.getDefaultHeight();
        this.nextLine = menuConfig.rowSpacing();
        this.menuFont = gameSettings.getMenuFont();
        this.winTextColor = gameSettings.getColorScheme().getBackgroundFontColor();

        this.playerNumber = menuConfig.initialPlayers();
        this.playerLimit = menuConfig.maxPlayers();
        this.roundTime = menuConfig.initialRoundTime();
        this.roundTimeLimit = menuConfig.maxRoundTime();
        this.roundTimeStep = menuConfig.roundTimeStep();
        this.roundNumber = menuConfig.initialRoundLimit();
        this.roundNumberLimit = menuConfig.maxRoundLimit();
        this.roundNumberStep = menuConfig.roundLimitStep();
        this.aiNumber = menuConfig.initialAiPlayers();

        this.stringPlayerNumber = optionValue(playerNumber);
        this.stringRoundNumber = optionValue(roundNumber);
        this.stringRoundTime = optionValue(roundTime);
        this.stringAiNumber = optionValue(aiNumber);
        this.stringAiDifficulty = difficultyValue(aiDifficulty.getDisplayName());
    }

    /**
     * Draws the menu UI.
     *
     * @param g2d the Graphics2D used to draw the elements
     */
    public void drawMenu(Graphics2D g2d, PlayWorld world) {
        g2d.setFont(menuFont);

        // Vertically centre the option block in the window each frame so the panel is truly centred.
        // Computed up-front so the controls overlay (which shares the backdrop) is centred too.
        menuHeight = centeredMenuTop();

        if (showingControls) {
            drawControls(g2d);
            return;
        }

        if (onlineScreen != OnlineScreen.NONE) {
            drawOnline(g2d);
            return;
        }

        // UX: anchor the menu on a dark rounded backdrop so text keeps consistent
        // contrast regardless of the game frame behind it.
        drawMenuBackdrop(g2d);

        // Subtle highlight bar behind the currently selected row, for clear focus.
        drawSelectionHighlight(g2d);

        // Theme colours (from graphic.properties): action labels, section sub-labels, idle value rows.
        Color label = menuLabelColor;
        Color sublabel = menuSublabelColor;
        Color valueIdle = menuValueColor;

        shadowStringCentered(g2d, stringNewGame, rowY(ROW_NEW_GAME), label);
        shadowStringCentered(g2d, stringPlayOnline, rowY(ROW_PLAY_ONLINE), label);
        shadowStringCentered(g2d, stringPlayerNumberText, rowY(ROW_PLAYER_LABEL), sublabel);
        shadowStringCentered(g2d, stringPlayerNumber, rowY(ROW_PLAYER_VALUE), valueIdle);
        shadowStringCentered(g2d, stringRoundLimitText, rowY(ROW_ROUND_LIMIT_LABEL), sublabel);
        shadowStringCentered(g2d, stringRoundNumber, rowY(ROW_ROUND_LIMIT_VALUE), valueIdle);
        shadowStringCentered(g2d, stringRoundTimeText, rowY(ROW_ROUND_TIME_LABEL), sublabel);
        shadowStringCentered(g2d, stringRoundTime, rowY(ROW_ROUND_TIME_VALUE), valueIdle);
        // AI section separator -- draws attention so players don't miss these options.
        shadowStringCentered(g2d, "--- AI Settings ---", rowY(ROW_AI_SEPARATOR), menuSeparatorColor);
        shadowStringCentered(g2d, stringAiNumberText, rowY(ROW_AI_NUMBER_LABEL), sublabel);
        shadowStringCentered(g2d, stringAiNumber, rowY(ROW_AI_NUMBER_VALUE), valueIdle);
        shadowStringCentered(g2d, stringAiDifficultyText, rowY(ROW_AI_DIFFICULTY_LABEL), sublabel);
        shadowStringCentered(g2d, stringAiDifficulty, rowY(ROW_AI_DIFFICULTY_VALUE), valueIdle);
        shadowStringCentered(g2d, stringControls, rowY(ROW_CONTROLS), label);
        shadowStringCentered(g2d, stringQuit, rowY(ROW_QUIT), label);

        if (gameSettings.getActualRoundNumber() == 0) {
            shadowStringCentered(g2d, stringContinue, rowY(ROW_CONTINUE), gameSettings.getColorScheme().getBackgroundFontColor());
        } else if (gameSettings.isGameEnd()) {
            drawGameEnd(g2d, world);
        } else {
            shadowStringCentered(g2d, stringContinue, rowY(ROW_CONTINUE), label);
        }

        drawChoosenMenuOption(g2d);
    }

    /** Baseline y (px) of option row {@code row}, measured in {@link #nextLine} units from {@link #menuHeight}. */
    private int rowY(int row) {
        return menuHeight + (row + optionRowOffset()) * nextLine;
    }

    /** Extra rows the option list is pushed down on the end screen to clear the scoreboard above it. */
    private int optionRowOffset() {
        return gameSettings.isGameEnd() ? END_SCREEN_OPTION_OFFSET_ROWS : 0;
    }

    /** Whole-window horizontal centre in this canvas's coords. The menu paints only on the playfield
     *  canvas, but the right-side stats panel shifts the window's true centre right; centring on it
     *  keeps the menu from sitting left-of-centre. */
    private int menuCenterX() {
        return (width + gameSettings.getDefaultPointerWidth()) / 2;
    }

    /** Total height (px) of the menu backdrop: headroom + all option rows + bottom padding. */
    private int panelHeight() {
        return (PANEL_TOP_ROWS + ROW_QUIT + optionRowOffset()) * nextLine + PANEL_BOTTOM_PAD;
    }

    /** Top baseline (px) of the option block. Centres the backdrop on the whole window vertically when
     *  there is room (the top counter panel biases the centre up); otherwise centres it in the playfield. */
    private int centeredMenuTop() {
        int panelH = panelHeight();
        int fit = height - panelH;
        int panelTop;
        if (fit <= 2 * panelScreenMargin()) {
            panelTop = fit / 2;                       // panel nearly fills the playfield: just centre it
        } else {
            int counter = gameSettings.getDefaultCounterHeight();
            int windowCentred = (height + counter - panelH) / 2 - counter;
            int maxTop = height - panelScreenMargin() - panelH;
            panelTop = Math.max(panelScreenMargin(), Math.min(windowCentred, maxTop));
        }
        return panelTop + PANEL_TOP_ROWS * nextLine;
    }

    /** Horizontal padding between the menu panel edge and its content (from config). */
    private int panelPadX() {
        return menuConfig.panelPadX();
    }

    /** Minimum gap kept between the panel and the window edges (from config). */
    private int panelScreenMargin() {
        return menuConfig.panelMargin();
    }

    /** Max players the scoreboard ever shows; the game-end panel is always sized for this many columns. */
    private int maxPlayers() {
        return menuConfig.maxPlayers();
    }

    // --- Win-text pulse animation (end-of-game "WINNER(s)!" colour ramps between these bounds). ---
    private static final int WIN_PULSE_MIN = 50;
    private static final int WIN_PULSE_MAX = 100;

    /** Rows the option list is pushed down on the end screen so the scoreboard above it never overlaps. */
    private static final int END_SCREEN_OPTION_OFFSET_ROWS = 1;

    // --- Option-block row layout, in nextLine units measured from menuHeight (row 0 = CONTINUE). ---
    // START NEW GAME sits directly under CONTINUE (no blank row), freeing row 2 for PLAY ONLINE; the
    // setup rows below are unchanged, so the panel height is identical to before.
    private static final int ROW_CONTINUE = 0;
    private static final int ROW_NEW_GAME = 1;
    private static final int ROW_PLAY_ONLINE = 2;
    private static final int ROW_PLAYER_LABEL = 3;
    private static final int ROW_PLAYER_VALUE = 4;
    private static final int ROW_ROUND_LIMIT_LABEL = 5;
    private static final int ROW_ROUND_LIMIT_VALUE = 6;
    private static final int ROW_ROUND_TIME_LABEL = 7;
    private static final int ROW_ROUND_TIME_VALUE = 8;
    private static final int ROW_AI_SEPARATOR = 9;
    private static final int ROW_AI_NUMBER_LABEL = 10;
    private static final int ROW_AI_NUMBER_VALUE = 11;
    private static final int ROW_AI_DIFFICULTY_LABEL = 12;
    private static final int ROW_AI_DIFFICULTY_VALUE = 13;
    private static final int ROW_CONTROLS = 15;
    private static final int ROW_QUIT = 16;

    /** Players are added/removed one at a time when cycling the player-count option. */
    private static final int PLAYER_STEP = 1;

    /** Rows of headroom kept above the CONTINUE row inside the backdrop. */
    private static final int PANEL_TOP_ROWS = 2;
    /** Extra padding (px) below the last row before the backdrop bottom edge. */
    private static final int PANEL_BOTTOM_PAD = 24;
    /** Widest menu row (measured) plus padding on both sides -- the base panel width. */
    private int menuRowsWidth(Graphics2D g2d) {
        FontMetrics fm = g2d.getFontMetrics(menuFont);
        int widest = 0;
        for (String row : new String[]{stringContinue, stringNewGame, stringPlayOnline, stringPlayerNumberText,
                stringRoundLimitText, stringRoundTimeText, stringAiNumberText, stringAiDifficultyText,
                "--- AI Settings ---", stringQuit, "       WINNER(s) !     "}) {
            widest = Math.max(widest, fm.stringWidth(row));
        }
        return widest + 2 * panelPadX();
    }

    /** Horizontal padding (px) inside a scoreboard column, around the value text. */
    private static final int SCORE_COL_PAD = 44;

    /**
     * Panel width. On the game-end screen it is widened to hold the label column plus every score
     * column; otherwise it is just the widest menu row. Always clamped to fit on-screen.
     */
    private int panelWidth(Graphics2D g2d) {
        int w = menuRowsWidth(g2d);
        if (showingControls) {
            w = Math.max(w, controlsWidth(g2d));
        }
        if (onlineScreen != OnlineScreen.NONE) {
            w = Math.max(w, onlineWidth(g2d));
        }
        if (gameSettings.isGameEnd()) {
            w = Math.max(w, scoreboardWidth(g2d));
        }
        return Math.min(w, width - 2 * panelScreenMargin());
    }

    /** Width (px) reserved at the scoreboard's left for the "Rounds:/Points:" labels (measured + a gap). */
    private int scoreLabelSpan(Graphics2D g2d) {
        FontMetrics fm = g2d.getFontMetrics(menuFont);
        return Math.max(fm.stringWidth("Rounds: "), fm.stringWidth("Points: ")) + SCORE_COL_PAD;
    }

    /** Backdrop width needed to hold the end-game scoreboard: label column + one column per player. */
    private int scoreboardWidth(Graphics2D g2d) {
        FontMetrics fm = g2d.getFontMetrics(menuFont);
        return scoreLabelSpan(g2d) + maxPlayers() * (fm.stringWidth("00") + SCORE_COL_PAD) + 2 * panelPadX();
    }

    /** Backdrop width needed to hold the controls table (its header or widest key row) plus padding. */
    private int controlsWidth(Graphics2D g2d) {
        FontMetrics fm = g2d.getFontMetrics(menuFont);
        int widest = fm.stringWidth(CONTROLS_HEADER);
        for (String row : buildControlRows()) {
            widest = Math.max(widest, fm.stringWidth(row));
        }
        return widest + 2 * panelPadX();
    }

    /** Left edge of the menu panel: centred on the screen horizontally, clamped on-screen. */
    private int panelLeft(Graphics2D g2d) {
        int w = panelWidth(g2d);
        int x = menuCenterX() - w / 2;
        int maxX = width - panelScreenMargin() - w;
        return Math.max(panelScreenMargin(), Math.min(x, maxX));
    }

    /** Top edge (px) of the menu backdrop: {@link #PANEL_TOP_ROWS} of headroom above the CONTINUE row. */
    private int panelTop() {
        return menuHeight - PANEL_TOP_ROWS * nextLine;
    }

    /** Dark navy rounded panel behind the menu items to lift text contrast. */
    private void drawMenuBackdrop(Graphics2D g2d) {
        int top = panelTop();
        int x = panelLeft(g2d);
        int w = panelWidth(g2d);
        int h = panelHeight();
        // Dark navy tint -- lighter than pure black so text colours pop against it.
        g2d.setColor(menuPanelFill);
        g2d.fillRoundRect(x, top, w, h, panelArc, panelArc);
        // Subtle violet border to tie into the game's purple palette.
        g2d.setColor(menuPanelBorder);
        g2d.drawRoundRect(x, top, w, h, panelArc, panelArc);
        // Inner border glow for depth.
        g2d.setColor(menuPanelGlow);
        g2d.drawRoundRect(x + 2, top + 2, w - 4, h - 4, panelArc - 2, panelArc - 2);
    }

    /** Draws a soft drop shadow then the coloured text; never changes the requested text colour. */
    private void shadowString(Graphics2D g2d, String text, int x, int y, Color color) {
        g2d.setColor(menuShadow);
        g2d.drawString(text, x + 2, y + 2);
        g2d.setColor(color);
        g2d.drawString(text, x, y);
    }

    /** Draws {@code text} horizontally centred on the screen. */
    private void shadowStringCentered(Graphics2D g2d, String text, int y, Color color) {
        FontMetrics fm = g2d.getFontMetrics(menuFont);
        int x = menuCenterX() - fm.stringWidth(text) / 2;
        shadowString(g2d, text, x, y, color);
    }

    /** Soft filled bar behind the selected row so the green/yellow choice reads as focused. */
    private void drawSelectionHighlight(Graphics2D g2d) {
        int row = selectedRow();
        if (row < 0) {
            return;
        }
        FontMetrics fm = g2d.getFontMetrics(menuFont);
        int y = rowY(row);
        int barX = panelLeft(g2d) + 8;
        int barW = panelWidth(g2d) - 16;
        int barY = y - fm.getAscent() - 2;
        int barH = fm.getHeight() + 8;
        // Brighter highlight so the selected row is unmistakably visible.
        g2d.setColor(menuHighlightFill);
        g2d.fillRoundRect(barX, barY, barW, barH, 12, 12);
        // Thin violet rim around the highlight.
        g2d.setColor(menuHighlightBorder);
        g2d.drawRoundRect(barX, barY, barW, barH, 12, 12);
    }

    /** Row index (in nextLine units from menuHeight) of the selected option, or -1 if none. */
    private int selectedRow() {
        return switch (menuOption) {
            case CONTINUE -> ROW_CONTINUE;
            case START_NEW_GAME -> ROW_NEW_GAME;
            case PLAY_ONLINE -> ROW_PLAY_ONLINE;
            case PLAYER_NUMBER_OPTION -> ROW_PLAYER_VALUE;
            case ROUND_NUMBER_OPTION -> ROW_ROUND_LIMIT_VALUE;
            case ROUND_TIME_OPTION -> ROW_ROUND_TIME_VALUE;
            case AI_NUMBER_OPTION -> ROW_AI_NUMBER_VALUE;
            case AI_DIFFICULTY_OPTION -> ROW_AI_DIFFICULTY_VALUE;
            case CONTROLS -> ROW_CONTROLS;
            case QUIT -> ROW_QUIT;
            default -> -1;
        };
    }

    /**
     * Draws the end-of-game results.
     *
     * @param g2d the Graphics2D used to draw the elements
     */
    public void drawGameEnd(Graphics2D g2d, PlayWorld world) {
        // Lay the scoreboard out INSIDE the centred panel: the row labels sit at the panel's left padding
        // and the player columns are distributed evenly across the remaining width, so every column
        // (including the last player) stays within the backdrop regardless of player count. Vertical
        // anchoring is derived from the panel so the scoreboard is centred along with the panel, not
        // pinned to a hard-coded screen offset.
        int panelX = panelLeft(g2d);
        int panelW = panelWidth(g2d);
        int labelX = panelX + panelPadX();
        int colsLeft = labelX + scoreLabelSpan(g2d);
        int colsRight = panelX + panelW - panelPadX();

        // Scoreboard occupies the panel's top rows (measured in the menu row unit so it lines up with the
        // rest of the panel); the option list is pushed below it by optionRowOffset() so they never overlap.
        int headerY = panelTop() + nextLine;
        int idsY = headerY + nextLine;
        int roundsY = headerY + 2 * nextLine;
        int pointsY = headerY + 3 * nextLine;

        Color labelColor = gameSettings.getColorScheme().getBackgroundFontColor();
        shadowString(g2d, "Rounds: ", labelX, roundsY, labelColor);
        shadowString(g2d, "Points: ", labelX, pointsY, labelColor);

        if (world == null) {
            return;
        }

        advanceWinPulse();

        // Animated green "WINNER" colour preserved; shadow added for legibility; centred over the panel.
        FontMetrics fm = g2d.getFontMetrics(menuFont);
        String winner = "WINNER(s) !";
        int winnerX = panelX + (panelW - fm.stringWidth(winner)) / 2;
        shadowString(g2d, winner, winnerX, headerY, winTextColor);

        // Distribute up to maxPlayers() columns evenly across [colsLeft, colsRight]; draw only active ones.
        int players = world.playerCount();
        int slotWidth = (colsRight - colsLeft) / maxPlayers();
        for (int i = 0; i < players; i++) {
            var score = world.matchFlow().scoreOf(i);
            int colCentre = colsLeft + slotWidth * i + slotWidth / 2;
            Color scoreColor = score.isWinner() ? winTextColor : gameSettings.getColorScheme().getDeadLineColor();
            drawScoreCell(g2d, "P" + (i + 1), colCentre, idsY, scoreColor, fm);
            drawScoreCell(g2d, "" + score.getRoundsWon(), colCentre, roundsY, scoreColor, fm);
            drawScoreCell(g2d, "" + score.getTotalPoints(), colCentre, pointsY, scoreColor, fm);
        }
    }

    /** Advances the end-of-game "WINNER(s)!" colour pulse one frame, bouncing between the pulse bounds. */
    private void advanceWinPulse() {
        if (textBrighter) {
            iterator++;
            if (iterator >= WIN_PULSE_MAX) {
                textBrighter = false;
            }
        } else {
            iterator--;
            if (iterator <= WIN_PULSE_MIN) {
                textBrighter = true;
            }
        }
        winTextColor = new Color(iterator / 2, 2 * iterator, iterator / 2);
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

        // While any online sub-screen is open it captures all input and drives the lobby network state.
        if (onlineScreen != OnlineScreen.NONE) {
            return checkOnlineInput(input);
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
        } else if (menuOption == MenuEnum.PLAY_ONLINE) {
            if (input.isJustPressed(GameAction.CONFIRM)) {
                openConnectMenu();
                choosenOption = MenuEnum.PLAY_ONLINE;
            }
        } else if (menuOption == MenuEnum.PLAYER_NUMBER_OPTION) {
            playerNumber = changeNumber(input, playerNumber, playerLimit, PLAYER_STEP);
            stringPlayerNumber = optionValue(playerNumber);
            choosenOption = MenuEnum.PLAYER_NUMBER_OPTION;
        } else if (menuOption == MenuEnum.ROUND_TIME_OPTION) {
            roundTime = changeNumber(input, roundTime, roundTimeLimit, roundTimeStep);
            stringRoundTime = optionValue(roundTime);
            choosenOption = MenuEnum.ROUND_TIME_OPTION;
        } else if (menuOption == MenuEnum.ROUND_NUMBER_OPTION) {
            roundNumber = changeNumber(input, roundNumber, roundNumberLimit, roundNumberStep);
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
            menuOption = MenuEnum.PLAY_ONLINE;
        } else if (menuOption == MenuEnum.PLAY_ONLINE) {
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
        } else if (menuOption == MenuEnum.PLAY_ONLINE) {
            menuOption = MenuEnum.START_NEW_GAME;
        } else if (menuOption == MenuEnum.PLAYER_NUMBER_OPTION) {
            menuOption = MenuEnum.PLAY_ONLINE;
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
        // Bright green for action rows (CONTINUE, START, CONTROLS, QUIT); warm yellow for value rows.
        if (menuOption == MenuEnum.CONTINUE) {
            shadowStringCentered(g2d, stringContinue, rowY(ROW_CONTINUE), Color.green);
        } else if (menuOption == MenuEnum.START_NEW_GAME) {
            shadowStringCentered(g2d, stringNewGame, rowY(ROW_NEW_GAME), Color.green);
        } else if (menuOption == MenuEnum.PLAY_ONLINE) {
            shadowStringCentered(g2d, stringPlayOnline, rowY(ROW_PLAY_ONLINE), Color.green);
        } else if (menuOption == MenuEnum.PLAYER_NUMBER_OPTION) {
            shadowStringCentered(g2d, stringPlayerNumber, rowY(ROW_PLAYER_VALUE), Color.yellow);
        } else if (menuOption == MenuEnum.ROUND_NUMBER_OPTION) {
            shadowStringCentered(g2d, stringRoundNumber, rowY(ROW_ROUND_LIMIT_VALUE), Color.yellow);
        } else if (menuOption == MenuEnum.ROUND_TIME_OPTION) {
            shadowStringCentered(g2d, stringRoundTime, rowY(ROW_ROUND_TIME_VALUE), Color.yellow);
        } else if (menuOption == MenuEnum.AI_NUMBER_OPTION) {
            shadowStringCentered(g2d, stringAiNumber, rowY(ROW_AI_NUMBER_VALUE), Color.yellow);
        } else if (menuOption == MenuEnum.AI_DIFFICULTY_OPTION) {
            shadowStringCentered(g2d, stringAiDifficulty, rowY(ROW_AI_DIFFICULTY_VALUE), Color.yellow);
        } else if (menuOption == MenuEnum.CONTROLS) {
            shadowStringCentered(g2d, stringControls, rowY(ROW_CONTROLS), Color.green);
        } else if (menuOption == MenuEnum.QUIT) {
            shadowStringCentered(g2d, stringQuit, rowY(ROW_QUIT), Color.green);
        }
    }

    /** The four players' rotate-left / rotate-right / shoot actions, in player order. */
    private static final GameAction[][] CONTROL_ACTIONS = {
            {GameAction.P1_ROTATE_LEFT, GameAction.P1_ROTATE_RIGHT, GameAction.P1_SHOOT},
            {GameAction.P2_ROTATE_LEFT, GameAction.P2_ROTATE_RIGHT, GameAction.P2_SHOOT},
            {GameAction.P3_ROTATE_LEFT, GameAction.P3_ROTATE_RIGHT, GameAction.P3_SHOOT},
            {GameAction.P4_ROTATE_LEFT, GameAction.P4_ROTATE_RIGHT, GameAction.P4_SHOOT},
    };

    /** Header for the controls table; player rows are formatted to align beneath these columns. */
    private static final String CONTROLS_HEADER = "Player   Left   Right   Shoot";
    /** Fixed-width row format so the key columns line up under {@link #CONTROLS_HEADER}. */
    private static final String CONTROLS_ROW_FORMAT = "  P%d     %-6s %-6s %-6s";

    /** Builds the per-player controls rows from the live key bindings, formatted to align under the header. */
    private String[] buildControlRows() {
        InputBridge input = gameSettings.getInputBridge();
        String[] rows = new String[CONTROL_ACTIONS.length];
        for (int p = 0; p < CONTROL_ACTIONS.length; p++) {
            rows[p] = String.format(CONTROLS_ROW_FORMAT, p + 1,
                    input.keyNameFor(CONTROL_ACTIONS[p][0]),
                    input.keyNameFor(CONTROL_ACTIONS[p][1]),
                    input.keyNameFor(CONTROL_ACTIONS[p][2]));
        }
        return rows;
    }

    /**
     * Draws the controls panel: a title, one row per player showing the rotate-left / rotate-right /
     * shoot keys (read live from the {@link InputBridge} so they match the real bindings), and a hint to
     * return. Reuses the centred menu backdrop; the table is centred as a block on the screen so it lines
     * up with the panel instead of being pinned left of centre.
     */
    private void drawControls(Graphics2D g2d) {
        g2d.setFont(menuFont);
        drawMenuBackdrop(g2d);

        Color purple = gameSettings.getColorScheme().getDeadLineColor();
        Color label = gameSettings.getColorScheme().getBackgroundFontColor();

        String[] rows = buildControlRows();

        // Left edge that centres the table block (its widest row) on the menu's centre.
        FontMetrics fm = g2d.getFontMetrics(menuFont);
        int widest = fm.stringWidth(CONTROLS_HEADER);
        for (String row : rows) {
            widest = Math.max(widest, fm.stringWidth(row));
        }
        int blockX = menuCenterX() - widest / 2;

        // First row sits just below the panel's top headroom; the rest follow row by row.
        int y = panelTop() + PANEL_TOP_ROWS * nextLine;
        shadowStringCentered(g2d, "CONTROLS", y, label);
        shadowString(g2d, CONTROLS_HEADER, blockX, y + 2 * nextLine, label);
        for (int p = 0; p < rows.length; p++) {
            shadowString(g2d, rows[p], blockX, y + (3 + p) * nextLine, purple);
        }
        shadowStringCentered(g2d, "[ ENTER / ESC to return ]", y + (4 + rows.length) * nextLine, label);
    }

    // -------------------------------------------------------------------------
    // Online play (F7): connect sub-screen, host/client waiting room, LAN/online search.

    /** The online sub-screens, layered over the menu backdrop (NONE = the normal option list). */
    public enum OnlineScreen { NONE, CONNECT_MENU, HOST_LOBBY, JOIN_LAN_SEARCH, JOIN_ONLINE_SEARCH, CLIENT_LOBBY }

    /** Connect-screen rows, in display order (friction order: host, zero-typing LAN, manual internet). */
    private static final String[] CONNECT_ROWS = {"[ HOST ]", "[ JOIN LAN ]", "[ JOIN ONLINE ]"};
    private static final int CONNECT_HOST = 0;
    private static final int CONNECT_JOIN_LAN = 1;
    private static final int CONNECT_JOIN_ONLINE = 2;

    /** Radius (px) of the search spinner ring. */
    private static final int SPINNER_RADIUS = 26;

    /** Whether an online sub-screen is currently shown (exposed for state wiring/tests). */
    public boolean isOnline() {
        return onlineScreen != OnlineScreen.NONE;
    }

    /** The current online sub-screen (exposed for tests). */
    public OnlineScreen getOnlineScreen() {
        return onlineScreen;
    }

    /** The current transient online error message (exposed for tests), or {@code null}. */
    String onlineError() {
        return onlineError;
    }

    /**
     * Hands the started network session to the caller (the paused state, on a {@code START_ONLINE} signal)
     * and clears the online overlay so the menu returns to its normal list behind the starting match.
     */
    public OnlineSession takeStartedSession() {
        OnlineSession session = startedSession;
        startedSession = null;
        // The session now owns the transport/server; drop our lobby/search handles without closing them.
        lobby = null;
        closeQuietly(lanSearch);
        lanSearch = null;
        joiner = null;
        onlineScreen = OnlineScreen.NONE;
        onlineError = null;
        return session;
    }

    private void openConnectMenu() {
        onlineScreen = OnlineScreen.CONNECT_MENU;
        connectIndex = CONNECT_HOST;
        onlineError = null;
    }

    /**
     * Drops a peer straight onto the PLAY ONLINE connect screen with a notice -- used when an in-progress
     * online match ends abnormally (e.g. the host left), so the player lands back where they can re-host or
     * re-join instead of a frozen game (#5).
     */
    public void showOnlineDisconnected(String message) {
        closeOnline();
        onlineScreen = OnlineScreen.CONNECT_MENU;
        connectIndex = CONNECT_HOST;
        onlineError = message;
    }

    /** Drives the active online sub-screen each tick: advances its network state and handles its input. */
    private MenuEnum checkOnlineInput(InputBridge input) {
        return switch (onlineScreen) {
            case CONNECT_MENU -> updateConnectMenu(input);
            case HOST_LOBBY -> updateHostLobby(input);
            case JOIN_LAN_SEARCH -> updateLanSearch(input);
            case JOIN_ONLINE_SEARCH -> updateOnlineSearch(input);
            case CLIENT_LOBBY -> updateClientLobby(input);
            case NONE -> MenuEnum.NO_OPTION;
        };
    }

    private MenuEnum updateConnectMenu(InputBridge input) {
        if (input.isJustPressed(GameAction.PAUSE)) {
            closeOnline();
            return MenuEnum.NO_OPTION;
        }
        if (input.isJustPressed(GameAction.NAVIGATE_DOWN)) {
            connectIndex = (connectIndex + 1) % CONNECT_ROWS.length;
        } else if (input.isJustPressed(GameAction.NAVIGATE_UP)) {
            connectIndex = (connectIndex - 1 + CONNECT_ROWS.length) % CONNECT_ROWS.length;
        }
        if (input.isJustPressed(GameAction.CONFIRM)) {
            switch (connectIndex) {
                case CONNECT_HOST -> startHosting();
                case CONNECT_JOIN_LAN -> startLanSearch();
                case CONNECT_JOIN_ONLINE -> startOnlineSearch();
                default -> { /* unreachable */ }
            }
        }
        return MenuEnum.NO_OPTION;
    }

    private void startHosting() {
        try {
            // #7: size the lobby by the menu's selected player count (online needs >= 2), and carry the menu's
            // round time / limit into the hosted match via the base config.
            int onlinePlayers = Math.max(2, Math.min(menuConfig.maxPlayers(), playerNumber));
            lobby = OnlineLobby.host(onlineBaseConfig(), onlinePlayers, onlineConfig.port(), playerName);
            onlineScreen = OnlineScreen.HOST_LOBBY;
            onlineError = null;
        } catch (IOException e) {
            onlineError = "Could not host: " + e.getMessage();
        }
    }

    /** The base game config with the menu-selected round time / limit overlaid, for an online match (#7). */
    private GameConfig onlineBaseConfig() {
        GameConfig base = gameSettings.getConfig();
        RoundConfig defaults = base.round();
        if (roundNumber == defaults.roundLimit() && roundTime == defaults.roundTimeSeconds()) {
            return base;
        }
        return base.withRound(new RoundConfig(roundTime, roundNumber, defaults.roundEndDelay(), defaults.animationTime()));
    }

    private void startLanSearch() {
        try {
            lanSearch = new LanSearch(onlineConfig.port());
            joiner = null;
            onlineScreen = OnlineScreen.JOIN_LAN_SEARCH;
            onlineError = null;
        } catch (SocketException e) {
            onlineError = "Could not search LAN: " + e.getMessage();
        }
    }

    private void startOnlineSearch() {
        joiner = new LobbyJoiner(gameSettings.getConfig(), onlineConfig.host(), onlineConfig.port(), playerName);
        onlineScreen = OnlineScreen.JOIN_ONLINE_SEARCH;
        onlineError = null;
    }

    private MenuEnum updateHostLobby(InputBridge input) {
        lobby.pump();
        if (input.isJustPressed(GameAction.PAUSE)) {
            // Host leaves the room: closing the server drops every client, sending them back to the main menu.
            closeOnline();
            return MenuEnum.NO_OPTION;
        }
        if (input.isJustPressed(GameAction.CONFIRM) && lobby.startMatch()) {
            startedSession = lobby.takeStarted();
            return MenuEnum.START_ONLINE;
        }
        return MenuEnum.NO_OPTION;
    }

    private MenuEnum updateLanSearch(InputBridge input) {
        if (input.isJustPressed(GameAction.PAUSE)) {
            closeQuietly(lanSearch);
            lanSearch = null;
            closeQuietly(joiner);
            joiner = null;
            openConnectMenu();
            return MenuEnum.NO_OPTION;
        }
        if (joiner == null) {
            if (lanSearch == null) {
                return MenuEnum.NO_OPTION;
            }
            DiscoveredMatch match = lanSearch.poll();
            if (match != null) {
                closeQuietly(lanSearch);
                lanSearch = null;
                joiner = new LobbyJoiner(gameSettings.getConfig(), match.host(), match.port(), playerName);
            }
            return MenuEnum.NO_OPTION;
        }
        return resolveJoiner(true);
    }

    private MenuEnum updateOnlineSearch(InputBridge input) {
        if (input.isJustPressed(GameAction.PAUSE)) {
            closeQuietly(joiner);
            joiner = null;
            openConnectMenu();
            return MenuEnum.NO_OPTION;
        }
        return resolveJoiner(false);
    }

    /** Polls the background connect: enters the client lobby on success; on failure shows an error/retries. */
    private MenuEnum resolveJoiner(boolean lanFailureReturnsToConnect) {
        if (joiner == null) {
            return MenuEnum.NO_OPTION; // a prior connect failed; the error stays shown until ESC
        }
        switch (joiner.status()) {
            case JOINED -> {
                lobby = joiner.lobby();
                joiner = null;
                onlineScreen = OnlineScreen.CLIENT_LOBBY;
            }
            case FAILED -> {
                onlineError = "Could not connect to " + joiner.target();
                closeQuietly(joiner);
                joiner = null;
                if (lanFailureReturnsToConnect) {
                    openConnectMenu();
                }
                // JOIN ONLINE: stay on the search screen showing the error until the user presses ESC.
            }
            case CONNECTING -> { /* keep the spinner going */ }
        }
        return MenuEnum.NO_OPTION;
    }

    private MenuEnum updateClientLobby(InputBridge input) {
        lobby.pump();
        if (lobby.isStarted()) {
            startedSession = lobby.takeStarted();
            return MenuEnum.START_ONLINE;
        }
        if (lobby.hostLeft()) {
            // Host closed the room -> back to the main menu (requirement: all players return to the menu).
            closeOnline();
            return MenuEnum.NO_OPTION;
        }
        if (input.isJustPressed(GameAction.PAUSE)) {
            // Client leaves: closing the transport frees our slot, which the host re-broadcasts as OPEN.
            closeQuietly(lobby);
            lobby = null;
            openConnectMenu();
            return MenuEnum.NO_OPTION;
        }
        return MenuEnum.NO_OPTION;
    }

    /** Tears down every online handle and returns to the normal main-menu option list. */
    private void closeOnline() {
        closeQuietly(lobby);
        lobby = null;
        closeQuietly(lanSearch);
        lanSearch = null;
        closeQuietly(joiner);
        joiner = null;
        onlineScreen = OnlineScreen.NONE;
        onlineError = null;
    }

    private static void closeQuietly(AutoCloseable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (Exception ignored) {
                // best-effort teardown
            }
        }
    }

    // --- online rendering ---------------------------------------------------

    /** Backdrop width needed for the widest online caption (so search/lobby text never clips). */
    private int onlineWidth(Graphics2D g2d) {
        FontMetrics fm = g2d.getFontMetrics(menuFont);
        int widest = 0;
        for (String s : new String[]{"Searching online game at", "[ JOIN ONLINE ]",
                "Match code: ABCXYZ", "Slot 4:  " + sampleName(), "ip number: 000.000.000.000",
                "[ START GAME ]   ", "[ ESC to return ]"}) {
            widest = Math.max(widest, fm.stringWidth(s));
        }
        return widest + 2 * panelPadX();
    }

    private String sampleName() {
        return playerName.length() > 10 ? playerName.substring(0, 10) : playerName;
    }

    /** Routes to the active online sub-screen's renderer (all share the centred menu backdrop). */
    private void drawOnline(Graphics2D g2d) {
        g2d.setFont(menuFont);
        drawMenuBackdrop(g2d);
        switch (onlineScreen) {
            case CONNECT_MENU -> drawConnectMenu(g2d);
            case HOST_LOBBY -> drawHostLobby(g2d);
            case JOIN_LAN_SEARCH -> drawSearchScreen(g2d, true);
            case JOIN_ONLINE_SEARCH -> drawSearchScreen(g2d, false);
            case CLIENT_LOBBY -> drawClientLobby(g2d);
            case NONE -> { /* not reached */ }
        }
    }

    private void drawConnectMenu(Graphics2D g2d) {
        Color label = gameSettings.getColorScheme().getBackgroundFontColor();
        Color dim = menuSublabelColor;
        int y = panelTop() + PANEL_TOP_ROWS * nextLine;
        shadowStringCentered(g2d, "PLAY ONLINE", y, label);
        for (int i = 0; i < CONNECT_ROWS.length; i++) {
            Color c = i == connectIndex ? Color.green : menuLabelColor;
            shadowStringCentered(g2d, CONNECT_ROWS[i], y + (2 + i) * nextLine, c);
        }
        // Read-only JOIN ONLINE target (edited via game.properties, not in-menu).
        shadowStringCentered(g2d, "target: " + onlineConfig.host() + ":" + onlineConfig.port(),
                y + 5 * nextLine, dim);
        if (onlineError != null) {
            shadowStringCentered(g2d, onlineError, y + 6 * nextLine, Color.red);
        }
        shadowStringCentered(g2d, "[ ESC to return ]", y + 8 * nextLine, label);
    }

    private void drawHostLobby(Graphics2D g2d) {
        Color label = gameSettings.getColorScheme().getBackgroundFontColor();
        int y = panelTop() + PANEL_TOP_ROWS * nextLine;
        shadowStringCentered(g2d, "Match code: " + lobby.matchCode(), y, label);
        shadowStringCentered(g2d, "Port: " + lobby.port(), y + nextLine, menuSublabelColor);
        drawSlots(g2d, y + 3 * nextLine, lobby.roster(), lobby.localSlot());

        boolean canStart = lobby.canStart();
        Color startColor = canStart ? Color.green : menuSublabelColor;
        String startText = canStart ? "[ START GAME ]" : "[ START GAME ]  (need 2 players)";
        shadowStringCentered(g2d, startText, y + 8 * nextLine, startColor);
        shadowStringCentered(g2d, "[ ESC to return ]", y + 10 * nextLine, label);
    }

    private void drawClientLobby(Graphics2D g2d) {
        Color label = gameSettings.getColorScheme().getBackgroundFontColor();
        int y = panelTop() + PANEL_TOP_ROWS * nextLine;
        shadowStringCentered(g2d, "Match code: " + lobby.matchCode(), y, label);
        shadowStringCentered(g2d, "Port: " + onlineConfig.port(), y + nextLine, menuSublabelColor);
        drawSlots(g2d, y + 3 * nextLine, lobby.roster(), lobby.localSlot());
        shadowStringCentered(g2d, "Waiting for host to start...", y + 8 * nextLine, menuSublabelColor);
        shadowStringCentered(g2d, "[ ESC to return ]", y + 10 * nextLine, label);
    }

    /** Draws the four (or fewer) player slots; the local player is tagged "(you)", empty slots read OPEN. */
    private void drawSlots(Graphics2D g2d, int topY, String[] roster, int localSlot) {
        for (int s = 0; s < roster.length; s++) {
            String name = roster[s];
            boolean open = name == null || name.isEmpty();
            String suffix = s == localSlot ? "  (you)" : "";
            String text = "Slot " + (s + 1) + ":  " + (open ? "OPEN" : name) + (open ? "" : suffix);
            Color c = open ? menuSublabelColor : menuValueColor;
            shadowStringCentered(g2d, text, topY + s * nextLine, c);
        }
    }

    private void drawSearchScreen(Graphics2D g2d, boolean lan) {
        Color label = gameSettings.getColorScheme().getBackgroundFontColor();
        int y = panelTop() + PANEL_TOP_ROWS * nextLine;
        spinnerFrame++;
        drawSpinner(g2d, menuCenterX(), y + nextLine, spinnerFrame);

        int captionY = y + 3 * nextLine;
        boolean connecting = joiner != null && joiner.status() == LobbyJoiner.Status.CONNECTING;
        if (lan) {
            shadowStringCentered(g2d, "Searching LAN game at", captionY, label);
            shadowStringCentered(g2d, "port: " + onlineConfig.port(), captionY + nextLine, menuSublabelColor);
            if (connecting) {
                shadowStringCentered(g2d, "host found - connecting...", captionY + 2 * nextLine, menuValueColor);
            }
        } else {
            shadowStringCentered(g2d, "Searching online game at", captionY, label);
            shadowStringCentered(g2d, "ip number: " + onlineConfig.host(), captionY + nextLine, menuSublabelColor);
            shadowStringCentered(g2d, "port: " + onlineConfig.port(), captionY + 2 * nextLine, menuSublabelColor);
        }
        if (onlineError != null) {
            shadowStringCentered(g2d, onlineError, captionY + 4 * nextLine, Color.red);
        }
        shadowStringCentered(g2d, "[ ESC to return ]", captionY + 5 * nextLine, label);
    }

    /**
     * Spinning loader: a dim ring with a bright leading arc that sweeps around as {@code frame} advances
     * (the same idea as {@code DisruptionRenderer#drawShield}). Pure decoration -- it gates nothing.
     */
    private void drawSpinner(Graphics2D g2d, int cx, int cy, int frame) {
        Stroke old = g2d.getStroke();
        int d = 2 * SPINNER_RADIUS;
        g2d.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.setColor(menuSeparatorColor);
        g2d.drawOval(cx - SPINNER_RADIUS, cy - SPINNER_RADIUS, d, d);
        int start = (frame * 6) % 360;
        g2d.setColor(Color.green);
        g2d.drawArc(cx - SPINNER_RADIUS, cy - SPINNER_RADIUS, d, d, start, 80);
        g2d.setStroke(old);
    }
}
