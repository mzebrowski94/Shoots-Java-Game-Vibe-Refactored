// pl/mzebrows/shoots/app/GameLoop.java
package pl.mzebrows.shoots.app;

import pl.mzebrows.shoots.ui.GameFrame;
import pl.mzebrows.shoots.ui.RoundEnum;

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
import pl.mzebrows.shoots.config.GameConfig;
import pl.mzebrows.shoots.config.GameConfigLoader;
import pl.mzebrows.shoots.config.OnlineConfig;
import pl.mzebrows.shoots.net.LanDiscovery;
import pl.mzebrows.shoots.net.OnlineSession;

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
    private PlayingState playingState;
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

    /** The live simulation; stable across pause/menu so panels always render the new model. */
    private PlayWorld currentWorld() {
        return playingState != null ? playingState.getWorld() : null;
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
        OnlineConfig onlineConfig = OnlineConfig.load();
        OnlineSession session = onlineConfig.isOnline() ? buildOnlineSession(onlineConfig) : null;

        if (session != null) {
            playingState = new PlayingState(gameSettings, gameFrame, session.world(), session.flow());
            playingState.setOnlineSession(session);
            gameFrame.getGameScreen().setWorld(session.world(), 0.0);
            gameFrame.getGamePointer().setWorld(session.world());
            log.info("Online match {} ready ({})", session.matchCode(), session.mode());
        } else {
            playingState = new PlayingState(gameSettings, gameFrame);
        }

        var pausedState = new PausedState(gameSettings, gameFrame.getGameScreen(), playingState);
        var gameOverState = new GameOverState(gameSettings, gameFrame.getGameScreen(), playingState);

        playingState.setPausedState(pausedState);
        playingState.setGameOverState(gameOverState);

        gameSettings.setActualRoundNumber(0);

        // Online: start the match immediately (lockstep handshake already done); offline: show the menu.
        stateMachine = new GameStateMachine(session != null ? playingState : pausedState);
    }

    /**
     * Builds the online session from {@code game.properties}: {@code online.mode=host} hosts (and beacons
     * on the LAN); {@code online.mode=client} joins {@code online.host:online.port} if set, otherwise picks
     * the first LAN-discovered host. Returns {@code null} (and the game starts offline) on any failure.
     */
    private OnlineSession buildOnlineSession(OnlineConfig oc) {
        GameConfig base = GameConfigLoader.load();
        String name = System.getProperty("user.name", "Player");
        try {
            if (oc.isHost()) {
                int players = Math.max(2, Math.min(4, base.menu().initialPlayers()));
                return OnlineSession.host(base, players, oc.port(), name);
            }
            if (oc.hasHostAddress()) {
                return OnlineSession.join(base, oc.host(), oc.port(), name);
            }
            return joinViaDiscovery(base, name);
        } catch (Exception e) {
            log.error("Online setup failed (mode={}); starting offline", oc.mode(), e);
            return null;
        }
    }

    /** Waits briefly for a LAN host beacon and joins the first one found; {@code null} if none appears. */
    private OnlineSession joinViaDiscovery(GameConfig base, String name) throws Exception {
        try (var discovery = new LanDiscovery(4000)) {
            discovery.start();
            long deadline = System.nanoTime() + 15_000L * 1_000_000L;
            while (System.nanoTime() < deadline) {
                var matches = discovery.matches();
                if (!matches.isEmpty()) {
                    var match = matches.get(0);
                    log.info("Joining discovered LAN match {} at {}:{}", match.matchCode(), match.host(), match.port());
                    return OnlineSession.join(base, match.host(), match.port(), name);
                }
                Thread.sleep(100);
            }
        }
        log.error("No LAN host discovered; starting offline");
        return null;
    }
}
