// src/main/java/pl/mzebrows/shoots/net/MatchCode.java
package pl.mzebrows.shoots.net;

import java.security.SecureRandom;
import java.util.Random;

/**
 * Generates a short, human-friendly match code (e.g. {@code ABCXYZ}) so players can identify a match
 * among several. This is match METADATA only -- it never enters the simulation or the lockstep hash
 * (see {@code OnlineMode.md}). The host creates one per session; it is shown in the menu and beaconed
 * in LAN discovery.
 */
public final class MatchCode {

    /** Number of letters in a code. */
    public static final int LENGTH = 6;

    private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    private MatchCode() {
    }

    /** A fresh random code using a cryptographically-strong source (host session creation). */
    public static String generate() {
        return generate(new SecureRandom());
    }

    /** A code drawn from the supplied RNG (deterministic for a seeded {@link Random}, for tests). */
    public static String generate(Random rng) {
        var sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET[rng.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }

    /** Whether {@code code} is a well-formed match code ({@value #LENGTH} uppercase A-Z letters). */
    public static boolean isValid(String code) {
        if (code == null || code.length() != LENGTH) {
            return false;
        }
        for (int i = 0; i < LENGTH; i++) {
            char c = code.charAt(i);
            if (c < 'A' || c > 'Z') {
                return false;
            }
        }
        return true;
    }
}
