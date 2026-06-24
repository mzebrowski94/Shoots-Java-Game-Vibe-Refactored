// src/main/java/pl/mzebrows/shoots/render/object/MapObjectRenderer.java
package pl.mzebrows.shoots.render.object;

import java.awt.Graphics2D;
import pl.mzebrows.shoots.world.PlayWorld;

/**
 * Draws one kind of map object from the live {@link PlayWorld} for a single frame. Each renderer owns a
 * single object's look, so adding a new map object means writing one renderer and registering it in the
 * ordered list in {@code GameScreen} -- the rest of the rendering pipeline is untouched (open/closed).
 */
@FunctionalInterface
public interface MapObjectRenderer {

    /** Renders this object layer onto {@code g2d} using the live model and per-frame state. */
    void render(Graphics2D g2d, PlayWorld world, RenderFrame frame);
}
