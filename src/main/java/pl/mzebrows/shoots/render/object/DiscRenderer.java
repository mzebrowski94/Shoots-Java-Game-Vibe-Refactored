// src/main/java/pl/mzebrows/shoots/render/object/DiscRenderer.java
package pl.mzebrows.shoots.render.object;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import pl.mzebrows.shoots.config.ObjectStyle;
import pl.mzebrows.shoots.entity.Entity;
import pl.mzebrows.shoots.world.PlayWorld;

/**
 * Draws each active disc as a ring in its owner's colour, interpolated by the loop alpha; a powered disc
 * additionally gets a halo glow and a bright core so it reads as charged.
 */
public final class DiscRenderer implements MapObjectRenderer {

    private final int coreRadius;

    public DiscRenderer(ObjectStyle style) {
        this.coreRadius = style.discCoreRadius();
    }

    @Override
    public void render(Graphics2D g2d, PlayWorld world, RenderFrame frame) {
        int big = world.config().disc().bigRadius();
        int small = world.config().disc().smallRadius();
        int halfBig = big / 2;
        int halfSmall = small / 2;
        double alpha = frame.alpha();
        var discs = world.discs();
        for (int i = 0, n = discs.size(); i < n; i++) {
            Entity disc = discs.get(i);
            if (!disc.isActive()) {
                continue;
            }
            double dx = disc.interpolatedX(alpha);
            double dy = disc.interpolatedY(alpha);
            Color ownerColor = world.playerColor(disc.getOwnerId());
            if (disc.isPowered()) {
                drawPowerGlow(g2d, dx, dy, big, ownerColor);
            }
            var outer = new Area(new Ellipse2D.Double(dx - halfBig, dy - halfBig, big, big));
            outer.subtract(new Area(new Ellipse2D.Double(dx - halfSmall, dy - halfSmall, small, small)));
            g2d.setColor(disc.isPowered() ? ownerColor.brighter() : ownerColor);
            g2d.fill(outer);
            if (disc.isPowered()) {
                g2d.setColor(new Color(255, 255, 255, 210));
                g2d.fill(new Ellipse2D.Double(dx - coreRadius, dy - coreRadius, 2.0 * coreRadius, 2.0 * coreRadius));
            }
        }
    }

    /** Lighting effect for a power disc: a couple of translucent halo rings in the owner colour. */
    private void drawPowerGlow(Graphics2D g2d, double dx, double dy, int big, Color owner) {
        Stroke old = g2d.getStroke();
        Color bright = owner.brighter();
        for (int ring = 1; ring <= 2; ring++) {
            int pad = ring * 4;
            int alpha = 130 / ring;
            g2d.setStroke(new BasicStroke(2.0f));
            g2d.setColor(new Color(bright.getRed(), bright.getGreen(), bright.getBlue(), alpha));
            g2d.draw(new Ellipse2D.Double(dx - big / 2.0 - pad, dy - big / 2.0 - pad, big + 2.0 * pad, big + 2.0 * pad));
        }
        g2d.setStroke(old);
    }
}
