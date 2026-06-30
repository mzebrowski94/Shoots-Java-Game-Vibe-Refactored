// src/main/java/pl/mzebrows/shoots/render/object/BlockHitRenderer.java
package pl.mzebrows.shoots.render.object;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import pl.mzebrows.shoots.world.BlockHitEffect;
import pl.mzebrows.shoots.world.PlayWorld;

/**
 * Draws the transient block-hit flash over each struck wall tile: a short grey ramp-up, then the disc
 * owner's colour fading out (mirrors the legacy {@code LightEffect}).
 */
public final class BlockHitRenderer implements MapObjectRenderer {

    @Override
    public void render(Graphics2D g2d, PlayWorld world, RenderFrame frame) {
        int unit = world.unit();
        g2d.setStroke(new BasicStroke());
        var effects = world.blockHits();
        for (int i = 0, n = effects.size(); i < n; i++) {
            BlockHitEffect e = effects.get(i);
            int px = e.tileX() * unit;
            int py = e.tileY() * unit;
            if (e.phase() == BlockHitEffect.Phase.GREY) {
                int g = e.greyLevel();
                g2d.setColor(new Color(g, g, g));
            } else {
                Color owner = world.playerColor(e.ownerId());
                int a = Math.clamp(e.colorAlpha(), 0, 255);
                g2d.setColor(new Color(owner.getRed(), owner.getGreen(), owner.getBlue(), a));
            }
            g2d.fillRect(px, py, unit, unit);
        }
    }
}
