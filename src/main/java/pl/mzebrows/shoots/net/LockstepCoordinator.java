// src/main/java/pl/mzebrows/shoots/net/LockstepCoordinator.java
package pl.mzebrows.shoots.net;

import java.util.HashMap;
import java.util.Map;

/**
 * Buffers per-slot {@link TickInput}s keyed by command frame and releases complete {@link InputFrame}s
 * strictly IN ORDER, so a peer advances the simulation only once EVERY player's input for the next
 * frame has arrived -- the lockstep gate. {@link #tryRelease()} returning {@code null} is the (correct)
 * stall point when a slot's input is still in flight.
 *
 * <p>Input delay is a scheduling concern of the caller, not of this gate: the loop reads local input and
 * submits it for {@code currentFrame + inputDelayFrames} to absorb latency/jitter; this class only
 * stores and gates. AWT-free and transport-free, so it is unit-testable without sockets.
 */
public final class LockstepCoordinator {

    private final int playerCount;
    private final int inputDelayFrames;
    private final Map<Long, TickInput[]> pending = new HashMap<>();
    private long nextFrameToRelease;

    /**
     * @param playerCount      number of player slots that must report each frame (>= 1)
     * @param inputDelayFrames how many command frames ahead local input is scheduled (>= 0); stored for
     *                         the caller's scheduling, not applied to the gate itself
     */
    public LockstepCoordinator(int playerCount, int inputDelayFrames) {
        if (playerCount < 1) {
            throw new IllegalArgumentException("playerCount must be >= 1: " + playerCount);
        }
        if (inputDelayFrames < 0) {
            throw new IllegalArgumentException("inputDelayFrames must be >= 0: " + inputDelayFrames);
        }
        this.playerCount = playerCount;
        this.inputDelayFrames = inputDelayFrames;
        this.nextFrameToRelease = 0;
    }

    /**
     * Records {@code slot}'s input for {@code frame}. A repeated submission for the same (frame, slot)
     * overwrites the earlier one; a submission for a frame that has already been released is ignored
     * (it can no longer change the deterministic timeline).
     */
    public void submit(int slot, long frame, TickInput input) {
        if (slot < 0 || slot >= playerCount) {
            throw new IllegalArgumentException("slot out of range [0," + playerCount + "): " + slot);
        }
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        if (frame < nextFrameToRelease) {
            return;
        }
        pending.computeIfAbsent(frame, f -> new TickInput[playerCount])[slot] = input;
    }

    /** Whether every slot's input for {@code frame} has been submitted. */
    public boolean isComplete(long frame) {
        TickInput[] slots = pending.get(frame);
        if (slots == null) {
            return false;
        }
        for (TickInput in : slots) {
            if (in == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Releases the next in-order frame if it is complete, advancing the release cursor; returns
     * {@code null} when that frame is not yet ready (the lockstep stall). Out-of-order submissions are
     * fine -- a later frame is held until every earlier frame has been released.
     */
    public InputFrame tryRelease() {
        if (!isComplete(nextFrameToRelease)) {
            return null;
        }
        TickInput[] slots = pending.remove(nextFrameToRelease);
        InputFrame frame = new InputFrame(nextFrameToRelease, slots);
        nextFrameToRelease++;
        return frame;
    }

    /** The next frame the coordinator will release (the simulation's current command frame). */
    public long nextFrameToRelease() {
        return nextFrameToRelease;
    }

    public int playerCount() {
        return playerCount;
    }

    public int inputDelayFrames() {
        return inputDelayFrames;
    }
}
