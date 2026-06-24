// src/main/java/pl/mzebrows/shoots/render/object/LaserRenderer.java
package pl.mzebrows.shoots.render.object;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import pl.mzebrows.shoots.world.PlayWorld;

/**
 * Draws each player's predicted aiming laser as a dashed line in the player colour, fading each
 * successive bounce segment by {@code laser.bounceAlphaFalloff}. Owns reusable scratch buffers (sized
 * from {@code laser.maxBounces}) so the per-frame trace is allocation-free.
 */
public final class LaserRenderer implements MapObjectRenderer {

    private int[] laserX;
    private int[] laserY;

    @Override
    public void render(Graphics2D g2d, PlayWorld world, RenderFrame frame) {
        int laserPoints = 1 + world.config().disc().laserMaxBounces();
        if (laserX == null || laserX.length != laserPoints) {
            laserX = new int[laserPoints];
            laserY = new int[laserPoints];
        }
        double falloff = world.config().disc().laserBounceAlphaFalloff();
        var dashed = new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                5.0f, new float[]{6f}, frame.dashPhase());
        g2d.setStroke(dashed);
        for (int p = 0; p < world.playerCount(); p++) {
            int n = world.predictLaser(p, laserX, laserY);
            if (n < 2) {
                continue;
            }
            Color base = world.playerColor(p);
            double segAlpha = 255.0;
            for (int i = 0; i + 1 < n; i++) {
                int a = Math.max(0, Math.min(255, (int) Math.round(segAlpha)));
                g2d.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), a));
                g2d.drawLine(laserX[i], laserY[i], laserX[i + 1], laserY[i + 1]);
                segAlpha *= falloff;
            }
        }
    }
}
