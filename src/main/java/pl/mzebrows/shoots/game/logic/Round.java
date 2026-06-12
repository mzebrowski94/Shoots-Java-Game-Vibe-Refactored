
package pl.mzebrows.shoots.game.logic;

import java.util.ArrayList;

/**
 * Klasa zawierająca informację na temat odbytej bądź odbywanej aktualnie rundy.
 * Zawiera informacje takie jak, numer rundy, ilośc zdobytych podczas niej
 * punktów
 *
 * @author Mateusz Żebrowski, Nr albumu: 95281
 */
public class Round {

    int roundNumber;
    ArrayList<Integer> playerPointsList;
    int roundTime;
    boolean roundEnd;

    /**
     * Zmienna przechowująca informację o tym ile dodatkowego czasu trwa runda
     */
    public int roundEndTimeDelay;
    boolean animationEnded;
    GameSettings gS;
    PointList pointList;

    /**
     * Konstruktor obiektu Round
     *
     * @param gameSettings argument przyjmujący ustawienia gry
     * @param roundNumber argument przyjmujący numer tworzonej rundy
     */
    public Round(GameSettings gameSettings, int roundNumber) {
        gS = gameSettings;
        this.roundEnd = false;
        this.roundEndTimeDelay = 0;
        this.animationEnded = false;
        this.roundTime = 0;
        playerPointsList = new ArrayList<>();
        pointList = new PointList(gS);
    }

    /**
     * Metoda odmierzająca czas trwania rundy
     */
    public void roundTick() {
        roundTime++;
        if (roundTime >= gS.getRoundTime()) {
            roundEnd = true;
        }
    }

    /**
     * Metoda odpowiedzialna, za zapisanie punktów zdobytych przez graczy
     * podczas tej rundy
     */
    public void savePlayerPoints() {
        for (int i = 0; i < gS.getPlayerList().size(); i++) {
            playerPointsList.add(gS.getPlayerList().get(i).getPoints());
            gS.getPlayerList().get(i).increaseAllPointsErned(playerPointsList.get(i));
        }
    }

    /**
     * Metoda sumująca punkty i sprawdzająca który z podanych graczy daną rundę
     * wygrał
     */
    public void checkRoundWinner() {
        int bestRoundScore = 0;

        for (int i = 0; i < gS.getPlayerList().size(); i++) {
            if (playerPointsList.get(i) > bestRoundScore) {
                bestRoundScore = playerPointsList.get(i);
            }
        }

        for (int i = 0; i < gS.getPlayerList().size(); i++) {
            if (playerPointsList.get(i) == bestRoundScore) {
                gS.getPlayerList().get(i).increaseRoundsWon();
            }
        }

    }

    public int getRoundNumber() {
        return roundNumber;
    }


    public void setRoundNumber(int roundNumber) {
        this.roundNumber = roundNumber;
    }


    public ArrayList<Integer> getPlayerPointsList() {
        return playerPointsList;
    }


    public void setPlayerPointsList(ArrayList<Integer> playerPointsList) {
        this.playerPointsList = playerPointsList;
    }

 
    public int getRoundTime() {
        return roundTime;
    }

    public void setRoundTime(int roundTime) {
        this.roundTime = roundTime;
    }


    public boolean isRoundEnd() {
        return roundEnd;
    }


    public void setRoundEnd(boolean roundEnd) {
        this.roundEnd = roundEnd;
    }


    public int getRoundEndTimeDelay() {
        return roundEndTimeDelay;
    }


    public void setRoundEndTimeDelay(int roundEndTimeDelay) {
        this.roundEndTimeDelay = roundEndTimeDelay;
    }


    public boolean isAnimationEnded() {
        return animationEnded;
    }


    public void setAnimationEnded(boolean animationEnded) {
        this.animationEnded = animationEnded;
    }

    public PointList getPointList() {
        return pointList;
    }

 
    public void setPointList(PointList pointList) {
        this.pointList = pointList;
    }

}
