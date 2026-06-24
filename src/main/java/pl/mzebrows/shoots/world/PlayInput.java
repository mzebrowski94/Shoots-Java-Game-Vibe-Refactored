// src/main/java/pl/mzebrows/shoots/world/PlayInput.java
package pl.mzebrows.shoots.world;

import pl.mzebrows.shoots.input.GameAction;
import pl.mzebrows.shoots.input.InputBridge;

/**
 * AWT-free translator from polled {@link InputBridge} {@link GameAction}s to {@link PlayWorld}
 * per-player intent, replacing the rotation/shoot branching previously embedded in the legacy
 * {@code Player.checkPlayerInput}. Stateless and unit-testable without a graphics context.
 *
 * <p>Rotation is read as held (continuous aim); shooting is read as just-pressed (one disc per
 * key press, matching the legacy edge-triggered fire). Left takes precedence over right when both
 * are held, mirroring the original {@code if/else if} ordering.
 */
public final class PlayInput {

    private static final GameAction[][] PLAYER_ACTIONS = {
            {GameAction.P1_ROTATE_LEFT, GameAction.P1_ROTATE_RIGHT, GameAction.P1_SHOOT},
            {GameAction.P2_ROTATE_LEFT, GameAction.P2_ROTATE_RIGHT, GameAction.P2_SHOOT},
            {GameAction.P3_ROTATE_LEFT, GameAction.P3_ROTATE_RIGHT, GameAction.P3_SHOOT},
            {GameAction.P4_ROTATE_LEFT, GameAction.P4_ROTATE_RIGHT, GameAction.P4_SHOOT},
    };

    private PlayInput() { }

    /**
     * Aim intent for {@code playerId} from current input (left wins ties, matching legacy order). For
     * players whose base faces so that screen-handedness is reversed ({@link PlayWorld#aimKeysMirrored}),
     * the LEFT/RIGHT keys are swapped so each player's key turns the cursor toward THEIR own left/right.
     */
    public static PlayWorld.AimInput aimFor(InputBridge input, int playerId) {
        GameAction[] a = PLAYER_ACTIONS[playerId];
        boolean mirrored = PlayWorld.aimKeysMirrored(playerId);
        if (input.isHeld(a[0])) {
            return mirrored ? PlayWorld.AimInput.RIGHT : PlayWorld.AimInput.LEFT;
        }
        if (input.isHeld(a[1])) {
            return mirrored ? PlayWorld.AimInput.LEFT : PlayWorld.AimInput.RIGHT;
        }
        return PlayWorld.AimInput.NONE;
    }

    /** Whether {@code playerId} just pressed shoot this frame. */
    public static boolean shootFor(InputBridge input, int playerId) {
        return input.isJustPressed(PLAYER_ACTIONS[playerId][2]);
    }

    /** Whether {@code playerId} is currently holding shoot (drives the hold-to-charge power shot). */
    public static boolean shootHeldFor(InputBridge input, int playerId) {
        return input.isHeld(PLAYER_ACTIONS[playerId][2]);
    }

    /**
     * Applies every player's input for this frame to the world in one call: aim intent, then the
     * shoot key as a HELD signal. The world's {@link PlayWorld#applyShoot} turns that into a normal
     * shot on the press edge, a filling charge while held, and an auto-released power shot when full.
     */
    public static void apply(InputBridge input, PlayWorld world) {
        for (int p = 0; p < world.playerCount(); p++) {
            world.applyInput(p, aimFor(input, p), false);
            world.applyShoot(p, shootHeldFor(input, p));
        }
    }
}
