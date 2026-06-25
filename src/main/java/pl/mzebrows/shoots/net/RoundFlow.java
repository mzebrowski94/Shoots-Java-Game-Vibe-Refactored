// src/main/java/pl/mzebrows/shoots/net/RoundFlow.java
package pl.mzebrows.shoots.net;

/**
 * Mode-aware owner of the round-flow phase (BEGIN -> CONTINUES -> ENDS), decoupled from AWT so the
 * transition policy is unit-testable. The local transition CONDITIONS still live in the caller
 * ({@code PlayingState}: animation completion, round-timer expiry); this class only decides WHO may act:
 *
 * <ul>
 *   <li>{@code OFFLINE} — advance whenever the caller reports the local condition is ready (today's
 *       single-machine behaviour, unchanged).</li>
 *   <li>{@code HOST} — same as offline, AND broadcast each transition as a {@link ControlEvent} so
 *       clients can follow.</li>
 *   <li>{@code CLIENT} — ignore the local condition; advance only when the matching host event arrives
 *       on the {@link ControlChannel}. This is what removes the render-coupled, machine-dependent phase
 *       timing for networked play (see {@code OnlineMode.md}, F2).</li>
 * </ul>
 */
public final class RoundFlow {

    /** The round-flow phase, mirroring {@code PlayingState}'s legacy cycle. */
    public enum Phase { BEGIN, CONTINUES, ENDS }

    /** Result of resolving the ENDS phase. */
    public enum EndsOutcome { STAY, NEXT_ROUND, MATCH_OVER }

    private final GameMode mode;
    private final ControlChannel channel;
    private Phase phase = Phase.BEGIN;

    public RoundFlow(GameMode mode, ControlChannel channel) {
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
        if (mode != GameMode.OFFLINE && channel == null) {
            throw new IllegalArgumentException("HOST/CLIENT require a ControlChannel");
        }
        this.mode = mode;
        this.channel = channel;
    }

    /** A single-machine flow that decides every transition locally and broadcasts nothing. */
    public static RoundFlow offline() {
        return new RoundFlow(GameMode.OFFLINE, null);
    }

    public Phase phase() {
        return phase;
    }

    public GameMode mode() {
        return mode;
    }

    /** Resets to BEGIN (a full match restart). */
    public void reset() {
        phase = Phase.BEGIN;
    }

    /** BEGIN -> CONTINUES. Returns whether the transition happened this call. */
    public boolean enterContinues(long frame, boolean localReady) {
        return advance(frame, localReady, Phase.BEGIN, Phase.CONTINUES, ControlEvent.Kind.ENTER_CONTINUES);
    }

    /** CONTINUES -> ENDS. Returns whether the transition happened this call. */
    public boolean enterEnds(long frame, boolean localReady) {
        return advance(frame, localReady, Phase.CONTINUES, Phase.ENDS, ControlEvent.Kind.ENTER_ENDS);
    }

    private boolean advance(long frame, boolean localReady, Phase from, Phase to, ControlEvent.Kind kind) {
        if (phase != from) {
            return false;
        }
        if (mode == GameMode.CLIENT) {
            ControlEvent next = channel.peek();
            if (next != null && next.kind() == kind) {
                channel.poll();
                phase = to;
                return true;
            }
            return false;
        }
        if (!localReady) {
            return false;
        }
        phase = to;
        if (mode == GameMode.HOST) {
            channel.send(new ControlEvent(frame, kind));
        }
        return true;
    }

    /**
     * Resolves the ENDS phase into the next round (BEGIN) or match over. {@code localReady} is the
     * caller's "round-end animation finished" signal (used by OFFLINE/HOST); {@code matchOver} is the
     * world's match-decided query. A CLIENT ignores both and follows the host's NEXT_ROUND / MATCH_OVER.
     */
    public EndsOutcome resolveEnds(long frame, boolean localReady, boolean matchOver) {
        if (phase != Phase.ENDS) {
            return EndsOutcome.STAY;
        }
        if (mode == GameMode.CLIENT) {
            ControlEvent next = channel.peek();
            if (next == null) {
                return EndsOutcome.STAY;
            }
            if (next.kind() == ControlEvent.Kind.MATCH_OVER) {
                channel.poll();
                return EndsOutcome.MATCH_OVER;
            }
            if (next.kind() == ControlEvent.Kind.NEXT_ROUND) {
                channel.poll();
                phase = Phase.BEGIN;
                return EndsOutcome.NEXT_ROUND;
            }
            return EndsOutcome.STAY;
        }
        if (!localReady) {
            return EndsOutcome.STAY;
        }
        if (matchOver) {
            if (mode == GameMode.HOST) {
                channel.send(new ControlEvent(frame, ControlEvent.Kind.MATCH_OVER));
            }
            return EndsOutcome.MATCH_OVER;
        }
        phase = Phase.BEGIN;
        if (mode == GameMode.HOST) {
            channel.send(new ControlEvent(frame, ControlEvent.Kind.NEXT_ROUND));
        }
        return EndsOutcome.NEXT_ROUND;
    }
}
