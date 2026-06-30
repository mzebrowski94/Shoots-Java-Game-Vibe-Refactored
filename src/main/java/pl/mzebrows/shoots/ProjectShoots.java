
package pl.mzebrows.shoots;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.mzebrows.shoots.engine.GameLoop;
import pl.mzebrows.shoots.config.ConfigException;

/** Application entry point: constructs the game loop and starts it on its own thread. */
public class ProjectShoots {

    private static final Logger log = LoggerFactory.getLogger(ProjectShoots.class);

    /** Launches the game. A missing/invalid required property is fatal: it is logged and the game exits. */
    public static void main(String[] args) {
        try {
            new GameLoop().start();
        } catch (ConfigException e) {
            log.error("Configuration error -- the game cannot start. Fix game.properties / graphic.properties: {}",
                    e.getMessage());
            System.exit(1);
        }
    }

}
