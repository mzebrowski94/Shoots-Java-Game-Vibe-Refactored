// src/main/java/pl/mzebrows/shoots/net/TickInput.java
package pl.mzebrows.shoots.net;

import pl.mzebrows.shoots.world.PlayWorld;

/**
 * One player's input for a single command frame: aim intent plus whether the shoot key is held.
 * This is the smallest unit synced over the wire in the deterministic-lockstep model
 * (see {@code OnlineMode.md}); it is intentionally tiny (an enum + a boolean). Immutable.
 */
public record TickInput(PlayWorld.AimInput aim, boolean shootHeld) {

    private static final TickInput IDLE = new TickInput(PlayWorld.AimInput.NONE, false);

    public TickInput {
        if (aim == null) {
            throw new IllegalArgumentException("aim must not be null");
        }
    }

    /** Neutral input: hold aim, shoot key up. Used to pad frames a player has not (yet) reported. */
    public static TickInput idle() {
        return IDLE;
    }
}
