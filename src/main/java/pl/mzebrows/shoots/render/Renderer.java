// src/main/java/pl/mzebrows/shoots/render/Renderer.java
package pl.mzebrows.shoots.render;

import pl.mzebrows.shoots.game.logic.RoundEnum;
import pl.mzebrows.shoots.world.PlayWorld;

/**
 * Render boundary decoupling the game loop from AWT: the loop hands over the current round state, an
 * interpolation factor, and the live {@link PlayWorld} (the new model), and the implementation draws
 * a single frame from that state.
 */
public interface Renderer {

    /**
     * Draws one frame.
     *
     * @param roundState current round phase to render
     * @param alpha      interpolation factor in {@code [0,1)} between the previous and current state
     * @param world      live simulation to render, or {@code null} when not in a playing state
     */
    void render(RoundEnum roundState, double alpha, PlayWorld world);

    /** Releases native resources (buffers, cached images). */
    void dispose();
}
