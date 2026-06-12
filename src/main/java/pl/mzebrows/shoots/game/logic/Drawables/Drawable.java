
package pl.mzebrows.shoots.game.logic.Drawables;

import java.awt.Graphics2D;

/**
 * Interfejs który implementuje część obiektów które rysowane są na głównym ekranie gry GameScreen
 * @author Mateusz Żebrowski, Nr albumu: 95281
 */
public interface Drawable {

    /**
     * Metoda odpowiedzialna za wywoływanie resyowania obiektów dzieciczących interface Drawable
     * @param shape przyjmuje obiekt typu Graphics2D służący do rysowania elementów w grze
     */
    public void draw(Graphics2D shape);
}
