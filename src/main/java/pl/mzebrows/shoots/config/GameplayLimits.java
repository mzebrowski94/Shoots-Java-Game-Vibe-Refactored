// src/main/java/pl/mzebrows/shoots/config/GameplayLimits.java
package pl.mzebrows.shoots.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Min/max/step caps for the player-editable gameplay options (the menu's GAMEPLAY OPTIONS screen). The
 * DEFAULT for each option is its base value in {@code game.properties}; this record only bounds how far the
 * player may move it. Caps live beside their value in {@code game.properties} under the hierarchical name
 * {@code <key>.min} / {@code <key>.max} / {@code <key>.step}. Every cap is REQUIRED -- a missing one throws
 * a {@link ConfigException} (no code-side defaults; the properties file is the single source of truth).
 *
 * @param roundTime          round duration range, seconds
 * @param roundLimit         rounds-per-match range
 * @param discBounces        max disc bounce range
 * @param discSpeed          disc base speed range (power-shot speed/bounces stay proportional to this)
 * @param laserBounces       max laser projection range
 * @param disruptionSeconds  base-disruption (distortion) effect duration range, seconds
 * @param graceSeconds       post-disruption guard/shield window range, seconds
 * @param minPort            lowest selectable host port
 * @param maxPort            highest selectable host port
 */
public record GameplayLimits(
        IntRange roundTime,
        IntRange roundLimit,
        IntRange discBounces,
        DoubleRange discSpeed,
        IntRange laserBounces,
        DoubleRange disruptionSeconds,
        DoubleRange graceSeconds,
        int minPort,
        int maxPort) {

    /** Inclusive integer range with a cycling step. */
    public record IntRange(int min, int max, int step) {
        public int clamp(int v) {
            return Math.max(min, Math.min(max, v));
        }
    }

    /** Inclusive floating-point range with a step (values are kept on the step grid by the menu). */
    public record DoubleRange(double min, double max, double step) {
        public double clamp(double v) {
            return Math.max(min, Math.min(max, v));
        }
    }

    private static final String RESOURCE = "game.properties";

    /** Loads caps from the bundled {@code game.properties}; a missing cap is a fatal {@link ConfigException}. */
    public static GameplayLimits load() {
        var props = new Properties();
        try (InputStream in = GameplayLimits.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new ConfigException("Required config resource not found on classpath: " + RESOURCE);
            }
            props.load(in);
        } catch (IOException e) {
            throw new ConfigException("Failed to read '" + RESOURCE + "': " + e.getMessage());
        }
        return fromProperties(props);
    }

    /** Builds caps from already-parsed properties; every cap key is required. */
    public static GameplayLimits fromProperties(Properties props) {
        return new GameplayLimits(
                intRange(props, "round.timeSeconds"),
                intRange(props, "round.limit"),
                intRange(props, "disc.maxBounces"),
                doubleRange(props, "disc.speed"),
                intRange(props, "laser.maxBounces"),
                doubleRange(props, "disruption.durationSeconds"),
                doubleRange(props, "disruption.graceSeconds"),
                requireInt(props, "online.port.min"),
                requireInt(props, "online.port.max"));
    }

    private static IntRange intRange(Properties p, String key) {
        return new IntRange(requireInt(p, key + ".min"), requireInt(p, key + ".max"), requireInt(p, key + ".step"));
    }

    private static DoubleRange doubleRange(Properties p, String key) {
        return new DoubleRange(requireDouble(p, key + ".min"), requireDouble(p, key + ".max"), requireDouble(p, key + ".step"));
    }

    private static int requireInt(Properties p, String key) {
        String raw = require(p, key);
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new ConfigException("Invalid int for '" + key + "': '" + raw + "'");
        }
    }

    private static double requireDouble(Properties p, String key) {
        String raw = require(p, key);
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            throw new ConfigException("Invalid double for '" + key + "': '" + raw + "'");
        }
    }

    private static String require(Properties p, String key) {
        String raw = p.getProperty(key);
        if (raw == null || raw.isBlank()) {
            throw new ConfigException("Missing required property: '" + key + "'");
        }
        return raw.trim();
    }
}
