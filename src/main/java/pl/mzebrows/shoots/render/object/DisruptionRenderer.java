// src/main/java/pl/mzebrows/shoots/render/object/DisruptionRenderer.java
package pl.mzebrows.shoots.render.object;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import pl.mzebrows.shoots.world.PlayWorld;

/**
 * Overlay effects for the base-disruption mechanic, drawn ON TOP of the base without altering the base
 * graphics. While a base is disrupted it shows: a spinning shield ring (fast), a chunky pixel
 * "glitch/distort" scatter in the player's colour alternating with the deadline purple, and the
 * attacking disc shaking in the centre of the base (as if rattling around inside it). When the
 * disruption ends and the base is briefly immune (grace), the same shield ring keeps spinning but
 * slows right down.
 *
 * <p>Read-only AWT view over {@link PlayWorld} (no simulation). Every animation is driven by the
 * elapsed fraction of the disruption / grace window ({@code 1 - progress}), so the number of shield
 * revolutions and glitch steps stays fixed regardless of how long {@code disruption.durationSeconds}
 * and {@code disruption.graceSeconds} are configured to be -- the speed scales with the durations.
 */
public final class DisruptionRenderer implements MapObjectRenderer {

    /** Half-extent (px) of the glitch field around the base centre. */
    private static final int GLITCH_REACH = 26;
    /** Number of (chunky) glitch shards scattered over a disrupted base. */
    private static final int GLITCH_SHARDS = 8;
    /** Min/max size (px) of a glitch shard -- deliberately big and blocky to match the game style. */
    private static final int SHARD_MIN = 7;
    private static final int SHARD_MAX = 15;
    /** How many discrete glitch "steps" play across the whole disruption (higher = faster animation). */
    private static final int GLITCH_STEPS = 24;

    /** Base radius (px) of the shield ring, just outside the base rings. */
    private static final int SHIELD_RADIUS = 36;
    /** Shield revolutions across the whole window: many while disrupted (fast), few in grace (slow). */
    private static final double SHIELD_TURNS_DISRUPT = 8.0;
    private static final double SHIELD_TURNS_GRACE = 4.0;

    /** Disc shake: how far it rattles (px) and how many wobble cycles play over the disruption. */
    private static final int SHAKE_RANGE = 10;
    private static final double SHAKE_CYCLES = 9.0;

    @Override
    public void render(Graphics2D g2d, PlayWorld world, RenderFrame frame) {
        int unit = world.unit();
        Color purple = world.config().palette().deadLine().toAwt();
        int big = world.config().disc().bigRadius();
        int small = world.config().disc().smallRadius();
        for (int p = 0; p < world.playerCount(); p++) {
            PlayWorld.BasePlacement base = world.baseOf(p);
            int cx = base.tileX() * unit + unit / 2;
            int cy = base.tileY() * unit + unit / 2;
            if (world.isDisrupted(p)) {
                double elapsed = 1.0 - world.disruptionProgress(p); // 0 -> 1 across the disruption
                drawShield(g2d, cx, cy, world.playerColor(p), elapsed, SHIELD_TURNS_DISRUPT, 1.0);
                drawGlitch(g2d, cx, cy, elapsed, p, world.playerColor(p), purple);
                drawParkedDisc(g2d, cx, cy, elapsed, world, p, big, small);
            } else if (world.isImmune(p)) {
                double elapsed = 1.0 - world.graceProgress(p); // 0 -> 1 across the grace window
                drawShield(g2d, cx, cy, world.playerColor(p), elapsed, SHIELD_TURNS_GRACE, world.graceProgress(p));
            }
        }
    }

    /**
     * Spinning shield ring in the player's colour. The bright leading arc rotates by
     * {@code turns} full revolutions across the window (so disruption spins fast, grace slow), with a
     * gentle pulse on the radius. {@code strength} (1 while disrupted, fading in grace) sets the alpha.
     */
    private void drawShield(Graphics2D g2d, int cx, int cy, Color color, double elapsed,
                            double turns, double strength) {
        Stroke old = g2d.getStroke();
        int pulse = (int) Math.round(2.0 * Math.sin(elapsed * turns * 2.0 * Math.PI));
        int radius = SHIELD_RADIUS + pulse;
        int d = 2 * radius;
        int ringAlpha = (int) Math.round(50 + 90 * clamp01(strength));
        g2d.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), ringAlpha));
        g2d.drawOval(cx - radius, cy - radius, d, d);
        // Bright leading arc that sweeps around as the shield spins.
        int start = (int) Math.round(elapsed * turns * 360.0) % 360;
        int arcAlpha = (int) Math.round(120 + 110 * clamp01(strength));
        g2d.setColor(new Color(255, 255, 255, Math.min(255, arcAlpha)));
        g2d.drawArc(cx - radius, cy - radius, d, d, start, 80);
        g2d.setStroke(old);
    }

    /**
     * Chunky pixel-glitch scatter in the player's colour alternating with the deadline purple. Animates
     * in discrete steps (slow) so it reads as a corrupted base rather than fizzing noise. A single big
     * horizontal tear sweeps across it.
     */
    private void drawGlitch(Graphics2D g2d, int cx, int cy, double elapsed, int seed, Color player, Color purple) {
        // Discrete, slow animation step: changes a handful of times across the whole disruption.
        int step = (int) (elapsed * GLITCH_STEPS);
        for (int i = 0; i < GLITCH_SHARDS; i++) {
            int hsh = hash(seed, step, i);
            int dx = ((hsh & 0xFF) - 128) * GLITCH_REACH / 128;
            int dy = (((hsh >> 8) & 0xFF) - 128) * GLITCH_REACH / 128;
            int w = SHARD_MIN + ((hsh >> 16) & 0x7) % (SHARD_MAX - SHARD_MIN + 1);
            int h = SHARD_MIN + ((hsh >> 19) & 0x7) % (SHARD_MAX - SHARD_MIN + 1);
            Color base = (i % 2 == 0) ? player : purple;
            g2d.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 205));
            g2d.fillRect(cx + dx - w / 2, cy + dy - h / 2, w, h);
        }
        // One big tear line that slides down the base as the glitch steps along.
        int tearY = cy - GLITCH_REACH + (int) Math.round(elapsed * 2.0 * GLITCH_REACH) % (2 * GLITCH_REACH);
        g2d.setColor(new Color(purple.getRed(), purple.getGreen(), purple.getBlue(), 220));
        g2d.fillRect(cx - GLITCH_REACH, tearY, 2 * GLITCH_REACH, 4);
    }

    /**
     * The attacking disc parked inside the disrupted base: drawn as a ring (in the attacker's colour)
     * at the base centre, rattling with a slow, wide wobble -- as if bouncing around inside and making
     * a mess. Position is purely cosmetic here; the model keeps the disc snapped to the base centre.
     */
    private void drawParkedDisc(Graphics2D g2d, int cx, int cy, double elapsed, PlayWorld world,
                                int victimId, int big, int small) {
        int attacker = world.parkedDiscOwnerAt(victimId);
        if (attacker < 0) {
            return;
        }
        double angle = elapsed * SHAKE_CYCLES * 2.0 * Math.PI;
        // Two slightly different frequencies on X/Y so it jitters around rather than sliding on a line.
        int ox = (int) Math.round(Math.sin(angle) * SHAKE_RANGE);
        int oy = (int) Math.round(Math.cos(angle * 1.37) * SHAKE_RANGE);
        double dx = cx + ox;
        double dy = cy + oy;
        int halfBig = big / 2;
        int halfSmall = small / 2;
        var ring = new Area(new Ellipse2D.Double(dx - halfBig, dy - halfBig, big, big));
        ring.subtract(new Area(new Ellipse2D.Double(dx - halfSmall, dy - halfSmall, small, small)));
        g2d.setColor(world.playerColor(attacker));
        g2d.fill(ring);
    }

    private static double clamp01(double v) {
        if (v < 0.0) {
            return 0.0;
        }
        return Math.min(v, 1.0);
    }

    /** Cheap deterministic hash for animated glitch placement (no per-frame allocation/RNG state). */
    private static int hash(int seed, int step, int i) {
        int h = seed * 73856093 ^ step * 19349663 ^ i * 83492791;
        h ^= (h >>> 13);
        h *= 0x5bd1e995;
        h ^= (h >>> 15);
        return h;
    }
}
