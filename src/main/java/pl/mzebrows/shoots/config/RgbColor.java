// src/main/java/pl/mzebrows/shoots/config/RgbColor.java
package pl.mzebrows.shoots.config;

import java.awt.Color;

/** Immutable, AWT-decoupled colour value (0-255 channels) so config stays unit-testable. */
public record RgbColor(int r, int g, int b, int a) {

    public RgbColor {
        requireChannel(r, "r");
        requireChannel(g, "g");
        requireChannel(b, "b");
        requireChannel(a, "a");
    }

    /** Opaque colour. */
    public RgbColor(int r, int g, int b) {
        this(r, g, b, 255);
    }

    private static void requireChannel(int value, String name) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException("Channel %s out of range [0,255]: %d".formatted(name, value));
        }
    }

    /** Converts to an AWT colour at the rendering boundary (headless-safe). */
    public Color toAwt() {
        return new Color(r, g, b, a);
    }
}
