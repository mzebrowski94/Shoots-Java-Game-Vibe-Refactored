// src/main/java/pl/mzebrows/shoots/render/object/CapturePointRenderer.java
package pl.mzebrows.shoots.render.object;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import pl.mzebrows.shoots.score.CapturePoint;
import pl.mzebrows.shoots.world.PlayWorld;

/** Draws each capture point: neutral as a crossed marker, captured tinted in the owner's colour by level. */
public final class CapturePointRenderer implements MapObjectRenderer {

    /** Quadrant arcs drawn around a captured point (one lit per capture level). */
    private static final int CAPTURED_ARCS = 4;

    @Override
    public void render(Graphics2D g2d, PlayWorld world, RenderFrame frame) {
        int unit = world.unit();
        var dashed = new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                5.0f, new float[]{6f}, frame.dashPhase());
        g2d.setStroke(dashed);
        for (CapturePoint point : world.scoring().points()) {
            int px = point.getTileX() * unit;
            int py = point.getTileY() * unit;
            if (point.isCaptured() && point.getOwnerId() >= 0) {
                Color owner = world.playerColor(point.getOwnerId());
                int level = Math.max(1, point.getLevel());
                int arcCount = Math.min(level, CAPTURED_ARCS);
                for (int k = 0; k < CAPTURED_ARCS; k++) {
                    g2d.setColor(k < arcCount ? owner : owner.darker().darker());
                    g2d.fillArc(px + 4, py + 4, unit - 8, unit - 8, 55 + k * 90, 70);
                }
                g2d.setColor(Color.gray);
                g2d.fillOval(px + unit / 2 - 7, py + unit / 2 - 7, 14, 14);
                g2d.setColor(owner.darker().darker());
                g2d.drawOval(px + 2, py + 2, unit - 4, unit - 4);
            } else {
                g2d.setColor(Color.black);
                g2d.drawRoundRect(px + 2, py + 2, unit - 4, unit - 4, 10, 10);
                g2d.drawLine(px + 4, py + 4, px + unit - 4, py + unit - 4);
                g2d.drawLine(px + 4, py + unit - 4, px + unit - 4, py + 4);
            }
        }
    }
}
