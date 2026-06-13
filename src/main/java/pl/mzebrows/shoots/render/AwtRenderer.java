// src/main/java/pl/mzebrows/shoots/render/AwtRenderer.java
package pl.mzebrows.shoots.render;

import pl.mzebrows.shoots.game.logic.GameCounter;
import pl.mzebrows.shoots.game.logic.GameFrame;
import pl.mzebrows.shoots.game.logic.GamePointer;
import pl.mzebrows.shoots.game.logic.GameScreen;
import pl.mzebrows.shoots.game.logic.RoundEnum;

/**
 * AWT/{@code BufferStrategy} renderer. Owns active rendering: it disables passive repaints on every
 * canvas and drives the three game panels — main play screen, top round-timer counter, and the side
 * score/round pointer — through their {@code drawUpdate} each frame.
 */
public final class AwtRenderer implements Renderer {

    private final GameScreen screen;
    private final GameCounter counter;
    private final GamePointer pointer;
    private final ImageCache imageCache;

    public AwtRenderer(GameFrame frame, ImageCache imageCache) {
        this.screen = frame.getGameScreen();
        this.counter = frame.getGameCounter();
        this.pointer = frame.getGamePointer();
        this.imageCache = imageCache;
        enableActiveRendering();
    }

    private void enableActiveRendering() {
        screen.setIgnoreRepaint(true);
        counter.setIgnoreRepaint(true);
        pointer.setIgnoreRepaint(true);
    }

    @Override
    public void render(RoundEnum roundState, double alpha) {
        // alpha is reserved for entity-level interpolation once entities migrate (clusters 4-6);
        // the legacy panel renderers below still draw from current state.
        counter.drawUpdate(roundState);
        pointer.drawUpdate(roundState);
        screen.drawUpdate(roundState);
    }

    @Override
    public void dispose() {
        imageCache.clear();
    }
}
