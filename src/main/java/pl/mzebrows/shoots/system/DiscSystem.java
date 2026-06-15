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
        /**
         * A disc struck a capture-point tile at the given indices.
         * @return {@code true} if the hit changed the point (the disc is consumed and should retire);
         *         {@code false} if nothing changed (the disc passes through).
         */
        boolean onCapturePointHit(Entity disc, int tileX, int tileY);

        /** A disc exhausted its bounce budget and was returned to the pool. */
        void onDiscRetired(Entity disc);
    }

    /** No-op sink for headless tests or when no listener is required. */
    public static final DiscEventSink NO_OP = new DiscEventSink() {
        @Override public boolean onCapturePointHit(Entity disc, int tileX, int tileY) { return false; }
        @Override public void onDiscRetired(Entity disc) { }
    };

    private final SpatialCollider collider;
    private final CombatSystem combatSystem;

    public DiscSystem(SpatialCollider collider, CombatSystem combatSystem) {
        this.collider = collider;
        this.combatSystem = combatSystem;
    }

    /**
     * Advances and collides every active disc in {@code discs}, retiring spent ones. Reuses the
     * entity instances; the only allocation is the immutable hit result.
     *
     * <p>Iterates from the end so the retirement {@link DiscEventSink} can remove the spent disc from
     * {@code discs} in place without corrupting the loop: a removal at index {@code i} shifts only the
     * already-visited tail, leaving lower indices valid. The earlier forward loop that cached
     * {@code size()} threw {@link IndexOutOfBoundsException} once a block bounce retired a disc and the
     * sink shrank the list mid-iteration.
     */
    public void update(List<Entity> discs, DiscEventSink sink) {
        for (int i = discs.size() - 1; i >= 0; i--) {
            Entity disc = discs.get(i);
            if (!disc.isActive()) {
                continue;
            }
            disc.snapshot();
            disc.getMovementStrategy().move(disc);

            CollisionResult result = collider.resolve(disc);
            if (result.collided() && result.tile().isCapturePoint()) {
                // A hit that captures/levels-up/steals the point consumes the disc; an ineffective
                // hit (owner on an already-maxed point) returns false so the disc passes through.
                if (sink.onCapturePointHit(disc, result.tileX(), result.tileY())) {
                    combatSystem.retire(disc);
                    sink.onDiscRetired(disc);
                    continue;
                }
            }

            if (combatSystem.isSpent(disc)) {
                combatSystem.retire(disc);
                sink.onDiscRetired(disc);
            }
        }
    }
}
