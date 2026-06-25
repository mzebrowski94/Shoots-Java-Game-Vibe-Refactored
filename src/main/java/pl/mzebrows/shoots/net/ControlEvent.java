// src/main/java/pl/mzebrows/shoots/net/ControlEvent.java
package pl.mzebrows.shoots.net;

/**
 * A host-dictated round-flow transition, stamped with the command frame it takes effect on so a client
 * applies it at the same point in the deterministic timeline (see {@code OnlineMode.md}, the `CONTROL`
 * message). Round/match flow is host-authoritative: only the host produces these; clients consume them.
 */
public record ControlEvent(long frame, Kind kind) {

    /** The flow transitions a client must be told about (the 3-phase cycle + match end). */
    public enum Kind {
        /** BEGIN -> CONTINUES: the round intro is over, play starts. */
        ENTER_CONTINUES,
        /** CONTINUES -> ENDS: the round timer expired and discs have settled. */
        ENTER_ENDS,
        /** ENDS -> BEGIN: start the next round. */
        NEXT_ROUND,
        /** ENDS -> game over: the match is decided. */
        MATCH_OVER
    }

    public ControlEvent {
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
    }
}
