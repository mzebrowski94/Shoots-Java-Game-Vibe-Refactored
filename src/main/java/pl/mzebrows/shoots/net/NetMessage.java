// src/main/java/pl/mzebrows/shoots/net/NetMessage.java
package pl.mzebrows.shoots.net;

import pl.mzebrows.shoots.world.PlayWorld;

/**
 * Closed set of wire messages exchanged between host and clients (see {@code OnlineMode.md}). Kept as a
 * sealed hierarchy of small records so {@link MessageCodec} can switch exhaustively and so adding a
 * message is a compile-checked change. All variants are tiny and flat -- input sync, not world state.
 */
public sealed interface NetMessage
        permits NetMessage.Join, NetMessage.Welcome, NetMessage.Input, NetMessage.Frame, NetMessage.Control,
                NetMessage.Hash {

    /** client -> server: request to join, with a display name and the protocol version. */
    record Join(String name, int protocolVersion) implements NetMessage { }

    /**
     * server -> client: everything a joiner needs to build an identical world -- its assigned player
     * slot, the player count, the master seed, and the host's match code. The full {@code GameConfig}
     * is reconstructed locally from the same {@code game.properties} via seed + player count (v1 assumes
     * peers share a build); a full config payload is deferred (see {@code OnlineMode.md}).
     */
    record Welcome(int slot, int playerCount, long seed, String matchCode) implements NetMessage { }

    /** client -> server: this client's input for a command frame. */
    record Input(long frame, TickInput input) implements NetMessage { }

    /** server -> clients: the authoritative full input set for a command frame (one entry per slot). */
    record Frame(long frame, TickInput[] bySlot) implements NetMessage { }

    /** server -> clients: a host-dictated round-flow transition for a frame. */
    record Control(long frame, ControlEvent.Kind kind) implements NetMessage { }

    /** client -> server: the client's {@code WorldHash} at a command frame, for desync detection. */
    record Hash(long frame, long hash) implements NetMessage { }

    /** Convenience: neutral aim constant re-exported so callers needn't import {@link PlayWorld}. */
    static PlayWorld.AimInput none() {
        return PlayWorld.AimInput.NONE;
    }
}
