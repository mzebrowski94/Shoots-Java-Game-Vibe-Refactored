// pl/mzebrows/shoots/input/InputBridgeTest.java
package pl.mzebrows.shoots.input;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InputBridgeTest {

    private InputBridge bridge;

    @BeforeEach
    void setUp() {
        bridge = InputBridge.withDefaultKeyMap();
    }

    // ---------- helper ----------

    private static KeyEvent keyEvent(int keyCode) {
        var e = mock(KeyEvent.class);
        when(e.getKeyCode()).thenReturn(keyCode);
        return e;
    }

    // ---------- isHeld ----------

    @Test
    void isHeld_false_before_any_key_press() {
        bridge.poll();
        assertThat(bridge.isHeld(GameAction.PAUSE)).isFalse();
    }

    @Test
    void isHeld_true_after_key_pressed_and_polled() {
        bridge.keyPressed(keyEvent(KeyEvent.VK_ESCAPE));
        bridge.poll();
        assertThat(bridge.isHeld(GameAction.PAUSE)).isTrue();
    }

    @Test
    void isHeld_false_after_key_released_and_polled() {
        bridge.keyPressed(keyEvent(KeyEvent.VK_ESCAPE));
        bridge.poll();
        bridge.keyReleased(keyEvent(KeyEvent.VK_ESCAPE));
        bridge.poll();
        assertThat(bridge.isHeld(GameAction.PAUSE)).isFalse();
    }

    // ---------- isJustPressed ----------

    @Test
    void isJustPressed_true_only_on_first_frame() {
        bridge.keyPressed(keyEvent(KeyEvent.VK_ESCAPE));

        bridge.poll(); // frame 1 – just pressed
        assertThat(bridge.isJustPressed(GameAction.PAUSE)).isTrue();

        bridge.poll(); // frame 2 – held, not just pressed
        assertThat(bridge.isJustPressed(GameAction.PAUSE)).isFalse();
        assertThat(bridge.isHeld(GameAction.PAUSE)).isTrue();
    }

    @Test
    void isJustPressed_false_before_poll() {
        bridge.keyPressed(keyEvent(KeyEvent.VK_ESCAPE));
        // No poll() yet
        assertThat(bridge.isJustPressed(GameAction.PAUSE)).isFalse();
    }

    // ---------- dual-action keys ----------

    @Test
    void VK_UP_triggers_both_P1_SHOOT_and_NAVIGATE_UP() {
        bridge.keyPressed(keyEvent(KeyEvent.VK_UP));
        bridge.poll();
        assertThat(bridge.isHeld(GameAction.P1_SHOOT)).isTrue();
        assertThat(bridge.isHeld(GameAction.NAVIGATE_UP)).isTrue();
    }

    @Test
    void VK_LEFT_triggers_both_P1_ROTATE_LEFT_and_NAVIGATE_LEFT() {
        bridge.keyPressed(keyEvent(KeyEvent.VK_LEFT));
        bridge.poll();
        assertThat(bridge.isHeld(GameAction.P1_ROTATE_LEFT)).isTrue();
        assertThat(bridge.isHeld(GameAction.NAVIGATE_LEFT)).isTrue();
    }

    // ---------- unmapped key ----------

    @Test
    void unmapped_key_produces_no_action() {
        bridge.keyPressed(keyEvent(KeyEvent.VK_F12));
        bridge.poll();
        for (var action : GameAction.values()) {
            assertThat(bridge.isHeld(action)).as("action " + action).isFalse();
        }
    }

    // ---------- player-specific keys ----------

    @Test
    void player2_shoot_maps_to_P2_SHOOT() {
        bridge.keyPressed(keyEvent(KeyEvent.VK_W));
        bridge.poll();
        assertThat(bridge.isJustPressed(GameAction.P2_SHOOT)).isTrue();
        assertThat(bridge.isJustPressed(GameAction.P1_SHOOT)).isFalse();
    }

    @Test
    void player3_numpad_maps_correctly() {
        bridge.keyPressed(keyEvent(KeyEvent.VK_NUMPAD4));
        bridge.poll();
        assertThat(bridge.isHeld(GameAction.P3_ROTATE_LEFT)).isTrue();
    }
}
