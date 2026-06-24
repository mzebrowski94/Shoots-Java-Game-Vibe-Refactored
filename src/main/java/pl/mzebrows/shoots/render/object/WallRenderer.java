// src/main/java/pl/mzebrows/shoots/render/object/WallRenderer.java
package pl.mzebrows.shoots.render.object;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import pl.mzebrows.shoots.spatial.TileType;
import pl.mzebrows.shoots.world.PlayWorld;

/** Draws wall tiles as solid squares; tile (i,j) occupies pixel (i*unit, j*unit). */
public final class WallRenderer implements MapObjectRenderer {

    private final Color wallColor;

    public WallRenderer(Color wallColor) {
        this.wallColor = wallColor;
    }

    @Override
    public void render(Graphics2D g2d, PlayWorld world, RenderFrame frame) {
        int unit = world.unit();
        TileType[][] tiles = world.tiles();
        g2d.setColor(wallColor);
        g2d.setStroke(new BasicStroke());
        for (int i = 0; i < tiles.length; i++) {
            for (int j = 0; j < tiles[i].length; j++) {
                if (tiles[i][j] == TileType.WALL) {
                    g2d.fillRect(i * unit, j * unit, unit, unit);
                }
            }
        }
    }
}
