// src/main/java/pl/mzebrows/shoots/net/LockstepApplier.java
package pl.mzebrows.shoots.net;

import pl.mzebrows.shoots.world.PlayWorld;

/**
 * Drives a {@link PlayWorld} by one released {@link InputFrame}: applies each slot's input and advances
 * the simulation a fixed number of sub-steps.
 *
 * <p>The held input is re-applied on EVERY sub-step, so one command frame is exactly equivalent to
 * {@code stepsPerFrame} normal per-step updates with that input held -- keeping aim rotation and the
 * hold-to-charge power shot bit-identical to the offline per-step path ({@code PlayInput.apply} +
 * {@code world.step()} once per step). This is what makes a 30 Hz command frame over a 120 Hz sim
 * behave like the live loop.
 */
public final class LockstepApplier {

    private LockstepApplier() {
    }

    /** Applies {@code frame} to {@code world} over {@code stepsPerFrame} fixed sub-steps. */
    public static void apply(PlayWorld world, InputFrame frame, int stepsPerFrame) {
        if (stepsPerFrame < 1) {
            throw new IllegalArgumentException("stepsPerFrame must be >= 1: " + stepsPerFrame);
        }
        if (frame.slots() < world.playerCount()) {
            throw new IllegalArgumentException(
                    "frame has " + frame.slots() + " slots, world needs " + world.playerCount());
        }
        for (int step = 0; step < stepsPerFrame; step++) {
            for (int slot = 0; slot < world.playerCount(); slot++) {
                TickInput in = frame.slot(slot);
                world.applyInput(slot, in.aim(), false);
                world.applyShoot(slot, in.shootHeld());
            }
            world.step();
        }
    }
}
