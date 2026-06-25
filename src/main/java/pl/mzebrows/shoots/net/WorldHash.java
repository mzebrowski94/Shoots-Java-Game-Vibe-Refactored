// src/main/java/pl/mzebrows/shoots/net/WorldHash.java
package pl.mzebrows.shoots.net;

import pl.mzebrows.shoots.entity.Entity;
import pl.mzebrows.shoots.score.CapturePoint;
import pl.mzebrows.shoots.world.PlayWorld;

/**
 * A compact, order-stable 64-bit hash of the gameplay-authoritative slice of a {@link PlayWorld} (disc
 * positions/angles/bounces, per-player aim, capture points, scores). Peers running the same seed + same
 * inputs produce the same hash every frame; any divergence changes it. This is the lockstep desync
 * DETECTOR (see {@code OnlineMode.md}, F5) -- it is never part of the simulation, only a check.
 *
 * <p>Positions/angles are quantized before hashing so it tolerates sub-pixel float noise while still
 * catching any divergence that would actually affect gameplay.
 */
public final class WorldHash {

    private static final long FNV_OFFSET = 1125899906842597L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final double QUANTUM = 16.0; // 1/16-pixel resolution

    private WorldHash() {
    }

    /** The authoritative-state hash of {@code world}. */
    public static long of(PlayWorld world) {
        long h = FNV_OFFSET;
        h = mix(h, world.playerCount());
        h = mix(h, world.totalActiveDiscs());

        for (Entity disc : world.discs()) {
            h = mix(h, disc.getOwnerId());
            h = mix(h, quantize(disc.getX()));
            h = mix(h, quantize(disc.getY()));
            h = mix(h, quantize(disc.getAngle()));
            h = mix(h, disc.getBounces());
        }
        for (int p = 0; p < world.playerCount(); p++) {
            h = mix(h, quantize(world.aimOf(p).currentAngle()));
        }
        for (CapturePoint point : world.scoring().points()) {
            h = mix(h, point.getTileX());
            h = mix(h, point.getTileY());
            h = mix(h, point.getOwnerId());
            h = mix(h, point.getLevel());
        }
        return h;
    }

    private static long quantize(double value) {
        return Math.round(value * QUANTUM);
    }

    private static long mix(long h, long value) {
        h ^= value;
        h *= FNV_PRIME;
        return h;
    }
}
