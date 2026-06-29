// pl/mzebrows/shoots/state/PausedState.java
package pl.mzebrows.shoots.state;

import pl.mzebrows.shoots.ui.GameScreen;
import pl.mzebrows.shoots.app.GameSettings;
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
    public void enter() {
        // When pausing an in-progress game, default the highlighted option to CONTINUE (not START NEW GAME).
        // Before the first round / after the match ends, CONTINUE is unavailable, so leave the default.
        if (settings.getActualRoundNumber() != 0 && !settings.isGameEnd()) {
            screen.getMenuLayout().selectContinue();
        }
    }

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
            case START_ONLINE -> {
                // The waiting room handed us a started network session (host pressed START, or START arrived).
                var session = screen.getMenuLayout().takeStartedSession();
                if (session == null) {
                    yield this;
                }
                settings.setGameEnd(false);
                playingState.startOnline(session);
                yield playingState;
            }
            case QUIT -> {
                if (settings.isMatchInProgress()) {
                    // Mid-game QUIT abandons the current match and returns to the main menu rather than
                    // exiting the application; GAMEPLAY OPTIONS become editable again afterwards (#6).
                    playingState.abandonMatch();
                    screen.getMenuLayout().selectStartNewGame();
                    yield this;
                }
                yield null; // from the fresh main menu / game-over screen: quit the application
            }
            default -> this;
        };
    }

    @Override
    public void exit() { /* nothing */ }
}
