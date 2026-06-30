// src/main/java/pl/mzebrows/shoots/world/ChargeController.java
package pl.mzebrows.shoots.world;

import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

/**
 * Per-player power-shot charge state machine (human input only; the AI fires power shots directly).
 * Hold-to-charge: a press starts charging (no instant disc), a full hold auto-releases one power disc,
 * and a release before the ring fills fires one normal disc; with the power shot disabled a press fires a
 * normal disc immediately. Owns only the per-player charge timers -- firing and the disrupted check are
 * delegated to {@code PlayWorld} via callbacks, so this stays self-contained and AWT-free.
 */
final class ChargeController {

    private final int[] chargeTicks;
    private final boolean[] chargeConsumed;
    private final boolean[] charging;
    private final boolean[] shootHeldPrev;
    private final int thresholdTicks;
    private final boolean powerEnabled;

    private final IntPredicate disrupted;
    private final IntConsumer fireNormal;
    private final IntConsumer firePower;

    ChargeController(int playerCount, int thresholdTicks, boolean powerEnabled,
                    IntPredicate disrupted, IntConsumer fireNormal, IntConsumer firePower) {
        this.chargeTicks = new int[playerCount];
        this.chargeConsumed = new boolean[playerCount];
        this.charging = new boolean[playerCount];
        this.shootHeldPrev = new boolean[playerCount];
        this.thresholdTicks = thresholdTicks;
        this.powerEnabled = powerEnabled;
        this.disrupted = disrupted;
        this.fireNormal = fireNormal;
        this.firePower = firePower;
    }

    /** Drives one player's shoot key for this step (press=charge, full hold=power, early release=normal). */
    void applyShoot(int playerId, boolean shootHeld) {
        if (disrupted.test(playerId)) {
            // While disrupted the player cannot shoot or charge; swallow the input and keep the charge clear.
            reset(playerId);
            shootHeldPrev[playerId] = shootHeld;
            return;
        }
        boolean prev = shootHeldPrev[playerId];
        boolean pressed = shootHeld && !prev;
        boolean released = !shootHeld && prev;

        if (!powerEnabled) {
            // No charging path: a single tap fires a normal disc on press (classic behaviour).
            if (pressed) {
                fireNormal.accept(playerId);
            }
            shootHeldPrev[playerId] = shootHeld;
            return;
        }

        if (pressed) {
            // Begin charging; do NOT fire a normal disc yet -- it is released on key-up (#3.1).
            chargeTicks[playerId] = 0;
            chargeConsumed[playerId] = false;
            charging[playerId] = true;
        } else if (shootHeld) {
            if (charging[playerId] && !chargeConsumed[playerId]) {
                chargeTicks[playerId]++;
                if (chargeTicks[playerId] >= thresholdTicks) {
                    firePower.accept(playerId);
                    chargeConsumed[playerId] = true;
                    charging[playerId] = false;
                }
            }
        } else if (released) {
            // Released before the ring filled -> fire the normal disc now (#3.1); a consumed charge fires nothing.
            if (!chargeConsumed[playerId]) {
                fireNormal.accept(playerId);
            }
            reset(playerId);
        } else {
            reset(playerId);
        }
        shootHeldPrev[playerId] = shootHeld;
    }

    /** Charge fill for {@code playerId} in {@code [0,1]} (0 when not charging), for the renderer. */
    double progress(int playerId) {
        if (!charging[playerId] || thresholdTicks <= 0) {
            return 0.0;
        }
        double p = (double) chargeTicks[playerId] / thresholdTicks;
        return p < 0.0 ? 0.0 : Math.min(p, 1.0);
    }

    /** Clears the charge state for every player (round reset). */
    void resetAll() {
        for (int p = 0; p < charging.length; p++) {
            reset(p);
            shootHeldPrev[p] = false;
        }
    }

    private void reset(int playerId) {
        chargeTicks[playerId] = 0;
        chargeConsumed[playerId] = false;
        charging[playerId] = false;
    }
}
