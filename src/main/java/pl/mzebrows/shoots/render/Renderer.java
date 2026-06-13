// src/main/java/pl/mzebrows/shoots/render/Renderer.java
package pl.mzebrows.shoots.render;

import pl.mzebrows.shoots.game.logic.RoundEnum;

/**
 * Render boundary decoupling the game loop from AWT: the loop hands over the current round state and
 * an interpolation factor, and the implementation is responsible for drawing a single frame.
 */
public interface Renderer {

    /**
     * Draws one frame.
     *
     * @param roundState current round phase to render
     * @param alpha      interpolation factor in {@code [0,1)} between the previous and current state
     */
    void render(RoundEnum roundState, double alpha);

    /** Releases native resources (buffers, cached images). */
    void dispose();
}
