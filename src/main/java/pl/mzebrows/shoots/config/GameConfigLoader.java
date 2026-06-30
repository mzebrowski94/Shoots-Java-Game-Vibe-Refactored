// src/main/java/pl/mzebrows/shoots/config/GameConfigLoader.java
package pl.mzebrows.shoots.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads {@link GameConfig} (+ {@link GraphicsConfig}) from the bundled {@code game.properties} and
 * {@code graphic.properties}. The property files are the SINGLE SOURCE OF TRUTH: there are no code-side
 * defaults. Every key a config needs is required -- a missing or unparseable key throws a
 * {@link ConfigException} (which the application logs before exiting). The only fixed-in-code value is the
 * map geometry ({@link #GRID_UNIT} / {@link #TABLE_SIZE}), kept as a deliberate constant so a future map
 * size is a one-line change here rather than a runtime property.
 */
public final class GameConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(GameConfigLoader.class);
    private static final String GAME_RESOURCE = "game.properties";
    private static final String GRAPHICS_RESOURCE = "graphic.properties";

    /** Fixed logical tile size in pixels (the rendering/scale unit); the map is authored against this. */
    public static final int GRID_UNIT = 36;
    /** Fixed "normal" map size in tiles (square). Change here (and re-tune the map) for a different size. */
    public static final int TABLE_SIZE = 25;

    private GameConfigLoader() {
    }

    /** Loads gameplay config from the bundled resources ({@code game.properties} + {@code graphic.properties}). */
    public static GameConfig load() {
        return fromProperties(bundledProperties());
    }

    /** Loads rendering config from the bundled resources. */
    public static GraphicsConfig loadGraphics() {
        return graphicsFromProperties(bundledProperties());
    }

    /** Reads both bundled resources into one Properties (graphic over game). Exposed for tests. */
    public static Properties bundledProperties() {
        var props = new Properties();
        readInto(props, GAME_RESOURCE);
        readInto(props, GRAPHICS_RESOURCE);
        return props;
    }

    private static void readInto(Properties props, String resourceName) {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new ConfigException("Required config resource not found on classpath: " + resourceName);
            }
            props.load(in);
            log.debug("Loaded configuration from '{}'", resourceName);
        } catch (IOException e) {
            throw new ConfigException("Failed to read config resource '" + resourceName + "': " + e.getMessage());
        }
    }

    /** Builds a config from already-parsed properties; every required key must be present and parseable. */
    public static GameConfig fromProperties(Properties props) {
        var grid = new GridConfig(GRID_UNIT, TABLE_SIZE);

        var disc = new DiscConfig(
                requireInt(props, "disc.bigRadius"),
                requireInt(props, "disc.smallRadius"),
                requireDouble(props, "disc.speed"),
                requireInt(props, "disc.maxBounces"),
                requireInt(props, "disc.maxPerPlayer"),
                requireInt(props, "disc.maxPerShot"),
                requireInt(props, "laser.maxBounces"),
                requireDouble(props, "disc.bounceSpeedGain"),
                requireDouble(props, "disc.maxSpeedFactor"),
                requireDouble(props, "laser.bounceAlphaFalloff"));

        var collision = new CollisionConfig(requireInt(props, "collision.ballCollisionSize"));

        var round = new RoundConfig(
                requireInt(props, "round.timeSeconds"),
                requireInt(props, "round.limit"),
                requireInt(props, "round.endDelay"),
                requireInt(props, "round.animationTime"));

        var palette = paletteFromProperties(props);

        var aiToggles = new AiSkillToggles(
                requireBool(props, "ai.skill.accuracy"),
                requireBool(props, "ai.skill.cursorSpeed"),
                requireBool(props, "ai.skill.retake"),
                requireBool(props, "ai.skill.defend"),
                requireBool(props, "ai.skill.bouncePath"),
                requireBool(props, "ai.skill.powerShot"),
                requireBool(props, "ai.skill.baseAttack"));
        var ai = new AiConfig(
                requireInt(props, "ai.scanAngles"),
                requireInt(props, "ai.scanBudgetPerFrame"),
                requireBool(props, "ai.skillsEnabled"),
                requireBool(props, "ai.powerShotEnabled"),
                requireInt(props, "ai.powerShotMinBounces"),
                requireBool(props, "ai.baseAttackEnabled"),
                aiToggles);

        var power = new PowerShotConfig(
                requireBool(props, "power.enabled"),
                requireDouble(props, "power.chargeSeconds"),
                requireDouble(props, "power.speedFactor"),
                requireDouble(props, "power.maxBouncesFactor"),
                requireInt(props, "power.captureStrength"));

        var disruption = new DisruptionConfig(
                requireBool(props, "disruption.enabled"),
                requireDouble(props, "disruption.durationSeconds"),
                requireDouble(props, "disruption.graceSeconds"));

        var menu = new MenuConfig(
                requireInt(props, "menu.maxPlayers"),
                requireInt(props, "menu.initialRoundLimit"),
                requireInt(props, "menu.maxRoundLimit"),
                requireInt(props, "menu.roundLimitStep"),
                requireInt(props, "menu.initialRoundTime"),
                requireInt(props, "menu.maxRoundTime"),
                requireInt(props, "menu.roundTimeStep"),
                requireInt(props, "menu.initialAiPlayers"),
                requireFloat(props, "menu.fontSize"),
                requireInt(props, "menu.rowSpacing"),
                requireInt(props, "menu.panelPadX"),
                requireInt(props, "menu.panelMargin"));

        var window = new WindowConfig(
                requireInt(props, "window.windowTiles"),
                requireInt(props, "window.counterHeightTiles"),
                requireInt(props, "window.pointerWidthTiles"));

        return new GameConfig(
                requireInt(props, "round.initialPlayerNumber"),
                resolveSeed(props),
                grid, disc, collision, round, palette, ai, menu, window, power, disruption);
    }

    /** Builds rendering config (menu chrome + map-object styling) from properties; all keys required. */
    public static GraphicsConfig graphicsFromProperties(Properties props) {
        var menu = new MenuTheme(
                requireColor(props, "menu.theme.label"),
                requireColor(props, "menu.theme.sublabel"),
                requireColor(props, "menu.theme.value"),
                requireColor(props, "menu.theme.separator"),
                requireColor(props, "menu.theme.panelFill"),
                requireColor(props, "menu.theme.panelBorder"),
                requireColor(props, "menu.theme.panelGlow"),
                requireColor(props, "menu.theme.highlightFill"),
                requireColor(props, "menu.theme.highlightBorder"),
                requireColor(props, "menu.theme.shadow"),
                requireInt(props, "menu.theme.panelArc"));
        var objects = new ObjectStyle(
                requireInt(props, "object.base.ringBig"),
                requireInt(props, "object.base.ringSmall"),
                requireInt(props, "object.disc.coreRadius"),
                requireDouble(props, "object.cursor.sizeFactor"),
                requireDouble(props, "object.cursor.standoffFactor"),
                requireDouble(props, "object.charge.glowThreshold"));
        return new GraphicsConfig(menu, objects);
    }

    private static ColorPalette paletteFromProperties(Properties props) {
        List<RgbColor> players = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            players.add(requireColor(props, "color.player" + i));
        }
        return new ColorPalette(
                requireColor(props, "color.background"),
                requireColor(props, "color.standard"),
                requireColor(props, "color.winBlock"),
                requireColor(props, "color.deadLine"),
                requireColor(props, "color.deadLineBackground"),
                requireColor(props, "color.fontBackground"),
                requireColor(props, "color.pointBarBackground"),
                requireColor(props, "color.menuStandard"),
                players);
    }

    /**
     * Resolves the master run seed: a parseable {@code game.seed} is used verbatim (reproducible run);
     * a blank or absent key resolves to a fresh time-based seed (a different run each launch). This is the
     * one numeric key whose ABSENCE is meaningful rather than fatal.
     */
    private static long resolveSeed(Properties props) {
        var raw = props.getProperty("game.seed");
        if (raw == null || raw.isBlank()) {
            return System.nanoTime();
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException _) {
            throw new ConfigException("Invalid long for 'game.seed': '" + raw + "'");
        }
    }

    // --- required-key accessors: a missing/blank/unparseable key is a fatal ConfigException ---------

    private static String requireRaw(Properties props, String key) {
        var raw = props.getProperty(key);
        if (raw == null || raw.isBlank()) {
            throw new ConfigException("Missing required property: '" + key + "'");
        }
        return raw.trim();
    }

    private static int requireInt(Properties props, String key) {
        String raw = requireRaw(props, key);
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException _) {
            throw new ConfigException("Invalid int for '" + key + "': '" + raw + "'");
        }
    }

    private static double requireDouble(Properties props, String key) {
        String raw = requireRaw(props, key);
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException _) {
            throw new ConfigException("Invalid double for '" + key + "': '" + raw + "'");
        }
    }

    private static float requireFloat(Properties props, String key) {
        String raw = requireRaw(props, key);
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException _) {
            throw new ConfigException("Invalid float for '" + key + "': '" + raw + "'");
        }
    }

    private static boolean requireBool(Properties props, String key) {
        String raw = requireRaw(props, key).toLowerCase();
        if (raw.equals("true")) {
            return true;
        }
        if (raw.equals("false")) {
            return false;
        }
        throw new ConfigException("Invalid boolean for '" + key + "': '" + raw + "'");
    }

    /** Parses an {@code r,g,b} or {@code r,g,b,a} CSV colour (channels 0-255); a parse error is fatal. */
    private static RgbColor requireColor(Properties props, String key) {
        String raw = requireRaw(props, key);
        try {
            var parts = raw.split(",");
            int r = Integer.parseInt(parts[0].trim());
            int g = Integer.parseInt(parts[1].trim());
            int b = Integer.parseInt(parts[2].trim());
            int a = parts.length > 3 ? Integer.parseInt(parts[3].trim()) : 255;
            return new RgbColor(r, g, b, a);
        } catch (RuntimeException _) {
            throw new ConfigException("Invalid colour for '" + key + "': '" + raw + "'");
        }
    }
}
