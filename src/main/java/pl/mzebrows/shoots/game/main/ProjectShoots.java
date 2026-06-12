
package pl.mzebrows.shoots.game.main;

import pl.mzebrows.shoots.game.logic.GameLoop;

/**
 * Główny obiekt gry przechowujący funkcję main w której wywoływany jest obiekt typu GameLoop czyli pętla gry
 * @author Mateusz Żebrowski, Nr albumu: 95281
 */
public class ProjectShoots {

    /**
     * Funckja main rozpoczynająca działanie programu
     * @param args - przyjmuje dodatkowe argument podane przy właczaniu programu
     */
    public static void main(String[] args) {
        //MAIN        

        System.out.println("-Run");
        GameLoop gameLoop = new GameLoop();
    } 

}
