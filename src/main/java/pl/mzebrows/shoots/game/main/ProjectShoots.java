
package pl.mzebrows.shoots.game.main;

import pl.mzebrows.shoots.game.logic.GameLoop;

/** Application entry point: constructs the game loop and starts it on its own thread. */
public class ProjectShoots {

    /** Launches the game. */
    public static void main(String[] args) {
        new GameLoop().start();
    }

}
