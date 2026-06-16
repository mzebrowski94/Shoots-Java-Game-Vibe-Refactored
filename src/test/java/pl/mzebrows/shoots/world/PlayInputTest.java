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
    void heldRotateLeftMapsToMirroredAimForP1() {
        var input = mock(InputBridge.class);
        when(input.isHeld(GameAction.P1_ROTATE_LEFT)).thenReturn(true);

        // P1's base faces up: its keys are mirrored so the LEFT key turns the cursor to the player's
        // own left, which is AimInput.RIGHT in absolute terms (legacy Player.moveUnit = -1).
        assertThat(PlayInput.aimFor(input, 0)).isEqualTo(PlayWorld.AimInput.RIGHT);
    }

    @Test
    void heldRotateLeftMapsToLeftAimForNonMirroredP2() {
        var input = mock(InputBridge.class);
        when(input.isHeld(GameAction.P2_ROTATE_LEFT)).thenReturn(true);

        // P2 is not mirrored (legacy moveUnit = +1), so its LEFT key maps straight to AimInput.LEFT.
        assertThat(PlayInput.aimFor(input, 1)).isEqualTo(PlayWorld.AimInput.LEFT);
    }

    @Test
    void mirroringMatchesLegacyMoveUnitSignsForAllPlayers() {
        // Legacy: P1=-1, P2=+1, P3=-1, P4=+1 -> P1/P3 mirrored, P2/P4 not.
        assertThat(PlayWorld.aimKeysMirrored(0)).isTrue();
        assertThat(PlayWorld.aimKeysMirrored(1)).isFalse();
        assertThat(PlayWorld.aimKeysMirrored(2)).isTrue();
        assertThat(PlayWorld.aimKeysMirrored(3)).isFalse();
    }

    @Test
    void heldRotateRightMapsToRightAim() {
        var input = mock(InputBridge.class);
        when(input.isHeld(GameAction.P2_ROTATE_RIGHT)).thenReturn(true);

        assertThat(PlayInput.aimFor(input, 1)).isEqualTo(PlayWorld.AimInput.RIGHT);
    }

    @Test
    void leftKeyWinsTiesMatchingLegacyOrder() {
        var input = mock(InputBridge.class);
        when(input.isHeld(GameAction.P1_ROTATE_LEFT)).thenReturn(true);
        when(input.isHeld(GameAction.P1_ROTATE_RIGHT)).thenReturn(true);

        // The LEFT KEY still wins the tie (checked first); for mirrored P1 that resolves to RIGHT intent.
        assertThat(PlayInput.aimFor(input, 0)).isEqualTo(PlayWorld.AimInput.RIGHT);
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
