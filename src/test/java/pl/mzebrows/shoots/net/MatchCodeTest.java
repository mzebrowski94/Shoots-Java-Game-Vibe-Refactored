// src/test/java/pl/mzebrows/shoots/net/MatchCodeTest.java
package pl.mzebrows.shoots.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** Match-code shape + deterministic generation from a seeded RNG. */
class MatchCodeTest {

    @Test
    void generatesSixUppercaseLetters() {
        String code = MatchCode.generate(new Random(1));
        assertThat(code).hasSize(MatchCode.LENGTH).matches("[A-Z]{6}");
        assertThat(MatchCode.isValid(code)).isTrue();
    }

    @Test
    void isDeterministicForAGivenSeed() {
        assertThat(MatchCode.generate(new Random(99))).isEqualTo(MatchCode.generate(new Random(99)));
    }

    @Test
    void validationRejectsMalformedCodes() {
        assertThat(MatchCode.isValid(null)).isFalse();
        assertThat(MatchCode.isValid("ABC")).isFalse();      // too short
        assertThat(MatchCode.isValid("abcxyz")).isFalse();   // lowercase
        assertThat(MatchCode.isValid("ABC123")).isFalse();   // digits
        assertThat(MatchCode.isValid("ABCXYZ")).isTrue();
    }
}
