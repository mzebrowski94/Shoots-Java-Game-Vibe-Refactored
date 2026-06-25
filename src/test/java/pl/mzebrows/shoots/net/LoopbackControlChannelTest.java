// src/test/java/pl/mzebrows/shoots/net/LoopbackControlChannelTest.java
package pl.mzebrows.shoots.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** FIFO delivery semantics of the in-process control channel (peek is non-destructive). */
class LoopbackControlChannelTest {

    @Test
    void deliversEventsInOrderAndPeekDoesNotRemove() {
        var ch = new LoopbackControlChannel();
        assertThat(ch.poll()).isNull();
        assertThat(ch.peek()).isNull();
        assertThat(ch.pending()).isZero();

        var first = new ControlEvent(1, ControlEvent.Kind.ENTER_CONTINUES);
        var second = new ControlEvent(2, ControlEvent.Kind.ENTER_ENDS);
        ch.send(first);
        ch.send(second);

        assertThat(ch.pending()).isEqualTo(2);
        assertThat(ch.peek()).isSameAs(first);
        assertThat(ch.pending()).isEqualTo(2);

        assertThat(ch.poll()).isSameAs(first);
        assertThat(ch.poll()).isSameAs(second);
        assertThat(ch.poll()).isNull();
    }

    @Test
    void rejectsNullSend() {
        assertThrows(IllegalArgumentException.class, () -> new LoopbackControlChannel().send(null));
    }
}
