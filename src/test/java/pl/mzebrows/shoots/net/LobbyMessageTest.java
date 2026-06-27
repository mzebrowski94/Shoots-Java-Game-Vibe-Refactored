// src/test/java/pl/mzebrows/shoots/net/LobbyMessageTest.java
package pl.mzebrows.shoots.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/** Round-trips the F7 lobby messages (roster + start), including open slots and framing. */
class LobbyMessageTest {

    @Test
    void roundTripsLobbyRosterWithOpenSlots() {
        var msg = new NetMessage.Lobby(new String[] {"Host", "mateusz", "", ""});
        var decoded = (NetMessage.Lobby) MessageCodec.decode(MessageCodec.encode(msg));
        assertThat(decoded.slotNames()).containsExactly("Host", "mateusz", "", "");
    }

    @Test
    void roundTripsStartWithCompactedSlots() {
        var msg = new NetMessage.Start(1234567890123L, new int[] {0, 1, 3});
        var decoded = (NetMessage.Start) MessageCodec.decode(MessageCodec.encode(msg));
        assertThat(decoded.seed()).isEqualTo(1234567890123L);
        assertThat(decoded.orderedSlots()).containsExactly(0, 1, 3);
    }

    @Test
    void lobbyAndStartSurviveStreamFraming() throws IOException {
        var out = new ByteArrayOutputStream();
        MessageCodec.writeFrame(out, new NetMessage.Lobby(new String[] {"A", "", "C", ""}));
        MessageCodec.writeFrame(out, new NetMessage.Start(42L, new int[] {0, 2}));

        var in = new ByteArrayInputStream(out.toByteArray());
        var lobby = (NetMessage.Lobby) MessageCodec.readFrame(in);
        var start = (NetMessage.Start) MessageCodec.readFrame(in);
        assertThat(lobby.slotNames()).containsExactly("A", "", "C", "");
        assertThat(start.orderedSlots()).containsExactly(0, 2);
        assertThat(MessageCodec.readFrame(in)).isNull();
    }
}
