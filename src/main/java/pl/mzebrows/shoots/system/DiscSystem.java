// src/main/java/pl/mzebrows/shoots/system/DiscSystem.java
package pl.mzebrows.shoots.system;

import java.util.List;
import pl.mzebrows.shoots.entity.Entity;
import pl.mzebrows.shoots.spatial.GridPathTracer;

/**
 * Per-step disc lifecycle over pooled {@link Entity} instances: advance each disc along its exact
 * analytic reflecting path ({@link GridPathTracer}), report capture/wall events, and retire discs that
 * exhaust their bounce budget. Replaces the legacy step-sampled move/collide trio.
 *
 * <p>Because the tracer reflects at exact tile faces, the bounce <em>path</em> is identical for slow
 * and fast discs (and the laser), and each corner is a single reflection event -- so a disc can no
 * longer rack up phantom bounces in a corner band and vanish. On every wall bounce the disc's realised
 * speed is multiplied by its {@code speedGainPerBounce} (capped at {@code maxMoveSpeed}); acceleration
 * only changes how far the disc travels per frame, never the geometry of its path.
 *
 * <p>Constructor-injected with the tracer and the {@link CombatSystem} (retirement back to the pool).
 * A caller-supplied {@link DiscEventSink} receives capture-point hits and retirements so scoring/audio
 * stay decoupled from disc physics. Reuses one {@link GridPathTracer.Ray} and visitor: no allocation.
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

        /** A disc bounced off a solid wall/block tile at the given indices (for the hit-flash effect). */
        default void onWallHit(Entity disc, int tileX, int tileY) { }

        /** A disc exhausted its bounce budget and was returned to the pool. */
        void onDiscRetired(Entity disc);
    }

    /** No-op sink for headless tests or when no listener is required. */
    public static final DiscEventSink NO_OP = new DiscEventSink() {
        @Override public boolean onCapturePointHit(Entity disc, int tileX, int tileY) { return false; }
        @Override public void onDiscRetired(Entity disc) { }
    };

    private GridPathTracer tracer;
    private final CombatSystem combatSystem;
    private final GridPathTracer.Ray ray = new GridPathTracer.Ray();
    private final DiscVisitor visitor = new DiscVisitor();

    public DiscSystem(GridPathTracer tracer, CombatSystem combatSystem) {
        this.tracer = tracer;
        this.combatSystem = combatSystem;
    }

    /** Rebinds the tracer, e.g. when the world regenerates its map between rounds. */
    public void setTracer(GridPathTracer tracer) {
        this.tracer = tracer;
    }

    /**
     * Advances and collides every active disc in {@code discs}, retiring spent ones. Iterates from the
     * end so the retirement {@link DiscEventSink} can remove the spent disc from {@code discs} in place
     * without corrupting the loop.
     */
    public void update(List<Entity> discs, DiscEventSink sink) {
        for (int i = discs.size() - 1; i >= 0; i--) {
            Entity disc = discs.get(i);
            if (!disc.isActive()) {
                continue;
            }
            disc.snapshot();
            integrate(disc, sink);
        }
    }

    /** Advances one disc a whole frame along its reflecting path, then accelerates / retires as needed. */
    private void integrate(Entity disc, DiscEventSink sink) {
        double frameSpeed = disc.getMoveSpeed();
        double radians = Math.toRadians(-disc.getAngle());
        double baseVx = Math.sin(radians);
        double baseVy = Math.cos(radians);
        double dx = disc.getDirectionX() * baseVx;
        double dy = disc.getDirectionY() * baseVy;

        int budget = disc.getMaxBounces() - disc.getBounces();
        if (budget < 0) {
            budget = 0;
        }

        ray.set(disc.getX(), disc.getY(), dx, dy);
        visitor.begin(disc, sink);
        tracer.walk(ray, frameSpeed, budget, visitor);

        disc.setX(ray.x);
        disc.setY(ray.y);
        // Map the (sign-flipped) travel direction back onto the entity's direction multipliers.
        if (Math.abs(baseVx) > 1e-12) {
            disc.setDirectionX(sameSign(ray.dirX, baseVx) ? 1 : -1);
        }
        if (Math.abs(baseVy) > 1e-12) {
            disc.setDirectionY(sameSign(ray.dirY, baseVy) ? 1 : -1);
        }

        int reflections = ray.reflections;
        if (reflections > 0) {
            disc.setBounces(disc.getBounces() + reflections);
            applyAcceleration(disc, frameSpeed, reflections);
        }

        if (visitor.consumed || combatSystem.isSpent(disc)) {
            combatSystem.retire(disc);
            sink.onDiscRetired(disc);
        }
    }

    /** Realised speed for next frame after {@code reflections} bounces: speed * gain^n, capped. */
    private static void applyAcceleration(Entity disc, double frameSpeed, int reflections) {
        double gain = disc.getSpeedGainPerBounce();
        if (gain <= 1.0) {
            return;
        }
        double cap = disc.getMaxMoveSpeed();
        double speed = frameSpeed;
        for (int r = 0; r < reflections; r++) {
            speed *= gain;
            if (cap > 0.0 && speed > cap) {
                speed = cap;
                break;
            }
        }
        disc.setMoveSpeed(speed);
    }

    private static boolean sameSign(double a, double b) {
        return (a >= 0) == (b >= 0);
    }

    /** Bridges tracer events to the {@link DiscEventSink}, tracking whether a capture consumed the disc. */
    private final class DiscVisitor implements GridPathTracer.PathVisitor {
        private Entity disc;
        private DiscEventSink sink;
        private boolean consumed;

        void begin(Entity disc, DiscEventSink sink) {
            this.disc = disc;
            this.sink = sink;
            this.consumed = false;
        }

        @Override
        public boolean onReflect(double x, double y, int tileX, int tileY) {
            sink.onWallHit(disc, tileX, tileY);
            return false; // bounce budget is enforced by the tracer
        }

        @Override
        public boolean onCapturePoint(double x, double y, int tileX, int tileY) {
            boolean hit = sink.onCapturePointHit(disc, tileX, tileY);
            if (hit) {
                consumed = true;
            }
            return hit; // a consumed hit stops the walk; an ineffective one passes through
        }
    }
}
