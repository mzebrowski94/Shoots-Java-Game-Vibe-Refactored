// pl/mzebrows/shoots/state/GameStateMachineTest.java
package pl.mzebrows.shoots.state;

import org.junit.jupiter.api.Test;
import pl.mzebrows.shoots.input.InputBridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GameStateMachineTest {

    private static InputBridge dummyInput() {
        return mock(InputBridge.class);
    }

    @Test
    void enters_initial_state_on_construction() {
        var state = mock(GameState.class);
        when(state.update(any())).thenReturn(state);

        new GameStateMachine(state);

        verify(state).enter();
    }

    @Test
    void stays_in_same_state_when_update_returns_this() {
        var state = mock(GameState.class);
        var input = dummyInput();
        when(state.update(input)).thenReturn(state);

        var machine = new GameStateMachine(state);
        machine.update(input);

        assertThat(machine.current()).isSameAs(state);
        verify(state, never()).exit();
        verify(state, times(1)).enter(); // only the initial enter
    }

    @Test
    void transitions_to_next_state() {
        var stateA = mock(GameState.class);
        var stateB = mock(GameState.class);
        var input  = dummyInput();

        when(stateA.update(input)).thenReturn(stateB);
        when(stateB.update(input)).thenReturn(stateB);

        var machine = new GameStateMachine(stateA);
        machine.update(input);

        verify(stateA).exit();
        verify(stateB).enter();
        assertThat(machine.current()).isSameAs(stateB);
    }

    @Test
    void isRunning_true_while_states_are_non_null() {
        var state = mock(GameState.class);
        when(state.update(any())).thenReturn(state);

        var machine = new GameStateMachine(state);
        assertThat(machine.isRunning()).isTrue();

        machine.update(dummyInput());
        assertThat(machine.isRunning()).isTrue();
    }

    @Test
    void quits_when_state_returns_null() {
        var state = mock(GameState.class);
        var input = dummyInput();
        when(state.update(input)).thenReturn(null);

        var machine = new GameStateMachine(state);
        assertThat(machine.isRunning()).isTrue();

        machine.update(input);

        assertThat(machine.isRunning()).isFalse();
        verify(state).exit();
    }

    @Test
    void does_nothing_after_quit() {
        var state = mock(GameState.class);
        var input = dummyInput();
        when(state.update(input)).thenReturn(null);

        var machine = new GameStateMachine(state);
        machine.update(input); // triggers quit
        machine.update(input); // should be no-op

        // update() called once, exit() called once
        verify(state, times(1)).update(input);
        verify(state, times(1)).exit();
    }

    @Test
    void chained_transitions_in_single_tick_do_not_happen() {
        // Each tick performs at most one transition
        var stateA = mock(GameState.class);
        var stateB = mock(GameState.class);
        var stateC = mock(GameState.class);
        var input  = dummyInput();

        when(stateA.update(input)).thenReturn(stateB);
        when(stateB.update(input)).thenReturn(stateC);
        when(stateC.update(input)).thenReturn(stateC);

        var machine = new GameStateMachine(stateA);

        machine.update(input); // A → B (single transition)
        assertThat(machine.current()).isSameAs(stateB);

        machine.update(input); // B → C
        assertThat(machine.current()).isSameAs(stateC);
    }
}
