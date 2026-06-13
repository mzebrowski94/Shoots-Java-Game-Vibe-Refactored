// pl/mzebrows/shoots/state/GameStateMachine.java
package pl.mzebrows.shoots.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.mzebrows.shoots.input.InputBridge;

/** Drives top-level game state transitions; must be called from the game-loop thread. */
public final class GameStateMachine {

    private static final Logger log = LoggerFactory.getLogger(GameStateMachine.class);

    private GameState current;
    private boolean running = true;

    public GameStateMachine(GameState initialState) {
        current = initialState;
        log.info("Entering initial state: {}", initialState.getClass().getSimpleName());
        initialState.enter();
    }

    /** Drive one tick. Handles state transitions and quit detection. */
    public void update(InputBridge input) {
        if (!running) return;
        var next = current.update(input);
        if (next == null) {
            log.info("Quit signalled from: {}", current.getClass().getSimpleName());
            current.exit();
            running = false;
        } else if (next != current) {
            log.info("State transition: {} → {}",
                    current.getClass().getSimpleName(), next.getClass().getSimpleName());
            current.exit();
            current = next;
            current.enter();
        }
    }

    public GameState current() { return current; }

    public boolean isRunning() { return running; }
}
