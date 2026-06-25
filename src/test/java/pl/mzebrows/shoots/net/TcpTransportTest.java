// src/test/java/pl/mzebrows/shoots/net/TcpTransportTest.java
package pl.mzebrows.shoots.net;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import pl.mzebrows.shoots.world.PlayWorld;

/**
 * Localhost integration smoke for F3: a client connects to the listen server, completes the JOIN/WELCOME
 * handshake (slot + seed + match code), then both directions carry messages (client INPUT -> server,
 * server CONTROL -> client). Uses an ephemeral port and short timeouts; daemon threads + try-with-
 * resources keep it self-cleaning.
 */
class TcpTransportTest {

    private static final long AWAIT_MS = 3000;

    @Test
    @Timeout(15)
    void clientJoinsReceivesWelcomeAndExchangesMessages() throws Exception {
        try (var server = new TcpServer(0, 2, 42L, "ABCXYZ")) {
            server.start();
            int port = server.port();

            try (var client = TcpClientTransport.connect("127.0.0.1", port, "Mateusz")) {
                NetMessage.Welcome welcome = client.awaitWelcome(AWAIT_MS);
                assertThat(welcome.slot()).isEqualTo(1);            // host is slot 0, first client is 1
                assertThat(welcome.playerCount()).isEqualTo(2);
                assertThat(welcome.seed()).isEqualTo(42L);
                assertThat(welcome.matchCode()).isEqualTo("ABCXYZ");

                // client -> server: an INPUT arrives tagged with the client's slot
                client.send(new NetMessage.Input(0, new TickInput(PlayWorld.AimInput.LEFT, true)));
                TcpServer.Inbound inbound = awaitInbound(server);
                assertThat(inbound.slot()).isEqualTo(1);
                assertThat(inbound.message()).isInstanceOf(NetMessage.Input.class);
                assertThat(((NetMessage.Input) inbound.message()).input().aim())
                        .isEqualTo(PlayWorld.AimInput.LEFT);

                // server -> client: a broadcast CONTROL is received intact
                server.broadcast(new NetMessage.Control(7, ControlEvent.Kind.ENTER_CONTINUES));
                NetMessage received = awaitClient(client);
                assertThat(received).isEqualTo(new NetMessage.Control(7, ControlEvent.Kind.ENTER_CONTINUES));
            }
        }
    }

    private TcpServer.Inbound awaitInbound(TcpServer server) throws InterruptedException {
        long deadline = System.nanoTime() + AWAIT_MS * 1_000_000L;
        while (System.nanoTime() < deadline) {
            TcpServer.Inbound message = server.poll();
            if (message != null) {
                return message;
            }
            Thread.sleep(2);
        }
        throw new AssertionError("no inbound from client within " + AWAIT_MS + " ms");
    }

    private NetMessage awaitClient(TcpClientTransport client) throws InterruptedException {
        long deadline = System.nanoTime() + AWAIT_MS * 1_000_000L;
        while (System.nanoTime() < deadline) {
            NetMessage message = client.poll();
            if (message != null) {
                return message;
            }
            Thread.sleep(2);
        }
        throw new AssertionError("no message from server within " + AWAIT_MS + " ms");
    }
}
