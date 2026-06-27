// pl/mzebrows/shoots/state/GameOverState.java
package pl.mzebrows.shoots.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.mzebrows.shoots.ui.GameScreen;
import pl.mzebrows.shoots.app.GameSettings;
import pl.mzebrows.shoots.input.InputBridge;

/**
 * End-of-match state displaying final scores.
 * Only START_NEW_GAME and QUIT are honoured here; CONTINUE is ignored.
 */
public final class GameOverState implements GameState {

    private static final Logger log = LoggerFactory.getLogger(GameOverState.class);

    private final GameSettings settings;
    private final GameScreen screen;
    private final PlayingState playingState;

    public GameOverState(GameSettings settings, GameScreen screen, PlayingState playingState) {
        this.settings = settings;
        this.screen = screen;
        this.playingState = playingState;
    }

    @Override
    public void enter() {
        log.info("Game over — showing final scores");
    }

    @Override
    public GameState update(InputBridge input) {
        var action = screen.getMenuLayout().checkMenuInput(input);
        return switch (action) {
            case START_NEW_GAME -> {
                settings.setGameEnd(false);
                playingState.requestRestart();
                yield playingState;
            }
            case START_ONLINE -> {
                // After a match the menu can host/join another online game; consume the started session and
                // hand it to the playing state, just like the pause/lobby menu does (#3 -- previously ignored
                // here, which left the menu frozen after "Starting match ...").
                var session = screen.getMenuLayout().takeStartedSession();
                if (session == null) {
                    yield this;
                }
                settings.setGameEnd(false);
                playingState.startOnline(session);
                yield playingState;
            }
            case QUIT -> null;
            default -> this;
        };
    }

    @Override
    public void exit() { /* nothing */ }
}
