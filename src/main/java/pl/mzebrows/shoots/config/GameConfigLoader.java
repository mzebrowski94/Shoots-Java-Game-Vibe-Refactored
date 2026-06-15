// src/main/java/pl/mzebrows/shoots/config/GameConfigLoader.java
package pl.mzebrows.shoots.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Loads {@link GameConfig} from a classpath {@code .properties} file, falling back to defaults. */
public final class GameConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(GameConfigLoader.class);
    private static final String DEFAULT_RESOURCE = "game.properties";

    private GameConfigLoader() {
    }

    /** Loads from the default classpath resource ({@code game.properties}). */
    public static GameConfig load() {
        return load(DEFAULT_RESOURCE);
    }

    /** Loads from {@code resourceName}; returns {@link #defaults()} if missing or unreadable. */
    public static GameConfig load(String resourceName) {
        var props = new Properties();
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                log.warn("Config resource '{}' not found on classpath; using built-in defaults", resourceName);
                return defaults();
            }
            props.load(in);
            log.debug("Loaded game configuration from '{}'", resourceName);
            return fromProperties(props);
        } catch (IOException | RuntimeException e) {
            log.error("Failed to read config resource '{}'; using built-in defaults", resourceName, e);
            return defaults();
        }
    }

    /** Builds a config from already-parsed properties, defaulting any absent key. */
    public static GameConfig fromProperties(Properties props) {
        var defaults = defaults();

        var grid = new GridConfig(
                intValue(props, "grid.unit", defaults.grid().unit()),
                intValue(props, "grid.tableSize", defaults.grid().tableSize()));

        var disc = new DiscConfig(
                intValue(props, "disc.bigRadius", defaults.disc().bigRadius()),
                intValue(props, "disc.smallRadius", defaults.disc().smallRadius()),
                doubleValue(props, "disc.moveSpeed", defaults.disc().moveSpeed()),
                intValue(props, "disc.maxBounces", defaults.disc().maxBounces()),
                intValue(props, "disc.maxPerPlayer", defaults.disc().maxPerPlayer()));

        var collision = new CollisionConfig(
                intValue(props, "collision.ballCollisionSize", defaults.collision().ballCollisionSize()));

        var round = new RoundConfig(
                intValue(props, "round.roundTimeSeconds", defaults.round().roundTimeSeconds()),
                intValue(props, "round.roundLimit", defaults.round().roundLimit()),
                intValue(props, "round.roundEndDelay", defaults.round().roundEndDelay()),
                intValue(props, "round.animationTime", defaults.round().animationTime()));

        var palette = paletteFromProperties(props, defaults.palette());

        return new GameConfig(
                intValue(props, "playerNumber", defaults.playerNumber()),
                grid, disc, collision, round, palette);
    }

    /** Built-in defaults mirroring the legacy hard-coded values. */
    public static GameConfig defaults() {
        var grid = new GridConfig(36, 25);
        var disc = new DiscConfig(18, 10, 2.0, 7, 3);
        var collision = new CollisionConfig(4);
        var round = new RoundConfig(15, 2, 2, 1);
        var palette = new ColorPalette(
                new RgbColor(95, 99, 104),
                new RgbColor(25, 25, 25),
                new RgbColor(68, 74, 80),
                new RgbColor(102, 0, 102),
                new RgbColor(102, 75, 102),
                new RgbColor(192, 192, 192),
                new RgbColor(68, 74, 80),
                new RgbColor(35, 35, 35, 10),
                List.of(
                        new RgbColor(124, 252, 0),
                        new RgbColor(48, 213, 200),
                        new RgbColor(252, 3, 0),
                        new RgbColor(237, 26, 116)));
        return new GameConfig(2, grid, disc, collision, round, palette);
    }

    private static ColorPalette paletteFromProperties(Properties props, ColorPalette defaults) {
        List<RgbColor> players = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            RgbColor fallback = defaults.players().get(Math.min(i, defaults.players().size()) - 1);
            players.add(colorValue(props, "color.player" + i, fallback));
        }
        return new ColorPalette(
                colorValue(props, "color.background", defaults.background()),
                colorValue(props, "color.standard", defaults.standard()),
                colorValue(props, "color.winBlock", defaults.winBlock()),
                colorValue(props, "color.deadLine", defaults.deadLine()),
                colorValue(props, "color.deadLineBackground", defaults.deadLineBackground()),
                colorValue(props, "color.fontBackground", defaults.fontBackground()),
                colorValue(props, "color.pointBarBackground", defaults.pointBarBackground()),
                colorValue(props, "color.menuStandard", defaults.menuStandard()),
                players);
    }

    private static int intValue(Properties props, String key, int fallback) {
        var raw = props.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid int for '{}'='{}'; using {}", key, raw, fallback);
            return fallback;
        }
    }

    private static double doubleValue(Properties props, String key, double fallback) {
        var raw = props.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid double for '{}'='{}'; using {}", key, raw, fallback);
            return fallback;
        }
    }

    /** Parses an {@code r,g,b} or {@code r,g,b,a} CSV colour; falls back on any parse error. */
    private static RgbColor colorValue(Properties props, String key, RgbColor fallback) {
        var raw = props.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            var parts = raw.trim().split(",");
            int r = Integer.parseInt(parts[0].trim());
            int g = Integer.parseInt(parts[1].trim());
            int b = Integer.parseInt(parts[2].trim());
            int a = parts.length > 3 ? Integer.parseInt(parts[3].trim()) : 255;
            return new RgbColor(r, g, b, a);
        } catch (RuntimeException e) {
            log.warn("Invalid colour for '{}'='{}'; using {}", key, raw, fallback);
            return fallback;
        }
    }
}
