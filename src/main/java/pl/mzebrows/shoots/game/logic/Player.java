
package pl.mzebrows.shoots.game.logic;

import pl.mzebrows.shoots.game.logic.Drawables.Disc;
import pl.mzebrows.shoots.game.logic.Drawables.PlayerBase;
import pl.mzebrows.shoots.game.logic.Drawables.PlayerCursor;
import pl.mzebrows.shoots.game.logic.Drawables.PlayerLaser;
import java.awt.Color;
import java.awt.event.KeyEvent;
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
    KeyboardInput keyboard;
    int keyLeft;
    int keyRight;
    int keyShoot;

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
            keyLeft = KeyEvent.VK_LEFT;
            keyRight = KeyEvent.VK_RIGHT;
            keyShoot = KeyEvent.VK_UP;
            moveUnit = -1;
            shootDirection = 180;
        } else if (number == 2) {
            indexPosX = 2;
            indexPosY = 12;
            color = new Color(48, 213, 200);
            keyLeft = KeyEvent.VK_A;
            keyRight = KeyEvent.VK_D;
            keyShoot = KeyEvent.VK_W;
            moveUnit = 1;
            shootDirection = 0;
        } else if (number == 3) {
            indexPosX = 12;
            indexPosY = 2;
            color = new Color(252, 3, 0);
            keyLeft = KeyEvent.VK_NUMPAD4;
            keyRight = KeyEvent.VK_NUMPAD6;
            keyShoot = KeyEvent.VK_NUMPAD8;
            moveUnit = -1;
            shootDirection = -90;
        } else if (number == 4) {
            indexPosX = 12;
            indexPosY = 23;
            color = new Color(237, 26, 116);
            keyLeft = KeyEvent.VK_L;
            keyRight = KeyEvent.VK_J;
            keyShoot = KeyEvent.VK_I;
            moveUnit = 1;
            shootDirection = 90;
        }

        positionX = indexPosY * 36;
        positionY = indexPosX * 36;
        keyboard = gameSettings.getKeyboard();
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

    /**
     * Metoda odpowiadająca za sprawdzenie czy na klawiaturze zostały wciśniętę
     * odpowienie klawisze dla odpowiedniego gracza, jeśli tak, wywoływane są
     * odpowiedenie metody
     */
    public void checkPlayerInput() {

        if (discsLimit < 3 && keyboard.keyDownOnce(keyShoot)) {
            addDisc();
        } else if (keyboard.keyDown(keyLeft)) {
            //System.out.println("LEFT, cursor: " + cursorRotation);
            cursorRotation = (int) playerCursor.setRotation(1, moveUnit);
            playerLaser.setRotation(1, moveUnit);

        } else if (keyboard.keyDown(keyRight)) {
            //System.out.println("RIGHT, cursor: " + cursorRotation);
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
