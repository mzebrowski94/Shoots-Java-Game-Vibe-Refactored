// src/main/java/pl/mzebrows/shoots/world/BlockHitEffects.java
package pl.mzebrows.shoots.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns the transient wall-tile hit flashes: starts (or retriggers) a {@link BlockHitEffect} per struck
 * tile, advances them each step, and prunes finished ones. Pure state, no AWT; the renderer reads
 * {@link #list()}. Extracted from {@code PlayWorld} so the flash bookkeeping has one clear home.
 */
final class BlockHitEffects {

    private final List<BlockHitEffect> effects = new ArrayList<>();
    private final Map<Long, BlockHitEffect> byTile = new HashMap<>();

    /** Starts (or restarts in place) the hit flash on a wall tile in the disc owner's colour. */
    void spawn(int tileX, int tileY, int ownerId) {
        long key = key(tileX, tileY);
        BlockHitEffect existing = byTile.get(key);
        if (existing != null) {
            existing.restart(ownerId); // same tile hit again -> retrigger the flash, no allocation
            return;
        }
        var effect = new BlockHitEffect(tileX, tileY, ownerId);
        effects.add(effect);
        byTile.put(key, effect);
    }

    /** Advances every active flash one tick and prunes finished ones (index loop, no iterator). */
    void advance() {
        for (int i = effects.size() - 1; i >= 0; i--) {
            BlockHitEffect effect = effects.get(i);
            effect.advance();
            if (effect.isDone()) {
                byTile.remove(key(effect.tileX(), effect.tileY()));
                effects.remove(i);
            }
        }
    }

    /** Drops all active flashes (round reset). */
    void clear() {
        effects.clear();
        byTile.clear();
    }

    /** Active flashes for the renderer to draw over wall tiles. */
    List<BlockHitEffect> list() {
        return effects;
    }

    private static long key(int tileX, int tileY) {
        return (((long) tileX) << 32) ^ (tileY & 0xFFFFFFFFL);
    }
}
