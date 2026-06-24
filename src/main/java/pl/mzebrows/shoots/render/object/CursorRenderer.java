// src/main/java/pl/mzebrows/shoots/render/object/CursorRenderer.java
package pl.mzebrows.shoots.render.object;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Stroke;
import pl.mzebrows.shoots.config.ObjectStyle;
import pl.mzebrows.shoots.world.PlayWorld;

/**
 * Draws each player's aim cursor as a filled arrowhead just outside the base ring, pointing along the
 * current shot angle (dir = (-sin a, cos a)) so it points exactly where a fired disc would travel.
 */
public final class CursorRenderer implements MapObjectRenderer {

    private final double sizeFactor;
    private final double standoffFactor;

    public CursorRenderer(ObjectStyle style) {
        this.sizeFactor = style.cursorSizeFactor();
        this.standoffFactor = style.cursorStandoffFactor();
    }

    @Override
    public void render(Graphics2D g2d, PlayWorld world, RenderFrame frame) {
        int unit = world.unit();
        int size = (int) (unit * sizeFactor);
        int standoff = (int) (standoffFactor * size);
        Stroke oldStroke = g2d.getStroke();
        g2d.setStroke(new BasicStroke(1.0f));
        for (int p = 0; p < world.playerCount(); p++) {
            PlayWorld.BasePlacement base = world.baseOf(p);
            int cx = base.tileX() * unit + unit / 2;
            int cy = base.tileY() * unit + unit / 2;

            double angle = Math.toRadians(-world.aimOf(p).currentAngle());
            double dirX = Math.sin(angle);
            double dirY = Math.cos(angle);
            double perpX = -dirY;
            double perpY = dirX;

            double baseX = cx + dirX * standoff;
            double baseY = cy + dirY * standoff;
            double tipX = baseX + dirX * size;
            double tipY = baseY + dirY * size;
            double leftX = baseX + perpX * size;
            double leftY = baseY + perpY * size;
            double rightX = baseX - perpX * size;
            double rightY = baseY - perpY * size;
            double notchX = baseX + dirX * (0.5 * size);
            double notchY = baseY + dirY * (0.5 * size);

            int[] xs = {(int) leftX, (int) tipX, (int) rightX, (int) notchX};
            int[] ys = {(int) leftY, (int) tipY, (int) rightY, (int) notchY};
            g2d.setColor(world.playerColor(p));
            g2d.fillPolygon(xs, ys, 4);
        }
        g2d.setStroke(oldStroke);
    }
}
