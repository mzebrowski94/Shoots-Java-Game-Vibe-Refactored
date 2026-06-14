// pl/mzebrows/shoots/game/logic/GameLoop.java
package pl.mzebrows.shoots.game.logic;

import java.awt.GraphicsConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.mzebrows.shoots.loop.FixedTimestep;
import pl.mzebrows.shoots.render.AwtRenderer;
import pl.mzebrows.shoots.render.ImageCache;
import pl.mzebrows.shoots.render.Renderer;
import pl.mzebrows.shoots.state.GameOverState;
import pl.mzebrows.shoots.state.GameStateMachine;
import pl.mzebrows.shoots.state.PausedState;
import pl.mzebrows.shoots.state.PlayingState;
import pl.mzebrows.shoots.world.PlayWorld;

/**
 * Top-level game loop: fixed-timestep simulation with an accumulator, max-delta clamp, and
 * render-side interpolation, driving the {@link GameStateMachine} on a dedicated thread.
 */
public final class GameLoop implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(GameLoop.class);

    private static final int UPDATES_PER_SECOND = 120;
    private static final int MAX_CATCH_UP_STEPS = 5;
    private static final String ICON_RESOURCE = "images/game.png";

    private final GameSettings gameSettings = new GameSettings();
    private final FixedTimestep timestep = FixedTimestep.ofRate(UPDATES_PER_SECOND, MAX_CATCH_UP_STEPS);

    private GameFrame gameFrame;
    private GameStateMachine stateMachine;
    private Renderer renderer;
    private Thread loopThread;

    /** Wires the object graph but does not start the loop; call {@link #start()} to run it. */
    public GameLoop() {
        log.info("Initialising GameLoop");
        initializeGraphics();
        initializeLogic();
    }

    /** Starts the simulation on a dedicated game-loop thread (AWT input still fires on the EDT). */
    public void start() {
        loopThread = new Thread(this, "game-loop");
        loopThread.start();
    }

    @Override
    public void run() {
        log.info("Game loop started (rate {} ups)", UPDATES_PER_SECOND);
        long previous = System.nanoTime();

        while (stateMachine.isRunning()) {
            long now = System.nanoTime();
            timestep.accumulate(now - previous);
            previous = now;

            var input = gameSettings.getInputBridge();
            boolean stepped = false;
            while (timestep.consumeStep() && stateMachine.isRunning()) {
                input.poll();
                stateMachine.update(input);
                stepped = true;
            }

            if (stepped) {
                renderer.render(currentRoundEnum(), timestep.alpha(), currentWorld());
            } else {
                parkBriefly();
            }
        }

        renderer.dispose();
        gameFrame.dispose();
        log.info("Game loop finished");
    }

    private RoundEnum currentRoundEnum() {
        return switch (stateMachine.current()) {
            case PlayingState ps -> ps.getRenderRoundEnum();
            default -> RoundEnum.ROUND_PAUSED;
        };
    }

    /** The live simulation when playing, else {@code null} (paused/menu/game-over). */
    private PlayWorld currentWorld() {
        return stateMachine.current() instanceof PlayingState ps ? ps.getWorld() : null;
    }

    private void parkBriefly() {
        try {
            Thread.sleep(1L);
        } catch (InterruptedException e) {
            log.error("Game loop interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    // -------------------------------------------------------------------------

    private void initializeGraphics() {
        GraphicsConfiguration gc = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration();
        var imageCache = new ImageCache(gc);
        gameFrame = new GameFrame(gameSettings, imageCache.icon(ICON_RESOURCE).orElse(null));
        renderer = new AwtRenderer(gameFrame, imageCache);
    }

    private void initializeLogic() {
        var playingState = new PlayingState(gameSettings, gameFrame);
        var pausedState = new PausedState(gameSettings, gameFrame.gameScreen, playingState);
        var gameOverState = new GameOverState(gameSettings, gameFrame.gameScreen, playingState);

        playingState.setPausedState(pausedState);
        playingState.setGameOverState(gameOverState);

        gameSettings.setActualRoundNumber(0);

        stateMachine = new GameStateMachine(pausedState);
    }
}
