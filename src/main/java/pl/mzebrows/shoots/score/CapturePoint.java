// src/main/java/pl/mzebrows/shoots/score/CapturePoint.java
package pl.mzebrows.shoots.score;

import lombok.Getter;

/**
 * Mutable capture-point state at a fixed tile, decoupled from AWT (the legacy
 * {@code PointField.GamePoint}). Holds the owning player, the current capture level (1..{@value #MAX_LEVEL}),
 * and whether it is captured. Capture rules live in {@link #tryCapture(int)} so they are testable
 * without a graphics context.
 */
@Getter
public final class CapturePoint {

    /** Maximum capture level a point can reach. */
    public static final int MAX_LEVEL = 4;

    /** Sentinel owner for a neutral (uncaptured) point. */
    public static final int NO_OWNER = -1;

    private final int tileX;
    private final int tileY;

    private boolean captured;
    private int level;
    private int ownerId = NO_OWNER;

    public CapturePoint(int tileX, int tileY) {
        this.tileX = tileX;
        this.tileY = tileY;
    }

    /**
     * Applies a single disc hit by {@code playerId}. Each hit advances the point by exactly ONE level so
     * a point needs {@link #MAX_LEVEL} hits to be fully controlled (per the game rules):
     * a neutral point is captured at level 1; the current owner raises the level by 1 up to
     * {@link #MAX_LEVEL}; a different player STEALS it, resetting it to level 1 under the new owner.
     * A hit by the owner on an already-maxed point changes nothing.
     *
     * @return {@code true} if ownership or level changed (the disc is then consumed by the hit)
     */
    public boolean tryCapture(int playerId) {
        if (!captured) {
            captured = true;
            ownerId = playerId;
            level = 1;
            return true;
        }
        if (ownerId != playerId) {
            // Steal: a hit from another player takes the point back to level 1 under the new owner.
            ownerId = playerId;
            level = 1;
            return true;
        }
        if (level < MAX_LEVEL) {
            level++;
            return true;
        }
        return false; // owner hitting an already-maxed point: no change, disc passes through
    }

    /** Points this capture point currently awards its owner (0 when neutral). */
    public int awardedPoints() {
        return captured ? level : 0;
    }

    /** Resets to a neutral, uncaptured point for round re-initialisation. */
    public void reset() {
        captured = false;
        level = 0;
        ownerId = NO_OWNER;
    }
}
