// src/test/java/pl/mzebrows/shoots/net/RoundFlowTest.java
package pl.mzebrows.shoots.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Mode-aware round flow: offline runs locally; a CLIENT reproduces the HOST's exact phase sequence. */
class RoundFlowTest {

    @Test
    void offlineRunsTheRoundCycleLocally() {
        RoundFlow f = RoundFlow.offline();
        assertThat(f.phase()).isEqualTo(RoundFlow.Phase.BEGIN);

        // A not-ready local condition does not advance.
        assertThat(f.enterContinues(0, false)).isFalse();
        assertThat(f.phase()).isEqualTo(RoundFlow.Phase.BEGIN);

        assertThat(f.enterContinues(0, true)).isTrue();
        assertThat(f.phase()).isEqualTo(RoundFlow.Phase.CONTINUES);

        assertThat(f.enterEnds(0, false)).isFalse();
        assertThat(f.enterEnds(0, true)).isTrue();
        assertThat(f.phase()).isEqualTo(RoundFlow.Phase.ENDS);

        assertThat(f.resolveEnds(0, true, false)).isEqualTo(RoundFlow.EndsOutcome.NEXT_ROUND);
        assertThat(f.phase()).isEqualTo(RoundFlow.Phase.BEGIN);

        // Second round, then the match ends.
        f.enterContinues(0, true);
        f.enterEnds(0, true);
        assertThat(f.resolveEnds(0, true, true)).isEqualTo(RoundFlow.EndsOutcome.MATCH_OVER);
        assertThat(f.phase()).isEqualTo(RoundFlow.Phase.ENDS); // stays ENDS; caller routes to game over
    }

    @Test
    void clientFollowsHostThroughAFullMatch() {
        var channel = new LoopbackControlChannel();
        RoundFlow host = new RoundFlow(GameMode.HOST, channel);
        RoundFlow client = new RoundFlow(GameMode.CLIENT, channel);

        for (int round = 0; round < 2; round++) {
            boolean lastRound = (round == 1);

            // BEGIN -> CONTINUES: client cannot advance before the host broadcasts it.
            assertThat(client.enterContinues(0, true))
                    .as("client must wait for the host even when locally 'ready'").isFalse();
            assertThat(host.enterContinues(round, true)).isTrue();
            assertThat(client.enterContinues(0, false)).isTrue();
            assertThat(client.phase()).isEqualTo(host.phase()).isEqualTo(RoundFlow.Phase.CONTINUES);

            // CONTINUES -> ENDS
            assertThat(host.enterEnds(round, true)).isTrue();
            assertThat(client.enterEnds(0, false)).isTrue();
            assertThat(client.phase()).isEqualTo(host.phase()).isEqualTo(RoundFlow.Phase.ENDS);

            // ENDS -> next round / match over: client mirrors the host's decision exactly.
            RoundFlow.EndsOutcome hostOut = host.resolveEnds(round, true, lastRound);
            RoundFlow.EndsOutcome clientOut = client.resolveEnds(0, false, false);
            assertThat(clientOut).isEqualTo(hostOut);
            if (lastRound) {
                assertThat(hostOut).isEqualTo(RoundFlow.EndsOutcome.MATCH_OVER);
            } else {
                assertThat(hostOut).isEqualTo(RoundFlow.EndsOutcome.NEXT_ROUND);
                assertThat(client.phase()).isEqualTo(host.phase()).isEqualTo(RoundFlow.Phase.BEGIN);
            }
        }
        assertThat(channel.pending()).as("client consumed every broadcast event").isZero();
    }

    @Test
    void clientIgnoresLocalReadyAndStallsWithoutEvents() {
        var channel = new LoopbackControlChannel();
        RoundFlow client = new RoundFlow(GameMode.CLIENT, channel);

        // No event queued: even a 'ready' local signal must not advance a client.
        assertThat(client.enterContinues(0, true)).isFalse();
        assertThat(client.phase()).isEqualTo(RoundFlow.Phase.BEGIN);
    }

    @Test
    void clientWillNotConsumeAnEventForADifferentTransition() {
        var channel = new LoopbackControlChannel();
        RoundFlow client = new RoundFlow(GameMode.CLIENT, channel);

        // An ENTER_ENDS while still in BEGIN must not be consumed by enterContinues.
        channel.send(new ControlEvent(0, ControlEvent.Kind.ENTER_ENDS));
        assertThat(client.enterContinues(0, false)).isFalse();
        assertThat(channel.pending()).isEqualTo(1);
        assertThat(client.phase()).isEqualTo(RoundFlow.Phase.BEGIN);
    }

    @Test
    void hostAndClientRequireAChannel() {
        assertThrows(IllegalArgumentException.class, () -> new RoundFlow(GameMode.HOST, null));
        assertThrows(IllegalArgumentException.class, () -> new RoundFlow(GameMode.CLIENT, null));
        // OFFLINE needs none.
        assertThat(RoundFlow.offline().mode()).isEqualTo(GameMode.OFFLINE);
    }
}
