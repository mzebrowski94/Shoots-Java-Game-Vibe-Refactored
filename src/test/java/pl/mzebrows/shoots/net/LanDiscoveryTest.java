// src/test/java/pl/mzebrows/shoots/net/LanDiscoveryTest.java
package pl.mzebrows.shoots.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Discovery table TTL/expiry (headless, injected clock) + a real loopback-UDP beacon->discovery smoke. */
class LanDiscoveryTest {

    private static final long TTL_MS = 1000;
    private static final long TTL_NS = TTL_MS * 1_000_000L;

    @Test
    void recordsBeaconsAndExpiresStaleOnes() {
        var discovery = new LanDiscovery(TTL_MS);
        discovery.record("10.0.0.5", new LanAnnouncement("ABCXYZ", "Host", 48900, 2, true), 0L);

        assertThat(discovery.matches(0L)).hasSize(1);
        assertThat(discovery.matches(TTL_NS / 2)).hasSize(1);          // still fresh
        assertThat(discovery.matches(TTL_NS + 1)).isEmpty();           // expired (no recent beacon)

        // A refreshed beacon keeps it alive past the original TTL.
        discovery.record("10.0.0.5", new LanAnnouncement("ABCXYZ", "Host", 48900, 2, true), TTL_NS);
        assertThat(discovery.matches(TTL_NS + 1)).hasSize(1);
    }

    @Test
    void tracksMultipleDistinctHostsAndCarriesDetails() {
        var discovery = new LanDiscovery(TTL_MS);
        discovery.record("10.0.0.5", new LanAnnouncement("AAAAAA", "One", 48900, 2, true), 0L);
        discovery.record("10.0.0.6", new LanAnnouncement("BBBBBB", "Two", 48901, 4, false), 0L);

        List<DiscoveredMatch> live = discovery.matches(0L);
        assertThat(live).hasSize(2);
        DiscoveredMatch first = live.get(0); // ordered by match code
        assertThat(first.matchCode()).isEqualTo("AAAAAA");
        assertThat(first.host()).isEqualTo("10.0.0.5");
        assertThat(first.port()).isEqualTo(48900);
        assertThat(first.players()).isEqualTo(2);
        assertThat(first.joinable()).isTrue();
    }

    @Test
    @Timeout(15)
    void discoversAHostBeaconOverLoopbackUdp() throws Exception {
        try (var discovery = new LanDiscovery(3000)) {
            discovery.start(0); // ephemeral port
            int port = discovery.port();

            var announcement = new LanAnnouncement("ABCXYZ", "Host", 55555, 2, true);
            try (var beacon = new LanBeacon(InetAddress.getByName("127.0.0.1"), port, 100, () -> announcement)) {
                beacon.start();

                DiscoveredMatch found = awaitMatch(discovery, "ABCXYZ");
                assertThat(found.port()).isEqualTo(55555);
                assertThat(found.players()).isEqualTo(2);
                assertThat(found.joinable()).isTrue();
                assertThat(found.host()).isEqualTo("127.0.0.1");
            }
        }
    }

    private DiscoveredMatch awaitMatch(LanDiscovery discovery, String code) throws InterruptedException {
        long deadline = System.nanoTime() + 10_000L * 1_000_000L;
        while (System.nanoTime() < deadline) {
            for (DiscoveredMatch m : discovery.matches()) {
                if (m.matchCode().equals(code)) {
                    return m;
                }
            }
            Thread.sleep(5);
        }
        throw new AssertionError("did not discover match " + code + " over loopback UDP");
    }
}
