
package pl.mzebrows.shoots.game.logic;

import pl.mzebrows.shoots.game.logic.Drawables.PointField;
import pl.mzebrows.shoots.game.logic.Drawables.PointField.GamePoint;

import java.util.ArrayList;

/**
 * Klasa służąca do obsługi punktacji podczas poszczególnej rundy w grze
 * Odpowiada, za przechowywanie, dodawanie aktualizowanie punktów zdobytych
 * przez graczy
 *
 * @author Mateusz Żebrowski, Nr albumu: 95281
 */
public class PointList {

    boolean changed = true;
    private ArrayList<PointField> pointFields;
    GamePoint actualPoint;
    ArrayList<Player> playerList;
    GameSettings gS;
    int maxPointsAmount;

    /**
     * Konstruktor klasy PointList
     *
     * @param gameSettings argument przyjmujący ustawienia gry
     */
    public PointList(GameSettings gameSettings) {
        gS = gameSettings;
        pointFields = new ArrayList<>();
        playerList = gameSettings.getPlayerList();
        maxPointsAmount = 8 * 4;
    }

    /**
     * Metoda sprawdzająca czy podane pole punktowe zostało przejęte, oraz
     * obsługuje zdarzenie kolizji dysku z danym punktem
     *
     * @param colisionPoint argument przyjumjący wartości punktu w którym
     * nastąpiło przejęcie
     * @param player argument przyjmujący informacje o graczu
     * @param colisionTimes argument przyjmujący informację o tym ile razy
     * wcześnej dany obiekt typu Disc odbił się od innych obiektów
     * @return zwrac wartość boolean: - true jeśli punkt udało się zdobyć -
     * false jeśli punktu nie udało się zdobyć
     */
    public boolean checkPointFiledErned(ColisionPoint colisionPoint, Player player, int colisionTimes) {
        boolean result = false;

        for (int i = 0; i < pointFields.size(); i++) {
            if (pointFields.get(i).getInX() == colisionPoint.getIndexX() && pointFields.get(i).getInY() == colisionPoint.getIndexY()) {
                result = pointFields.get(i).chckIfPointFieldErned(player, colisionTimes);
                if (result) {
                    changed = true;
                    updatePlayerPoints();
                }
            }
        }

        return result;
    }

    /**
     * Metoda sprawdzając czy podane pole punktowe zostało przejęte, jeśli
     * tak, odpowiednia ilość punktów jest przypisywana odpowiedniemu graczowi
     */
    public void updatePlayerPoints() {

        for (int i = 0; i < gS.getPlayerNumber(); i++) {
            playerList.get(i).setPoints(0);
        }

        for (int i = 0; i < pointFields.size(); i++) {
            actualPoint = pointFields.get(i).getPoint();
            if (actualPoint.isErned()) {
                if (actualPoint.getPlayerNumber() == 1) {
                    playerList.get(0).addPoints(actualPoint.getErnedPoints());

                } else if (actualPoint.getPlayerNumber() == 2) {
                    playerList.get(1).addPoints(actualPoint.getErnedPoints());

                } else if (actualPoint.getPlayerNumber() == 3) {
                    playerList.get(2).addPoints(actualPoint.getErnedPoints());
                } else if (actualPoint.getPlayerNumber() == 4) {
                    playerList.get(3).addPoints(actualPoint.getErnedPoints());
                }
            }
        }

    }

    public boolean isChanged() {
        return changed;
    }


    public void setChanged(boolean changed) {
        this.changed = changed;
    }


    public ArrayList<PointField> getPointFields() {
        return pointFields;
    }


    public void setPointFields(ArrayList<PointField> pointFields) {
        this.pointFields = pointFields;
    }


    public int getMaxPointsAmount() {
        return maxPointsAmount;
    }

    public void setMaxPointsAmount(int maxPointsAmount) {
        this.maxPointsAmount = maxPointsAmount;
    }

}
