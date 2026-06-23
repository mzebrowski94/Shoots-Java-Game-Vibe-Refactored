// pl/mzebrows/shoots/ui/GameDimensions.java
package pl.mzebrows.shoots.ui;

/**
 * @deprecated Superseded by {@code GridConfig} (tile unit / table size) and {@code WindowConfig}
 * (window-tile multipliers), both sourced from {@code game.properties}. This type no longer holds any
 * values and can be deleted; it remains only so a stale import elsewhere fails loudly rather than
 * silently reintroducing hard-coded dimensions. Safe to remove from the repo on Windows.
 */
@Deprecated(forRemoval = true)
public final class GameDimensions {
    private GameDimensions() {
    }
}
