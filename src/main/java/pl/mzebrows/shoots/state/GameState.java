// pl/mzebrows/shoots/state/GameState.java
package pl.mzebrows.shoots.state;

import pl.mzebrows.shoots.input.InputBridge;

/** A node in the top-level game state machine. */
public interface GameState {

    /** Called once when the machine enters this state. */
    void enter();

    /**
     * Advances the state by one game tick.
     *
     * @return the next state to enter, {@code this} to remain, or {@code null} to quit the game.
     */
    GameState update(InputBridge input);

    /** Called once when the machine leaves this state. */
    void exit();
}
