// src/test/java/pl/mzebrows/shoots/net/HostAddressTest.java
package pl.mzebrows.shoots.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Parsing the manual internet {@code host:port} entry. */
class HostAddressTest {

    @Test
    void parsesIpAndHostnameWithPort() {
        HostAddress ip = HostAddress.parse("203.0.113.7:48900");
        assertThat(ip.host()).isEqualTo("203.0.113.7");
        assertThat(ip.port()).isEqualTo(48900);

        HostAddress named = HostAddress.parse("  my-host.example.com : 5555 ");
        assertThat(named.host()).isEqualTo("my-host.example.com");
        assertThat(named.port()).isEqualTo(5555);
        assertThat(named.toString()).isEqualTo("my-host.example.com:5555");
    }

    @Test
    void rejectsMalformedAddresses() {
        assertThatThrownBy(() -> HostAddress.parse("noport")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HostAddress.parse("host:")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HostAddress.parse(":5555")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HostAddress.parse("host:abc")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HostAddress.parse("host:0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HostAddress.parse("host:70000")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HostAddress.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
