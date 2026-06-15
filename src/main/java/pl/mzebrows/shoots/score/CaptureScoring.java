// src/main/java/pl/mzebrows/shoots/score/CaptureScoring.java
package pl.mzebrows.shoots.score;

import java.util.HashMap;
import java.util.Map;
import java.util.Collection;

/**
 * AWT-free capture-point bookkeeping for a round: owns the {@link CapturePoint}s and resolves disc
 * hits in O(1) by tile index, replacing the legacy {@code PointList} O(N&sup2;) per-disc scan.
 *
 * <p>A point is keyed by its packed tile coordinate; {@link #resolveHit(int, int, int)} looks it
 * up directly instead of scanning every field. Per-player totals are derived from the points' current
 * capture state, matching the legacy "recompute on change" behaviour without touching rendering.
 */
public final class CaptureScoring {

    private final Map<Long, CapturePoint> pointsByTile = new HashMap<>();

    /** Registers (or replaces) a capture point at its tile; returns it for convenience. */
    public CapturePoint register(int tileX, int tileY) {
        var point = new CapturePoint(tileX, tileY);
        pointsByTile.put(key(tileX, tileY), point);
        return point;
    }

    /** All registered capture points (e.g. for the renderer to draw). */
    public Collection<CapturePoint> points() {
        return pointsByTile.values();
    }

    /** The capture point at a tile, or {@code null} if none is registered there. */
    public CapturePoint at(int tileX, int tileY) {
        return pointsByTile.get(key(tileX, tileY));
    }

    /**
     * Resolves a single disc hit at ({@code tileX},{@code tileY}) by {@code playerId}. No-ops (returns
     * {@code false}) when no capture point sits on that tile, or when the hit changes nothing (owner
     * hitting an already-maxed point) -- in which case the disc passes through.
     *
     * @return {@code true} if the hit captured, levelled-up, or stole the point
     */
    public boolean resolveHit(int tileX, int tileY, int playerId) {
        CapturePoint point = pointsByTile.get(key(tileX, tileY));
        return point != null && point.tryCapture(playerId);
    }

    /** Total points currently controlled by {@code playerId} across all capture points. */
    public int pointsFor(int playerId) {
        int total = 0;
        for (CapturePoint point : pointsByTile.values()) {
            if (point.isCaptured() && point.getOwnerId() == playerId) {
                total += point.awardedPoints();
            }
        }
        return total;
    }

    /** Resets every capture point to neutral for a new round. */
    public void resetAll() {
        for (CapturePoint point : pointsByTile.values()) {
            point.reset();
        }
    }

    /** Clears all registered points (e.g. when rebuilding the map). */
    public void clear() {
        pointsByTile.clear();
    }

    private static long key(int tileX, int tileY) {
        return (((long) tileX) << 32) ^ (tileY & 0xFFFFFFFFL);
    }
}
