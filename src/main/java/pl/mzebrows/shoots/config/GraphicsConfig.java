// src/main/java/pl/mzebrows/shoots/config/GraphicsConfig.java
package pl.mzebrows.shoots.config;

/**
 * Root immutable rendering configuration, loaded from {@code graphic.properties}. Kept separate from the
 * gameplay {@link GameConfig} so the logic/graphics division is explicit: this holds only how things are
 * drawn (menu chrome + per-object visual styling), never game rules.
 */
public record GraphicsConfig(MenuTheme menu, ObjectStyle objects) {
}
