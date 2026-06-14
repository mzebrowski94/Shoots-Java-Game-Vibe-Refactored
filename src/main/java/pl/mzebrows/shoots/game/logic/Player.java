
package pl.mzebrows.shoots.game.logic;

import pl.mzebrows.shoots.game.logic.Drawables.Disc;
import pl.mzebrows.shoots.game.logic.Drawables.PlayerBase;
import pl.mzebrows.shoots.game.logic.Drawables.PlayerCursor;
import pl.mzebrows.shoots.game.logic.Drawables.PlayerLaser;
import pl.mzebrows.shoots.input.GameAction;
import pl.mzebrows.shoots.input.InputBridge;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedList;

/**
 *
 * @author Mateusz Żebrowski, Nr albumu: 95281
 */
public class Player {

    GameSettings gS;

    //STATYSTYKI//
    int number;
    int points;
    int combo;
    Color color;
    LinkedList<ColisionPoint> ReachedFields;
    String name;
    int roundsWon;
    int allPointsErned;
    boolean winner;

    //DYSKI//4
    boolean discStatus;
    int discsLimit;
    int shootDirection;

    //OBIEKTY GRAFICZNE//
    ArrayList<Disc> playerDiscs;
    PlayerBase playerBase;
    PlayerCursor playerCursor;
    PlayerLaser playerLaser;

    //POZYCJA// 
    int positionX;
    int positionY;
    int indexPosX;
    int indexPosY;
    int cursorRotation;

    double moveUnit;

    //KLAWIATURA//
    GameAction rotateLeftAction;
    GameAction rotateRightAction;
    GameAction shootAction;

    /**
     * Konstruktor obiektu
     *
     * @param number argumment przyjmujący numer tworzonego gracza
     * @param gameSettings argument pobierający ustawienia gry
     */
    public Player(int number, GameSettings gameSettings) {

        this.number = number;
        this.gS = gameSettings;
        this.name = "Player " + (number);
        System.out.println(name);
        if (number == 1) {
            indexPosX = 23;
            indexPosY = 12;
            color = new Color(124, 252, 0);
            rotateLeftAction  = GameAction.P1_ROTATE_LEFT;
            rotateRightAction = GameAction.P1_ROTATE_RIGHT;
            shootAction       = GameAction.P1_SHOOT;
            moveUnit = -1;
            shootDirection = 180;
        } else if (number == 2) {
            indexPosX = 2;
            indexPosY = 12;
            color = new Color(48, 213, 200);
            rotateLeftAction  = GameAction.P2_ROTATE_LEFT;
            rotateRightAction = GameAction.P2_ROTATE_RIGHT;
            shootAction       = GameAction.P2_SHOOT;
            moveUnit = 1;
            shootDirection = 0;
        } else if (number == 3) {
            indexPosX = 12;
            indexPosY = 2;
            color = new Color(252, 3, 0);
            rotateLeftAction  = GameAction.P3_ROTATE_LEFT;
            rotateRightAction = GameAction.P3_ROTATE_RIGHT;
            shootAction       = GameAction.P3_SHOOT;
            moveUnit = -1;
            shootDirection = -90;
        } else if (number == 4) {
            indexPosX = 12;
            indexPosY = 23;
            color = new Color(237, 26, 116);
            rotateLeftAction  = GameAction.P4_ROTATE_LEFT;
            rotateRightAction = GameAction.P4_ROTATE_RIGHT;
            shootAction       = GameAction.P4_SHOOT;
            moveUnit = 1;
            shootDirection = 90;
        }

        positionX = indexPosY * 36;
        positionY = indexPosX * 36;
        points = 0;
        combo = 0;
        cursorRotation = 0;
        discsLimit = 0;

        playerDiscs = new ArrayList<Disc>();
        ReachedFields = new LinkedList<ColisionPoint>();

        playerBase = new PlayerBase(positionX, positionY, color);
        playerCursor = new PlayerCursor(positionX, positionY, this);
        playerLaser = new PlayerLaser(positionX, positionY, this);
    }

    /** Reads player-specific {@link GameAction}s from the input bridge for this frame. */
    public void checkPlayerInput(InputBridge input) {
        if (discsLimit < 3 && input.isJustPressed(shootAction)) {
            addDisc();
        } else if (input.isHeld(rotateLeftAction)) {
            cursorRotation = (int) playerCursor.setRotation(1, moveUnit);
            playerLaser.setRotation(1, moveUnit);
        } else if (input.isHeld(rotateRightAction)) {
            cursorRotation = (int) playerCursor.setRotation(2, moveUnit);
            playerLaser.setRotation(2, moveUnit);
        } else {
            playerLaser.isMoving(false);
        }
    }

    /**
     * Metoda odpowiedzialna za dodanie nowego obiektu Dics na ekran gry
     *
     * @return zwraca wskaźnik do nowo powstałego obiektu typu Disc
     */
    public Disc addDisc() {
        Disc newDisc = new Disc(positionX, positionY, this);
        playerDiscs.add(newDisc);
        discsLimit++;
        //System.out.println("++"+discsLimit);
        return newDisc;
    }

    /**
     * Metoda służąca od usuniecią obiektu typu Disc
     *
     * @param index pobiera index obiektu typu Disc który ma zostać usunięty
     */
    public void removeDisc(int index) {
        playerDiscs.remove(index);
        discsLimit--;
    }

    /**
     * Metoda dodająca punkty do zmiennej points
     *
     * @param i argument pobierajacy wartość punktów do dodania
     */
    public void addPoints(int i) {
        points += i;
    }

    /**
     * Metoda umieszczająca obiekty PlayerCuror oraz PlayerLaser w domyślnej
     * pozycji na ekranie gry
     */
    public void resetPlayerCursor() {
        cursorRotation = 0;
        playerLaser.resetLaserPosition();
        playerCursor.resetCursorPosition();

    }

    /**
     * Metoda doadająca ilośc wygranaych przez gracza rund
     */
    public void increaseRoundsWon() {
        this.roundsWon += 1;
    }

    /**
     * Metoda sumująca punkty gracza z aktualnej i przprzednich rund
     * @param i argument pobierajacy wartość punktów do dodania
     */
    public void increaseAllPointsErned(int i) {
        this.allPointsErned += i;
    }

    public GameSettings getGameSettings() {
        return gS;
    }

    public void setGameSettings(GameSettings gS) {
        this.gS = gS;
    }

    public PlayerLaser getPlayerLaser() {
        return playerLaser;
    }

    public void setPlayerLaser(PlayerLaser playerLaser) {
        this.playerLaser = playerLaser;
    }

    public ArrayList<Disc> getPlayerDiscs() {
        return playerDiscs;
    }


    public void setPlayerDiscs(ArrayList<Disc> playerDiscs) {
        this.playerDiscs = playerDiscs;
    }


    public int getShootDirection() {
        return shootDirection;
    }


    public void setShootDirection(int shootDirection) {
        this.shootDirection = shootDirection;
    }


    public int getCursorRotation() {
        return cursorRotation;
    }


    public void setCursorRotation(int cursorRotation) {
        this.cursorRotation = cursorRotation;
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public int getCombo() {
        return combo;
    }

    public void setCombo(int combo) {
        this.combo = combo;
    }

    public LinkedList<ColisionPoint> getReachedFields() {
        return ReachedFields;
    }

    public void setReachedFields(LinkedList<ColisionPoint> ReachedFields) {
        this.ReachedFields = ReachedFields;
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public PlayerBase getPlayerBase() {
        return playerBase;
    }

    public void setPlayerBase(PlayerBase playerBase) {
        this.playerBase = playerBase;
    }

    public PlayerCursor getPlayerCursor() {
        return playerCursor;
    }

    public void setPlayerCursor(PlayerCursor playerCursor) {
        this.playerCursor = playerCursor;
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    
    public int getRoundsWon() {
        return roundsWon;
    }

    public void setRoundsWon(int roundsWon) {
        this.roundsWon = roundsWon;
    }

    public int getAllPointsErned() {
        return allPointsErned;
    }

    public void setAllPointsErned(int allPointsErned) {
        this.allPointsErned = allPointsErned;
    }

    public boolean isWinner() {
        return winner;
    }

    public void setWinner(boolean winner) {
        this.winner = winner;
    }

}
