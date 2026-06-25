// src/main/java/pl/mzebrows/shoots/net/InputFrame.java
package pl.mzebrows.shoots.net;

/**
 * The authoritative full set of player inputs for one command frame: exactly one {@link TickInput} per
 * player slot, indexed by 0-based player id. Every peer applies the identical {@code InputFrame}, so all
 * peers advance the same command frame identically (the lockstep invariant; see {@code OnlineMode.md}).
 *
 * <p>The {@code bySlot} array is owned by this frame; callers read it by index and must not mutate it.
 */
public record InputFrame(long frame, TickInput[] bySlot) {

    public InputFrame {
        if (frame < 0) {
            throw new IllegalArgumentException("frame must be >= 0: " + frame);
        }
        if (bySlot == null || bySlot.length == 0) {
            throw new IllegalArgumentException("bySlot must be non-empty");
        }
        for (int s = 0; s < bySlot.length; s++) {
            if (bySlot[s] == null) {
                throw new IllegalArgumentException("missing input for slot " + s);
            }
        }
    }

    /** Number of player slots in this frame. */
    public int slots() {
        return bySlot.length;
    }

    /** This slot's input. */
    public TickInput slot(int playerId) {
        return bySlot[playerId];
    }
}
