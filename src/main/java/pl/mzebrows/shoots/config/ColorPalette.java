// src/main/java/pl/mzebrows/shoots/config/ColorPalette.java
package pl.mzebrows.shoots.config;

import java.util.List;

/** Immutable colour palette for world, UI, and per-player disc colours. */
public record ColorPalette(
        RgbColor background,
        RgbColor standard,
        RgbColor winBlock,
        RgbColor deadLine,
        RgbColor deadLineBackground,
        RgbColor fontBackground,
        RgbColor pointBarBackground,
        RgbColor menuStandard,
        List<RgbColor> players) {

    public ColorPalette {
        players = List.copyOf(players);
        if (players.isEmpty()) {
            throw new IllegalArgumentException("palette must define at least one player colour");
        }
    }

    /** Colour for the given 1-based player number, clamped to the available palette. */
    public RgbColor playerColor(int playerNumber) {
        int index = Math.floorMod(playerNumber - 1, players.size());
        return players.get(index);
    }
}
