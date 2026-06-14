// src/main/java/pl/mzebrows/shoots/score/CapturePoint.java
package pl.mzebrows.shoots.score;

import lombok.Getter;

/**
 * Mutable capture-point state at a fixed tile, decoupled from AWT (the legacy
 * {@code PointField.GamePoint}). Holds the owning player, the current capture level (1..{@value #MAX_LEVEL}),
 * and whether it is captured. Capture rules live in {@link #tryCapture(int, int)} so they are testable
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
     * Applies a disc hit by {@code playerId} that arrived after {@code bounces} reflections, mirroring
     * the legacy rule: a hit with a strictly higher bounce count captures (raising the level, capped at
     * {@link #MAX_LEVEL}); an equal-count hit by a different player steals it; otherwise nothing changes.
     *
     * @return {@code true} if ownership or level changed
     */
    public boolean tryCapture(int playerId, int bounces) {
        if (level < bounces) {
            captured = true;
            ownerId = playerId;
            level = Math.min(bounces, MAX_LEVEL);
            return true;
        }
        if (level == bounces && ownerId != playerId) {
            captured = true;
            ownerId = playerId;
            return true;
        }
        return false;
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
