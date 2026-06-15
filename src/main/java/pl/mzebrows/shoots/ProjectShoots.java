
package pl.mzebrows.shoots;

import pl.mzebrows.shoots.app.GameLoop;

/** Application entry point: constructs the game loop and starts it on its own thread. */
public class ProjectShoots {

    /** Launches the game. */
    public static void main(String[] args) {
        new GameLoop().start();
    }

}
