// src/main/java/pl/mzebrows/shoots/system/DiscSystem.java
package pl.mzebrows.shoots.system;

import java.util.List;
import pl.mzebrows.shoots.entity.Entity;
import pl.mzebrows.shoots.spatial.CollisionResult;
import pl.mzebrows.shoots.spatial.SpatialCollider;

/**
 * Per-step disc lifecycle over pooled {@link Entity} instances: integrate movement, resolve wall and
 * capture-point collisions via the {@link SpatialCollider}, and retire discs that exhaust their
 * bounce budget. Replaces the legacy {@code Disc.moveDisc()/checkCollision()/checkColisionsNumber()}
 * trio with allocation-free, AWT-decoupled logic driven by config-derived state on the entity.
 *
 * <p>Constructor-injected with the collider, the {@link MovementSystem} (movement strategies), and
 * the {@link CombatSystem} (retirement back to the pool). A caller-supplied {@link DiscEventSink}
 * receives capture-point hits and retirements so scoring/audio can react without this system knowing
 * about rendering.
 */
public final class DiscSystem {

    /** Callback for disc events so scoring/effects stay decoupled from disc physics. */
    public interface DiscEventSink {
        /** A disc struck a capture-point tile at the given tile indices. */
        void onCapturePointHit(Entity disc, int tileX, int tileY);

        /** A disc exhausted its bounce budget and was returned to the pool. */
        void onDiscRetired(Entity disc);
    }

    /** No-op sink for headless tests or when no listener is required. */
    public static final DiscEventSink NO_OP = new DiscEventSink() {
        @Override public void onCapturePointHit(Entity disc, int tileX, int tileY) { }
        @Override public void onDiscRetired(Entity disc) { }
    };

    private final SpatialCollider collider;
    private final CombatSystem combatSystem;

    public DiscSystem(SpatialCollider collider, CombatSystem combatSystem) {
        this.collider = collider;
        this.combatSystem = combatSystem;
    }

    /**
     * Advances and collides every active disc in {@code discs}, retiring spent ones. Iterates by
     * index and reuses the entity instances; the only allocation is the immutable hit result.
     */
    public void update(List<Entity> discs, DiscEventSink sink) {
        for (int i = 0, n = discs.size(); i < n; i++) {
            Entity disc = discs.get(i);
            if (!disc.isActive()) {
                continue;
            }
            disc.snapshot();
            disc.getMovementStrategy().move(disc);

            CollisionResult result = collider.resolve(disc);
            if (result.collided() && result.tile().isCapturePoint()) {
                sink.onCapturePointHit(disc, result.tileX(), result.tileY());
            }

            if (combatSystem.isSpent(disc)) {
                combatSystem.retire(disc);
                sink.onDiscRetired(disc);
            }
        }
    }
}
