
package pl.mzebrows.shoots.game.logic;

import pl.mzebrows.shoots.input.GameAction;
import pl.mzebrows.shoots.input.InputBridge;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;

/**
 * Klasa odpowiadając za interfejs menu gry, zmienę ustawień menu, obsługę
 * klawiatury w menu oraz wyświetlenie wyników końcowych
 *
 * @author Mateusz Żebrowski, Nr albumu: 95281
 */
public class GameMenu {

    int width;
    int hight;
    int textPostion;
    int nextLine;
    int menuHight;
    int playerTextPosition = 175;
    int menuScoreHigh;
    Rectangle menuRect;
    MenuEnum menuOption;
    GameSettings gS;
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
        gS = gameSettings;
        width = gS.getDEFAULT_WIDTH();
        hight = gS.getDEFAULT_HIGHT();
        nextLine = 50;
        menuHight = 150;
        menuScoreHigh = 100;
        textPostion = (width / 2) - 50;
        menuFont = gS.getMenuFont().deriveFont(30f);
        winTextColor = gS.getColorScheme().getBackgroundFontColor();
    }

    /**
     * Metoda rysująca na ekranie interfejs menu gry
     *
     * @param g2d parametr pobierający obiekt Graphic2D który rysuje elementy
     * menu
     */
    public void drawMenu(Graphics2D g2d) {
        g2d.setFont(menuFont);

        g2d.setColor(gS.getColorScheme().getDeadLineColor());
        g2d.drawString(stringNewGame, textPostion, menuHight + 3 * nextLine);
        g2d.drawString(stringPlayerNumberText, textPostion, menuHight + 4 * nextLine);
        g2d.drawString(stringPlayerNumber, textPostion, menuHight + 5 * nextLine);
        g2d.drawString(stringRoundLimitText, textPostion, menuHight + 6 * nextLine);
        g2d.drawString(stringRoundNumber, textPostion, menuHight + 7 * nextLine);
        g2d.drawString(stringRoundTimeText, textPostion, menuHight + 8 * nextLine);
        g2d.drawString(stringRoundTime, textPostion, menuHight + 9 * nextLine);
        g2d.drawString(stringQuit, textPostion, menuHight + 12 * nextLine);

        if (gS.getActualRoundNumber() == 0) {
            g2d.setColor(gS.getColorScheme().getBackgroundFontColor());
            g2d.drawString(stringContinue, textPostion, menuHight);
        } else if (gS.isGameEnd()) {
            drawGameEnd(g2d);
        } else {
            g2d.drawString(stringContinue, textPostion, menuHight);
        }

        drawChoosenMenuOption(g2d);
    }

    /**
     * Metoda odpowiedzialna za rysowanie wyników końcowych gry
     *
     * @param g2d parametr pobierający obiekt Graphic2D który rysuje odpowiednie
     * elementy na ekranie gry menu
     */
    public void drawGameEnd(Graphics2D g2d) {
        g2d.drawString("Rounds: ", playerTextPosition - 150, menuScoreHigh + 50);
        g2d.drawString("Points: ", playerTextPosition - 150, menuScoreHigh + 100);

        for (int i = 0; i < gS.getPlayerList().size(); i++) {

            if (textBrighter) {
                winTextColor = new Color(iterator / 2, 2 * iterator, iterator / 2);

                iterator++;
                if (iterator == 100) {
                    textBrighter = false;
                    //winTextColor = new Color(iterator, iterator, iterator);
                }
            } else {
                winTextColor = new Color(iterator / 2, 2 * iterator, iterator / 2);

                iterator--;
                if (iterator == 50) {
                    textBrighter = true;
                    // winTextColor = gS.getColorScheme().getBackgroundFontColor();
                    //winTextColor = new Color(iterator, iterator, iterator);
                }
            }

            g2d.setColor(winTextColor);
            g2d.drawString("       WINNER(s) !     ", textPostion, menuHight - 2 * nextLine);

            if (gS.getPlayerList().get(i).isWinner()) {
                g2d.setColor(winTextColor);
            } else {
                g2d.setColor(gS.getColorScheme().getDeadLineColor());
            }

            g2d.drawString(gS.getPlayerList().get(i).getName(), playerTextPosition + 200 * i, menuScoreHigh);
            g2d.drawString("" + gS.getPlayerList().get(i).getRoundsWon(), playerTextPosition + 200 * i, menuScoreHigh + 50);
            g2d.drawString("" + gS.getPlayerList().get(i).allPointsErned, playerTextPosition + 200 * i, menuScoreHigh + 100);

        }
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
                gS.setRoundTime(roundTime);
                gS.setPlayerNumber(playerNumber);
                gS.setRoundLimit(roundNumber);
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

    /**
     * Metoda służaca do zmiany podświetlanego elementu menu przy zmianię opcji
     * menu w górę
     */
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
            if (gS.getActualRoundNumber() != 0 && !gS.isGameEnd()) {
                menuOption = MenuEnum.CONTINUE;
            } else {
                menuOption = MenuEnum.START_NEW_GAME;
            }
        }

    }

    /**
     * Metoda służaca do zmiany podświetlanego elementu menu przy zmianię opcji
     * menu w dół
     */
    public void changeMenuOptionUp() {

        if (menuOption == MenuEnum.CONTINUE) {
            menuOption = MenuEnum.QUIT;
        } else if (menuOption == MenuEnum.START_NEW_GAME) {
            if (gS.getActualRoundNumber() != 0 && !gS.isGameEnd()) {
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
     * Metoda odpowiedzialna za podświetlenie aktualnie wybranego elementu menu
     *
     * @param g2d parametr pobierający obiekt Graphic2D który rysuje odpowiednie
     * elementy na ekranie gry
     */
    public void drawChoosenMenuOption(Graphics2D g2d) {

        g2d.setColor(Color.green);
        if (menuOption == MenuEnum.CONTINUE) {
            g2d.drawString(stringContinue, textPostion, menuHight);
        } else if (menuOption == MenuEnum.START_NEW_GAME) {
            g2d.drawString(stringNewGame, textPostion, menuHight + 3 * nextLine);
        } else if (menuOption == MenuEnum.PLAYER_NUMBER_OPTION) {
            g2d.setColor(Color.yellow);
            g2d.drawString(stringPlayerNumber, textPostion, menuHight + 5 * nextLine);
        } else if (menuOption == MenuEnum.ROUND_NUMBER_OPTION) {
            g2d.setColor(Color.yellow);
            g2d.drawString(stringRoundNumber, textPostion, menuHight + 7 * nextLine);
        } else if (menuOption == MenuEnum.ROUND_TIME_OPTION) {
            g2d.setColor(Color.yellow);
            g2d.drawString(stringRoundTime, textPostion, menuHight + 9 * nextLine);
        } else if (menuOption == MenuEnum.QUIT) {
            g2d.drawString(stringQuit, textPostion, menuHight + 12 * nextLine);
        }
    }
}
