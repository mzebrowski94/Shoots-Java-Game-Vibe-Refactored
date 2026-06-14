// src/test/java/pl/mzebrows/shoots/world/PlayInputTest.java
package pl.mzebrows.shoots.world;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import pl.mzebrows.shoots.input.GameAction;
import pl.mzebrows.shoots.input.InputBridge;

/** Verifies the AWT-free mapping from polled {@link GameAction}s to {@link PlayWorld} intent. */
class PlayInputTest {

    @Test
    void heldRotateLeftMapsToLeftAim() {
        var input = mock(InputBridge.class);
        when(input.isHeld(GameAction.P1_ROTATE_LEFT)).thenReturn(true);

        assertThat(PlayInput.aimFor(input, 0)).isEqualTo(PlayWorld.AimInput.LEFT);
    }

    @Test
    void heldRotateRightMapsToRightAim() {
        var input = mock(InputBridge.class);
        when(input.isHeld(GameAction.P2_ROTATE_RIGHT)).thenReturn(true);

        assertThat(PlayInput.aimFor(input, 1)).isEqualTo(PlayWorld.AimInput.RIGHT);
    }

    @Test
    void leftWinsTiesMatchingLegacyOrder() {
        var input = mock(InputBridge.class);
        when(input.isHeld(GameAction.P1_ROTATE_LEFT)).thenReturn(true);
        when(input.isHeld(GameAction.P1_ROTATE_RIGHT)).thenReturn(true);

        assertThat(PlayInput.aimFor(input, 0)).isEqualTo(PlayWorld.AimInput.LEFT);
    }

    @Test
    void noRotationMapsToNone() {
        var input = mock(InputBridge.class);

        assertThat(PlayInput.aimFor(input, 0)).isEqualTo(PlayWorld.AimInput.NONE);
    }

    @Test
    void shootIsEdgeTriggered() {
        var input = mock(InputBridge.class);
        when(input.isJustPressed(GameAction.P3_SHOOT)).thenReturn(true);

        assertThat(PlayInput.shootFor(input, 2)).isTrue();
        assertThat(PlayInput.shootFor(input, 0)).isFalse();
    }

    @Test
    void mapsEachPlayerToItsOwnActions() {
        var input = mock(InputBridge.class);
        when(input.isHeld(GameAction.P4_ROTATE_LEFT)).thenReturn(true);

        assertThat(PlayInput.aimFor(input, 3)).isEqualTo(PlayWorld.AimInput.LEFT);
        assertThat(PlayInput.aimFor(input, 0)).isEqualTo(PlayWorld.AimInput.NONE);
    }
}
