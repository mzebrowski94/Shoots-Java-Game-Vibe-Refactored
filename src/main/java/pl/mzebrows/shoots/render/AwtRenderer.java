// src/main/java/pl/mzebrows/shoots/render/AwtRenderer.java
package pl.mzebrows.shoots.render;

import pl.mzebrows.shoots.ui.GameCounter;
import pl.mzebrows.shoots.ui.GameFrame;
import pl.mzebrows.shoots.ui.GamePointer;
import pl.mzebrows.shoots.ui.GameScreen;
import pl.mzebrows.shoots.ui.RoundEnum;
import pl.mzebrows.shoots.world.PlayWorld;

/**
 * AWT/{@code BufferStrategy} renderer. Owns active rendering: it disables passive repaints on every
 * canvas and drives the three game panels — main play screen, top round-timer counter, and the side
 * score/round pointer — through their {@code drawUpdate} each frame, feeding the play screen and side
 * panel the live {@link PlayWorld} so they render the new model rather than legacy state.
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
    public void render(RoundEnum roundState, double alpha, PlayWorld world) {
        screen.setWorld(world, alpha);
        pointer.setWorld(world);
        counter.drawUpdate(roundState);
        pointer.drawUpdate(roundState);
        screen.drawUpdate(roundState);
    }

    @Override
    public void dispose() {
        imageCache.clear();
    }
}
