
package pl.mzebrows.shoots.game.logic;

import pl.mzebrows.shoots.game.logic.Drawables.LightEffect;

import java.awt.event.KeyEvent;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Klasa stanowiaca główny obiekt w grze, w klasie tej odbywa wywoływanie całej
 * logiki występującej w grze Odpowiedzialna jest, za: -obsługę głównej pętli
 * gry -zmianę stanu rundy -wywoływanie funkcji renderujących grafikę
 * -wywoływanie fukcji odpowiedzialny za obsłuchę klawiatury
 *
 * @author Mateusz Żebrowski, Nr albumu: 95281
 */
public final class GameLoop {

    GameSettings gameSettings = new GameSettings();
    GameFrame gameFrame;

    boolean isRunning;
    final int TARGET_FPS = 120;
    final long OPTIMAL_TIME = 1000000000 / TARGET_FPS;
    long lastLoopTime = System.nanoTime();
    long lastFpsTime = 0;
    RoundEnum roundState, previousRoundState;
    MenuEnum menuState;
    boolean changeRoundState = false;
    boolean roundStateChanged = false;
    boolean gamePaused = false;

    /**
     * Konstruktor pełni rolę głównej pętli gry
     */
    public GameLoop() {
        System.out.println("Starting GameLoop");
        initializeGraphics();
        initializeLogic();
        //int second = 0;

        while (isRunning) {
            long now = System.nanoTime();
            long updateLength = now - lastLoopTime;
            lastLoopTime = now;
            //double delta = updateLength / ((double) OPTIMAL_TIME);

            lastFpsTime += updateLength;

            // Should we exit?
            if (gameSettings.getKeyboard().keyDownOnce(KeyEvent.VK_ESCAPE)) {
                if (gamePaused == false) {
                    gamePaused = true;
                    previousRoundState = roundState;
                    roundState = RoundEnum.ROUND_PAUSED;
                } else {
                    roundState = previousRoundState;
                    gamePaused = false;
                }
                //isRunning = false;
            }

            if (lastFpsTime >= 1000000000) {
                //System.out.println("(FPS: "+fps+")");
                //System.out.println(++second);
                if (roundState != RoundEnum.ROUND_PAUSED) {
                    gameSettings.getActualRound().roundTick();
                    if (gameSettings.getActualRound().isRoundEnd()) {
                        gameSettings.setPlayerKeyboardAvailable(false);
                        if (checkDiscsAmount() == 0) {
                            gameSettings.getActualRound().roundEndTimeDelay++;
                            if (gameSettings.getActualRound().getRoundEndTimeDelay() >= gameSettings.getRoundEndDelay()) {
                                changeRoundState = true;
                            }
                        }
                    }
                }
                lastFpsTime = 0;
            }

            if (lastFpsTime >= OPTIMAL_TIME) {

                // Poll the keyboard
                gameSettings.getKeyboard().poll();

                if (roundState == RoundEnum.ROUND_CONTINUES) {
                    gameFrame.gameCounter.tick();
                    if (roundStateChanged) {
                        gameSettings.setPlayerKeyboardAvailable(true);
                        roundStateChanged = false;
                    }

                    updateGameLogic();
                    if (gameSettings.isPlayerKeyboardAvailable()) {
                        gameSettings.checkPlayerInput();
                    }
                    if (changeRoundState) {
                        roundState = RoundEnum.ROUND_ENDS;
                        roundStateChanged = true;
                    }

                } else if (roundState == RoundEnum.ROUND_ENDS) {
                    gameFrame.gameCounter.tick();
                    if (roundStateChanged) {
                        gameSettings.setPlayerKeyboardAvailable(false);
                        roundStateChanged = false;
                        gameSettings.getActualRound().savePlayerPoints();
                        gameSettings.getActualRound().checkRoundWinner();

                    }

                    gameFrame.gameScreen.tick();
                    gameFrame.gamePointer.tick();
                    if (checkGameCanvasesAnimationEnd()) {
                        roundState = RoundEnum.ROUND_BEGIN;
                        restartAnimationTimers();
                        changeRoundState = false;
                        roundStateChanged = true;
                        if (menuState == MenuEnum.QUIT) {
                            isRunning = false;
                        }
                        if (gameSettings.checkGameEnd()) {
                            roundState = RoundEnum.ROUND_PAUSED;
                            roundStateChanged = true;
                        }
                    }

                } else if (roundState == RoundEnum.ROUND_BEGIN) {

                    if (roundStateChanged) {
                        if (menuState == MenuEnum.START_NEW_GAME) {
                            restartGame();
                            menuState = MenuEnum.NO_OPTION;
                        } else {
                            gameSettings.startNewRound(gameFrame.gameScreen);
                        }
                        roundStateChanged = false;
                    }

                    gameFrame.gameScreen.tick();
                    if (checkGameCanvasesAnimationEnd()) {
                        restartAnimationTimers();
                        changeRoundState = false;
                        roundStateChanged = true;
                        roundState = RoundEnum.ROUND_CONTINUES;
                    }

                } else if (roundState == RoundEnum.ROUND_PAUSED) {
                    menuState = gameFrame.gameScreen.getMenuLayout().checkMenuInput();

                    if (menuState == MenuEnum.CONTINUE) {
                        roundState = RoundEnum.ROUND_CONTINUES;

                    } else if (menuState == MenuEnum.START_NEW_GAME) {
                        roundState = RoundEnum.ROUND_BEGIN;
                    } else if (menuState == MenuEnum.QUIT) {
                        roundState = RoundEnum.ROUND_ENDS;
                    }

                    roundStateChanged = true;
                }

                gameRenderUpdate(roundState);

            }

            long sleepTime = (lastLoopTime - System.nanoTime() + OPTIMAL_TIME) / 1000000;

            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException ex) {
                    Logger.getLogger(GameLoop.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        gameFrame.dispose();
    }

    /**
     * Metoda odpowiadająca za uaktualnienie elementów logiki, zmiany sanów
     * obiektów w grze
     */
    public void updateGameLogic() {
        //Interakcja dysków z mapą
        ColisionPoint colisionPoint;

        for (int i = 0; i < gameSettings.getPlayerList().size(); i++) {
            Player player = gameSettings.getPlayerList().get(i);
            player.getPlayerLaser().moveLaser();

            for (int j = 0; j < player.getPlayerDiscs().size(); j++) {

                player.getPlayerDiscs().get(j).moveDisc();
                colisionPoint = player.getPlayerDiscs().get(j).checkCollision();
                if (colisionPoint.isColision()) {
                    if (player.getPlayerDiscs().get(j).checkColisionsNumber()) {
                        player.removeDisc(j);
                    } else if (colisionPoint.getColisionType() == 0) {
                        if (gameFrame.gameScreen.setPointField(colisionPoint, player, player.getPlayerDiscs().get(j).getColisionTimes())) {
                            player.removeDisc(j);
                        }
                    } else {
                        gameFrame.gameScreen.getEffectList().add(new LightEffect(player.getColor(), colisionPoint));
                    }
                }
            }
        }

    }

    /**
     * Metoda odpowiedzialna, za inicjializacje okienka gry
     * @return boolean zwraca czy inicializacja grafiki (stworzenie okienka GameFrame) odbyło się prawidłowo
     */
    private boolean initializeGraphics() {
        gameFrame = new GameFrame(gameSettings);
        return true;
    }

    private void initializeLogic() {
        isRunning = true;
        //keyboard = gameSettings.getKeyboard();
        gameSettings.startNewRound(gameFrame.gameScreen);
        roundState = RoundEnum.ROUND_PAUSED;
        roundState = RoundEnum.ROUND_PAUSED;
        gameSettings.setActualRoundNumber(0);

        previousRoundState = RoundEnum.ROUND_CONTINUES;
    }

    /**
     * Metoda służca sprawdzaniu czy na ekranie gry znajdują się jeszcze obiekty typu Disc
     * @return liczba pozostałych obiektów typu Disc
     */
    public int checkDiscsAmount() {
        int discAmount = 0;
        for (int i = 0; i < gameSettings.getPlayerList().size(); i++) {
            Player player = gameSettings.getPlayerList().get(i);
            for (int j = 0; j < player.getPlayerDiscs().size(); j++) {
                discAmount++;
            }
        }
        return discAmount;
    }

    /**
     * Metoda odpowiedzialna za wywołanie metod rysujących wszystkich paneli występujących w grze
     * @param roundState argument pobiera akutalny stan rundy
     */
    public void gameRenderUpdate(RoundEnum roundState) {
        gameFrame.gameCounter.drawUpdate(roundState);
        gameFrame.gamePointer.drawUpdate(roundState);
        gameFrame.gameScreen.drawUpdate(roundState);

    }

    /**
     * Metoda sprawdzająca czy skończyły się animacjie poszczególnych elementów paneli w grze
     * @return zwraca wartość boolean:
     *  - true - animacje zakończyły się
     *  - false - animacje nadal trwają
     */
    public boolean checkGameCanvasesAnimationEnd() {
        return gameFrame.gameScreen.isAnimationElementEnd()
                && gameFrame.gameCounter.isAnimationElementEnd();
        /* && gameFrame.gamePointer.isAnimationElementEnd(); */
    }

    /**
     * Metoda wywołująca funkcjie restartujące czas animacji elenetów paneli w grze
     */
    public void restartAnimationTimers() {
        gameFrame.gameCounter.restartAnimation();
        gameFrame.gamePointer.restartAnimation();
        gameFrame.gameScreen.restartAnimation();
    }

    /**
     * Metoda restartująca ustawienia gry w przygotowaniu do nowej rozgrywki
     */
    public void restartGame() {
        gameSettings.restartGame();
        gameFrame.gamePointer.restartGamePointer();
        gameFrame.gameCounter.restartAnimationTime();
        gameSettings.startNewRound(gameFrame.gameScreen);
    }
}
