// src/test/java/pl/mzebrows/shoots/net/LanAnnouncementTest.java
package pl.mzebrows.shoots.net;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Beacon payload round-trips, and unrelated UDP bytes decode to null (so the listener ignores them). */
class LanAnnouncementTest {

    @Test
    void roundTripsThroughTextAndBytes() {
        var ann = new LanAnnouncement("ABCXYZ", "Mateusz' game", 48900, 3, true);
        assertThat(LanAnnouncement.decode(ann.encode())).isEqualTo(ann);
        assertThat(LanAnnouncement.fromBytes(ann.toBytes(), ann.toBytes().length)).isEqualTo(ann);
    }

    @Test
    void ignoresNonBeaconTraffic() {
        assertThat(LanAnnouncement.decode("hello world")).isNull();
        assertThat(LanAnnouncement.decode(null)).isNull();
        assertThat(LanAnnouncement.decode("SHOOOTS1|code=ABCXYZ")).isNull();   // missing port/players
        assertThat(LanAnnouncement.decode("SHOOOTS1|code=ABCXYZ|port=abc|players=2")).isNull(); // bad port
    }

    @Test
    void sanitizesDelimitersFromTheName() {
        var ann = new LanAnnouncement("ABCXYZ", "a|b=c", 5000, 2, false);
        LanAnnouncement decoded = LanAnnouncement.decode(ann.encode());
        assertThat(decoded).isNotNull();
        assertThat(decoded.hostName()).doesNotContain("|").doesNotContain("=");
    }
}
