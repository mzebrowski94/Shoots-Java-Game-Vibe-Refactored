// src/test/java/pl/mzebrows/shoots/net/OnlineLoopIntegrationTest.java
package pl.mzebrows.shoots.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import pl.mzebrows.shoots.config.AiConfig;
import pl.mzebrows.shoots.config.CollisionConfig;
import pl.mzebrows.shoots.config.ColorPalette;
import pl.mzebrows.shoots.config.DiscConfig;
import pl.mzebrows.shoots.config.GameConfig;
import pl.mzebrows.shoots.config.GridConfig;
import pl.mzebrows.shoots.config.RgbColor;
import pl.mzebrows.shoots.config.RoundConfig;
import pl.mzebrows.shoots.entity.Entity;
import pl.mzebrows.shoots.score.CapturePoint;
import pl.mzebrows.shoots.world.PlayWorld;

/**
 * End-to-end proof of the online stack (F1 coordinator + F2 flow + F3 transport) over REAL localhost
 * TCP: a client builds its world from the seed delivered in WELCOME, then host and client exchange
 * inputs each command frame and stay BIT-IDENTICAL; a host round-flow transition broadcast as CONTROL
 * is followed by the client. The host seed is random (not shared in code), so identical worlds prove the
 * seed truly travelled over the wire.
 */
class OnlineLoopIntegrationTest {

    private static final int PLAYERS = 2;
    private static final int STEPS_PER_FRAME = 4;
    private static final int FRAMES = 90;
    private static final long AWAIT_MS = 4000;

    private GameConfig config(long seed) {
        var palette = new ColorPalette(
                new RgbColor(95, 99, 104, 255), new RgbColor(25, 25, 25, 255),
                new RgbColor(68, 74, 80, 255), new RgbColor(102, 0, 102, 255),
                new RgbColor(102, 75, 102, 255), new RgbColor(192, 192, 192, 255),
                new RgbColor(68, 74, 80, 255), new RgbColor(35, 35, 35, 10),
                List.of(
                        new RgbColor(124, 252, 0, 255), new RgbColor(48, 213, 200, 255),
                        new RgbColor(252, 3, 0, 255), new RgbColor(237, 26, 116, 255)));
        return new GameConfig(PLAYERS, seed,
                new GridConfig(36, 25),
                new DiscConfig(18, 10, 2.0, 7, 3, 3, 4),
                new CollisionConfig(4),
                new RoundConfig(15, 2, 2, 1),
                palette,
                new AiConfig(24, 4, true));
    }

    private TickInput hostTick(long f) {
        return new TickInput((f / 5) % 2 == 0 ? PlayWorld.AimInput.LEFT : PlayWorld.AimInput.RIGHT, f % 11 == 0);
    }

    private TickInput clientTick(long f) {
        return new TickInput((f / 7) % 2 == 0 ? PlayWorld.AimInput.RIGHT : PlayWorld.AimInput.LEFT, f % 13 == 0);
    }

    @Test
    @Timeout(30)
    void hostAndClientWorldsStayBitIdenticalOverTcp() throws Exception {
        long hostSeed = new Random().nextLong();
        var hostWorld = new PlayWorld(config(hostSeed));

        try (var server = new TcpServer(0, PLAYERS, hostSeed, MatchCode.generate(new Random(hostSeed)))) {
            server.start();
            try (var clientTransport = TcpClientTransport.connect("127.0.0.1", server.port(), "Client")) {
                NetMessage.Welcome welcome = clientTransport.awaitWelcome(AWAIT_MS);
                assertThat(welcome.seed()).as("seed travelled over the wire").isEqualTo(hostSeed);
                assertThat(welcome.playerCount()).isEqualTo(PLAYERS);
                assertThat(MatchCode.isValid(welcome.matchCode())).isTrue();

                // The client builds its world purely from the WELCOME seed -- nothing shared in code.
                var clientWorld = new PlayWorld(config(welcome.seed()));

                var host = new OnlineHost(hostWorld, server, STEPS_PER_FRAME, 0);
                var client = new OnlineClient(clientWorld, clientTransport, STEPS_PER_FRAME);

                assertThat(fingerprint(clientWorld)).isEqualTo(fingerprint(hostWorld));

                for (long frame = 0; frame < FRAMES; frame++) {
                    client.sendLocalInput(frame, clientTick(frame));
                    host.submitLocalInput(frame, hostTick(frame));
                    awaitHostRelease(host, frame);   // host gathers both inputs, broadcasts + applies
                    awaitClientApplied(client, frame); // client receives + applies the same frame
                    assertThat(fingerprint(clientWorld))
                            .as("worlds diverged at frame %d (seed %d)", frame, hostSeed)
                            .isEqualTo(fingerprint(hostWorld));
                }
            }
        }
    }

    @Test
    @Timeout(30)
    void clientRoundFlowFollowsHostControlOverTcp() throws Exception {
        long hostSeed = 1234L;
        var hostWorld = new PlayWorld(config(hostSeed));

        try (var server = new TcpServer(0, PLAYERS, hostSeed, "ABCXYZ")) {
            server.start();
            try (var clientTransport = TcpClientTransport.connect("127.0.0.1", server.port(), "Client")) {
                clientTransport.awaitWelcome(AWAIT_MS);
                var client = new OnlineClient(new PlayWorld(config(hostSeed)), clientTransport, STEPS_PER_FRAME);
                var host = new OnlineHost(hostWorld, server, STEPS_PER_FRAME, 0);

                // Host decides BEGIN -> CONTINUES and broadcasts it; the client must follow.
                assertThat(host.flow().enterContinues(0, true)).isTrue();
                assertThat(host.flow().phase()).isEqualTo(RoundFlow.Phase.CONTINUES);
                awaitClientPhase(client, RoundFlow.Phase.CONTINUES);

                // ...then CONTINUES -> ENDS.
                assertThat(host.flow().enterEnds(10, true)).isTrue();
                awaitClientPhase(client, RoundFlow.Phase.ENDS);
            }
        }
    }

    // -- await helpers (bounded, localhost is fast) -------------------------

    private void awaitHostRelease(OnlineHost host, long frame) {
        long deadline = System.nanoTime() + AWAIT_MS * 1_000_000L;
        while (System.nanoTime() < deadline) {
            host.pumpInbound();
            host.tryAdvance();
            if (host.lastReleasedFrame() >= frame) {
                return;
            }
            sleepBriefly();
        }
        throw new AssertionError("host did not release frame " + frame + " within " + AWAIT_MS + " ms");
    }

    private void awaitClientApplied(OnlineClient client, long frame) {
        long deadline = System.nanoTime() + AWAIT_MS * 1_000_000L;
        while (System.nanoTime() < deadline) {
            client.pump();
            if (client.lastAppliedFrame() >= frame) {
                return;
            }
            sleepBriefly();
        }
        throw new AssertionError("client did not apply frame " + frame + " within " + AWAIT_MS + " ms");
    }

    private void awaitClientPhase(OnlineClient client, RoundFlow.Phase expected) {
        long deadline = System.nanoTime() + AWAIT_MS * 1_000_000L;
        while (System.nanoTime() < deadline) {
            client.pump();
            // Drive the client's follow-the-host transition (localReady is ignored in CLIENT mode).
            client.flow().enterContinues(0, false);
            client.flow().enterEnds(0, false);
            if (client.flow().phase() == expected) {
                return;
            }
            sleepBriefly();
        }
        throw new AssertionError("client did not reach phase " + expected + " within " + AWAIT_MS + " ms");
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** The gameplay-authoritative slice that must match across peers. */
    private String fingerprint(PlayWorld world) {
        var sb = new StringBuilder();
        sb.append("active=").append(world.totalActiveDiscs());
        List<Entity> discs = world.discs();
        sb.append(" discs=").append(discs.size());
        for (Entity d : discs) {
            sb.append('|').append(d.getOwnerId())
              .append(',').append(d.getX())
              .append(',').append(d.getY())
              .append(',').append(d.getAngle())
              .append(',').append(d.getBounces());
        }
        for (int p = 0; p < world.playerCount(); p++) {
            sb.append(" aim").append(p).append('=').append(world.aimOf(p).currentAngle());
        }
        for (CapturePoint cp : world.scoring().points()) {
            sb.append(" cp").append(cp.getTileX()).append(':').append(cp.getTileY())
              .append('=').append(cp.getOwnerId()).append('/').append(cp.getLevel());
        }
        return sb.toString();
    }
}
