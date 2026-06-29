// src/main/java/pl/mzebrows/shoots/net/NetMessage.java
package pl.mzebrows.shoots.net;

import pl.mzebrows.shoots.world.PlayWorld;

/**
 * Closed set of wire messages exchanged between host and clients (see OnlineMode.md). Sealed hierarchy
 * of small records so MessageCodec can switch exhaustively; adding a message is a compile-checked change.
 */
public sealed interface NetMessage
        permits NetMessage.Join, NetMessage.Welcome, NetMessage.Input, NetMessage.Frame, NetMessage.Control,
                NetMessage.Hash, NetMessage.Lobby, NetMessage.Start, NetMessage.Pause {

    /** client -> server: request to join, with a display name and the protocol version. */
    record Join(String name, int protocolVersion) implements NetMessage { }

    /** server -> client: the joiner's assigned lobby slot and the host's match code (sent at connect). */
    record Welcome(int slot, int playerCount, long seed, String matchCode) implements NetMessage { }

    /** client -> server: this client's input for a command frame. */
    record Input(long frame, TickInput input) implements NetMessage { }

    /** server -> clients: the authoritative full input set for a command frame (one entry per slot). */
    record Frame(long frame, TickInput[] bySlot) implements NetMessage { }

    /** server -> clients: a host-dictated round-flow transition for a frame. */
    record Control(long frame, ControlEvent.Kind kind) implements NetMessage { }

    /** client -> server: the client's WorldHash at a command frame, for desync detection. */
    record Hash(long frame, long hash) implements NetMessage { }

    /**
     * server -> clients: the live lobby roster (waiting room). Indexed by lobby slot; entry 0 is the host,
     * an empty string is an open slot, otherwise the player's display name.
     */
    record Lobby(String[] slotNames) implements NetMessage { }

    /**
     * server -> clients: begin the match. seed = master seed; orderedSlots = participating lobby slots in
     * ascending order, so a peer's final 0-based player id is the INDEX of its own lobby slot within
     * orderedSlots, and the player count is orderedSlots.length. {@code roundTimeSeconds}/{@code roundLimit}
     * carry the host's menu-chosen round pacing so every peer builds an identical match (#7); {@code 0}
     * means "unset -> keep the local default" (back-compatible with the pre-#7 two-field form).
     */
    record Start(long seed, int[] orderedSlots, int roundTimeSeconds, int roundLimit,
                 double discSpeed, int maxDiscBounces, int maxLaserBounces,
                 double disruptionSeconds, double graceSeconds) implements NetMessage {
        /** Round pacing only, no gameplay payload (pre-#4.8 form); gameplay fields default to 0 = unset. */
        Start(long seed, int[] orderedSlots, int roundTimeSeconds, int roundLimit) {
            this(seed, orderedSlots, roundTimeSeconds, roundLimit, 0.0, 0, 0, 0.0, 0.0);
        }
        /** Back-compatible form without round pacing (used by tests/older callers); leaves all at 0 = unset. */
        Start(long seed, int[] orderedSlots) {
            this(seed, orderedSlots, 0, 0);
        }
    }

    /**
     * pause toggle. client -> server: a request to pause/resume (the host is the authority); server ->
     * clients: the authoritative broadcast that a given player slot paused or resumed the whole match (#3).
     * {@code slot} is the player who paused, so peers can show "PLAYER n PAUSED".
     */
    record Pause(int slot, boolean paused) implements NetMessage { }

    /** Convenience: neutral aim constant re-exported so callers needn't import PlayWorld. */
    static PlayWorld.AimInput none() {
        return PlayWorld.AimInput.NONE;
    }
}
