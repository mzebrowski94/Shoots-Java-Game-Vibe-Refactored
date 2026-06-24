// src/main/java/pl/mzebrows/shoots/render/object/BaseRenderer.java
package pl.mzebrows.shoots.render.object;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import pl.mzebrows.shoots.config.ObjectStyle;
import pl.mzebrows.shoots.world.PlayWorld;

/**
 * Draws each player base as two counter-rotating dashed rings in the player colour, plus the power-shot
 * charge ring that fills as the shoot key is held.
 */
public final class BaseRenderer implements MapObjectRenderer {

    /** Extra radius (px) of the charge ring outside the base's outer ring. */
    private static final int CHARGE_RING_GAP = 6;

    private final int ringBig;
    private final int ringSmall;
    private final double glowThreshold;

    public BaseRenderer(ObjectStyle style) {
        this.ringBig = style.baseRingBig();
        this.ringSmall = style.baseRingSmall();
        this.glowThreshold = style.chargeGlowThreshold();
    }

    @Override
    public void render(Graphics2D g2d, PlayWorld world, RenderFrame frame) {
        int unit = world.unit();
        var dashed = new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                10.0f, new float[]{6.9f}, 1.0f);
        g2d.setStroke(dashed);
        int rotation = frame.baseRotation();
        for (int p = 0; p < world.playerCount(); p++) {
            PlayWorld.BasePlacement base = world.baseOf(p);
            int cx = base.tileX() * unit + unit / 2;
            int cy = base.tileY() * unit + unit / 2;
            Color color = world.playerColor(p);
            g2d.setColor(color);
            drawRotatedRing(g2d, cx, cy, ringBig, rotation);
            drawRotatedRing(g2d, cx, cy, ringSmall, -rotation);
            drawChargeRing(g2d, cx, cy, ringBig + CHARGE_RING_GAP, world.chargeProgress(p), color);
        }
    }

    /** Charge indicator: a ring that fills clockwise from the top and glows as it nears full. */
    private void drawChargeRing(Graphics2D g2d, int cx, int cy, int radius, double progress, Color color) {
        if (progress <= 0.0) {
            return;
        }
        Stroke old = g2d.getStroke();
        int d = 2 * radius;
        g2d.setStroke(new BasicStroke(4.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 55));
        g2d.drawOval(cx - radius, cy - radius, d, d);
        int sweep = (int) Math.round(360.0 * Math.min(1.0, progress));
        int alpha = Math.min(255, (int) (110 + 145 * Math.min(1.0, progress)));
        g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
        g2d.drawArc(cx - radius, cy - radius, d, d, 90, -sweep);
        if (progress >= glowThreshold) {
            int gr = radius + 5;
            double denom = Math.max(1e-6, 1.0 - glowThreshold);
            int glowAlpha = (int) (90 * ((progress - glowThreshold) / denom));
            g2d.setStroke(new BasicStroke(3.0f));
            g2d.setColor(new Color(255, 255, 255, Math.max(0, Math.min(120, glowAlpha))));
            g2d.drawOval(cx - gr, cy - gr, 2 * gr, 2 * gr);
        }
        g2d.setStroke(old);
    }

    /** Draws one dashed ring of {@code radius} centred at (cx,cy), rotated by {@code rotationDegrees}. */
    private void drawRotatedRing(Graphics2D g2d, int cx, int cy, int radius, int rotationDegrees) {
        AffineTransform old = g2d.getTransform();
        g2d.rotate(Math.toRadians(rotationDegrees), cx, cy);
        g2d.draw(new Ellipse2D.Double(cx - radius, cy - radius, 2.0 * radius, 2.0 * radius));
        g2d.setTransform(old);
    }
}
