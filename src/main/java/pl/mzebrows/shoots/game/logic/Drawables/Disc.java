
package pl.mzebrows.shoots.game.logic.Drawables;

import pl.mzebrows.shoots.game.logic.GameSettings;
import pl.mzebrows.shoots.game.logic.ColisionPoint;
import pl.mzebrows.shoots.game.logic.Player;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;

/**
 * Klasa imprelementująca interfejs Drawable, obiekt obrazujący dysk który jest
 * wypuszczany przez gracza, i który służy do zdobywania punktów podczas trwania
 * gry.
 *
 * @author Mateusz Żebrowski, Nr albumu: 95281
 */
public class Disc implements Drawable {

    int size;
    GameSettings gS;
//SHAPE   
    Color color = Color.black;
    int bigRadius = 18;
    int smallRadius = 10;
    int halfSmallRadius = (int) (0.5 * smallRadius);
    int halfBigRadius = (int) (0.5 * bigRadius);
    Graphics2D shape2;

//MOVEMENT
    int angle = 0;

//COLISION
    int colisionTimes = 0;
    int colisionType = 0;
    int ballColisionSize = 1;
    ColisionPoint discPoint = new ColisionPoint(0, 0);

    /**
     * Konstruktor obiektu typu Disc
     *
     * @param positionX pozycja X obiektu na mapie
     * @param positionY pozycja Y obiektu na mapie
     * @param player argument przyjmujący ustawienia gracza który utworzył
     * obiekt typu Disc
     */
    public Disc(int positionX, int positionY, Player player) {
        gS = player.getGameSettings();
        size = gS.getSIZE();
        this.color = player.getColor();
        this.angle = player.getCursorRotation() + player.getShootDirection();
        discPoint.setColision(false);
        discPoint.setDirectionX(1);
        discPoint.setDirectionY(1);
        discPoint.setPosX((int) positionX);
        discPoint.setPosY((int) positionY);
        discPoint.setMoveSpeed(2);
    }

    @Override
    public void draw(Graphics2D shape) {
        Area outsideCircle = new Area(new Ellipse2D.Double(discPoint.getPosX() - halfBigRadius, discPoint.getPosY() - halfBigRadius, bigRadius, bigRadius));
        Area insideCircle = new Area(new Ellipse2D.Double(discPoint.getPosX() - halfSmallRadius, discPoint.getPosY() - halfSmallRadius, smallRadius, smallRadius));
        shape.setColor(color);
        outsideCircle.subtract(insideCircle);
        shape.fill(outsideCircle);
    }

    /**
     * Metoda wywołująca sprawdzanie kolizji obecnego obiektu z elementami mapy
     * (obiektami typu Block lub PointField)
     *
     * @return Obiekt typu ColiisonPoint zawierający wartości dotyczące
     * przesuwania obiektu
     */
    public ColisionPoint checkCollision() {
        discPoint.setColision(false);
        // c11: GameSettings.getColisionCalculator() removed when legacy collision state was
        // decommissioned. Legacy class kept only as a behavioural reference (see STATE.md); not run
        // by the live game. Disabled so the IDE raw compile (which ignores Maven <excludes>) passes.
        // discPoint = gS.getColisionCalculator().checkColision(discPoint);
        if (discPoint.isColision()) {
            if (discPoint.getColisionType() != 0) {
                colisionTimes++;
            }
        }
        return discPoint;
    }

    /**
     * Metoda obliczająca przesunięcie obiektu
     */
    public void moveDisc() {
        discPoint.updatePosition(angle);
    }

    /**
     * Metoda sprawdzająca ile razy obiekt typu Disc kolidował z innymi
     * obiektami. Jeśli liczba zderzeń przekroczy odpowiednią wartość jest to
     * sygnał do usunięcia dysku
     *
     * @return zwraca wartość typu boolean określającą czy dysk powienien zostać
     * usunięty
     */
    public boolean checkColisionsNumber() {
        if (colisionTimes > 7) {
            return true;
        } else {
            return false;
        }
    }

    public int getColisionTimes() {
        return colisionTimes;
    }

    public void setColisionTimes(int colisionTimes) {
        this.colisionTimes = colisionTimes;
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }

}
