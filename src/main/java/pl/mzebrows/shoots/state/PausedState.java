// pl/mzebrows/shoots/state/PausedState.java
package pl.mzebrows.shoots.state;

import pl.mzebrows.shoots.ui.GameScreen;
import pl.mzebrows.shoots.app.GameSettings;
import pl.mzebrows.shoots.ui.MenuEnum;
import pl.mzebrows.shoots.input.InputBridge;

/**
 * Pause / lobby state.  Shows the game menu and reacts to menu selections.
 * Also serves as the initial state before the first round begins.
 */
public final class PausedState implements GameState {

    private final GameSettings settings;
    private final GameScreen screen;
    private final PlayingState playingState;

    public PausedState(GameSettings settings, GameScreen screen, PlayingState playingState) {
        this.settings = settings;
        this.screen = screen;
        this.playingState = playingState;
    }

    @Override
    public void enter() { /* nothing */ }

    @Override
    public GameState update(InputBridge input) {
        var action = screen.getMenuLayout().checkMenuInput(input);

        return switch (action) {
            case CONTINUE -> {
                // CONTINUE is unavailable before the game starts or after it ends
                if (settings.getActualRoundNumber() == 0 || settings.isGameEnd()) {
                    yield this;
                }
                yield playingState;
            }
            case START_NEW_GAME -> {
                // Settings (roundTime, playerNumber, roundLimit) already applied inside checkMenuInput
                settings.setGameEnd(false);
                playingState.requestRestart();
                yield playingState;
            }
            case QUIT -> null; // null signals the state machine to quit
            default -> this;
        };
    }

    @Override
    public void exit() { /* nothing */ }
}
