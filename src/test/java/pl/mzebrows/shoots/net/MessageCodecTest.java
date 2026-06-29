// src/test/java/pl/mzebrows/shoots/net/MessageCodecTest.java
package pl.mzebrows.shoots.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import pl.mzebrows.shoots.world.PlayWorld;

/** Round-trips every wire message and proves the length-prefixed framing survives chunked reads. */
class MessageCodecTest {

    @Test
    void roundTripsValueMessages() {
        assertRoundTrip(new NetMessage.Join("Cool Player", 1));
        assertRoundTrip(new NetMessage.Welcome(2, 4, 1234567890123L, "ABCXYZ"));
        assertRoundTrip(new NetMessage.Input(17, new TickInput(PlayWorld.AimInput.LEFT, true)));
        assertRoundTrip(new NetMessage.Control(480, ControlEvent.Kind.ENTER_ENDS));
        assertRoundTrip(new NetMessage.Pause(2, true));
        assertRoundTrip(new NetMessage.Pause(0, false));
    }

    @Test
    void startCarriesRoundPacing() {
        var decoded = (NetMessage.Start) MessageCodec.decode(
                MessageCodec.encode(new NetMessage.Start(99L, new int[] {0, 1}, 45, 7)));
        assertThat(decoded.seed()).isEqualTo(99L);
        assertThat(decoded.orderedSlots()).containsExactly(0, 1);
        assertThat(decoded.roundTimeSeconds()).isEqualTo(45);
        assertThat(decoded.roundLimit()).isEqualTo(7);
    }

    @Test
    void startCarriesGameplayOptions() {
        var decoded = (NetMessage.Start) MessageCodec.decode(MessageCodec.encode(
                new NetMessage.Start(5L, new int[] {0, 1, 2}, 90, 4, 4.5, 11, 6, 3.5, 1.5)));
        assertThat(decoded.seed()).isEqualTo(5L);
        assertThat(decoded.orderedSlots()).containsExactly(0, 1, 2);
        assertThat(decoded.roundTimeSeconds()).isEqualTo(90);
        assertThat(decoded.roundLimit()).isEqualTo(4);
        assertThat(decoded.discSpeed()).isEqualTo(4.5);
        assertThat(decoded.maxDiscBounces()).isEqualTo(11);
        assertThat(decoded.maxLaserBounces()).isEqualTo(6);
        assertThat(decoded.disruptionSeconds()).isEqualTo(3.5);
        assertThat(decoded.graceSeconds()).isEqualTo(1.5);
    }

    @Test
    void preGameplayStartDecodesWithZeroPayload() {
        // The 4-arg (pre-#4.8) form leaves the gameplay fields at 0 = unset, so a client keeps its local tunables.
        var decoded = (NetMessage.Start) MessageCodec.decode(
                MessageCodec.encode(new NetMessage.Start(9L, new int[] {0, 1}, 30, 2)));
        assertThat(decoded.discSpeed()).isZero();
        assertThat(decoded.maxDiscBounces()).isZero();
        assertThat(decoded.disruptionSeconds()).isZero();
    }

    @Test
    void roundTripsAMultiSlotFrame() {
        TickInput[] slots = {
                new TickInput(PlayWorld.AimInput.NONE, false),
                new TickInput(PlayWorld.AimInput.RIGHT, true),
                new TickInput(PlayWorld.AimInput.LEFT, false),
        };
        var decoded = (NetMessage.Frame) MessageCodec.decode(MessageCodec.encode(new NetMessage.Frame(9, slots)));
        assertThat(decoded.frame()).isEqualTo(9);
        assertThat(decoded.bySlot()).containsExactly(slots);
    }

    @Test
    void welcomePreservesTheMatchCode() {
        var decoded = (NetMessage.Welcome) MessageCodec.decode(
                MessageCodec.encode(new NetMessage.Welcome(0, 2, 42L, "QWERTZ")));
        assertThat(decoded.matchCode()).isEqualTo("QWERTZ");
        assertThat(decoded.seed()).isEqualTo(42L);
        assertThat(decoded.slot()).isZero();
    }

    @Test
    void framingDeliversMessagesInOrderAndSignalsCleanEof() throws IOException {
        var out = new ByteArrayOutputStream();
        MessageCodec.writeFrame(out, new NetMessage.Join("p", 1));
        MessageCodec.writeFrame(out, new NetMessage.Control(3, ControlEvent.Kind.NEXT_ROUND));

        var in = new ByteArrayInputStream(out.toByteArray());
        assertThat(MessageCodec.readFrame(in)).isEqualTo(new NetMessage.Join("p", 1));
        assertThat(MessageCodec.readFrame(in)).isEqualTo(new NetMessage.Control(3, ControlEvent.Kind.NEXT_ROUND));
        assertThat(MessageCodec.readFrame(in)).as("clean EOF").isNull();
    }

    @Test
    void framingReassemblesAcrossOneByteReads() throws IOException {
        var out = new ByteArrayOutputStream();
        MessageCodec.writeFrame(out, new NetMessage.Welcome(1, 2, 7L, "ABCXYZ"));
        MessageCodec.writeFrame(out, new NetMessage.Input(5, new TickInput(PlayWorld.AimInput.RIGHT, false)));

        // A stream that hands back at most one byte per read -- the worst case for framing.
        InputStream trickle = new InputStream() {
            private final byte[] data = out.toByteArray();
            private int pos;
            @Override public int read() {
                return pos < data.length ? data[pos++] & 0xFF : -1;
            }
            @Override public int read(byte[] b, int off, int len) {
                if (len == 0) {
                    return 0;
                }
                if (pos >= data.length) {
                    return -1;
                }
                b[off] = data[pos++];
                return 1;
            }
        };

        assertThat(MessageCodec.readFrame(trickle)).isEqualTo(new NetMessage.Welcome(1, 2, 7L, "ABCXYZ"));
        assertThat(MessageCodec.readFrame(trickle))
                .isEqualTo(new NetMessage.Input(5, new TickInput(PlayWorld.AimInput.RIGHT, false)));
        assertThat(MessageCodec.readFrame(trickle)).isNull();
    }

    @Test
    void rejectsUnknownMessageType() {
        assertThatThrownBy(() -> MessageCodec.decode("BOGUS|x=1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void assertRoundTrip(NetMessage message) {
        assertThat(MessageCodec.decode(MessageCodec.encode(message))).isEqualTo(message);
    }
}
