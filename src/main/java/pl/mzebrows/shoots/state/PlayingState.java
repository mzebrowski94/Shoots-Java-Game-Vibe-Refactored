// pl/mzebrows/shoots/state/PlayingState.java
package pl.mzebrows.shoots.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.mzebrows.shoots.config.GameConfig;
import pl.mzebrows.shoots.config.GameConfigLoader;
import pl.mzebrows.shoots.config.RoundConfig;
import pl.mzebrows.shoots.ui.GameCounter;
import pl.mzebrows.shoots.ui.GameFrame;
import pl.mzebrows.shoots.ui.GamePointer;
import pl.mzebrows.shoots.ui.GameScreen;
import pl.mzebrows.shoots.app.GameSettings;
import pl.mzebrows.shoots.ui.RoundEnum;
import pl.mzebrows.shoots.input.GameAction;
import pl.mzebrows.shoots.input.InputBridge;
import pl.mzebrows.shoots.ai.AiPlayers;
import pl.mzebrows.shoots.world.PlayInput;
import pl.mzebrows.shoots.world.PlayWorld;

/**
 * Active gameplay state: manages the ROUND_BEGIN → ROUND_CONTINUES → ROUND_ENDS cycle.
 * Transitions out to {@link PausedState} on {@link GameAction#PAUSE} or to
 * {@link GameOverState} when the match ends.
 */
public final class PlayingState implements GameState {

    private static final Logger log = LoggerFactory.getLogger(PlayingState.class);

    private enum Phase { BEGIN, CONTINUES, ENDS }

    private final GameSettings settings;
    private final GameScreen screen;
    private final GameCounter counter;
    private final GamePointer pointer;

    /** Headless simulation of the new model (aiming, pooled discs, bounces, capture scoring). */
    private PlayWorld world;
    /** Computer-controlled players occupying the highest slots (empty when all-human). */
    private AiPlayers aiPlayers = AiPlayers.none();

    private GameState pausedState;
    private GameState gameOverState;

    private Phase phase = Phase.BEGIN;
    private boolean phaseJustEntered = true;
    private boolean requestNextPhase = false;

    // Tracks elapsed time for 1-second round ticks
    private long lastSecondNano = System.nanoTime();

    private boolean restartRequested = false;

    public PlayingState(GameSettings settings, GameFrame frame) {
        this(settings, frame, new PlayWorld(GameConfigLoader.load()));
    }

    /** Constructor allowing a pre-built {@link PlayWorld} (used by tests with a seeded map). */
    public PlayingState(GameSettings settings, GameFrame frame, PlayWorld world) {
        this.settings = settings;
        this.screen  = frame.getGameScreen();
        this.counter = frame.getGameCounter();
        this.pointer = frame.getGamePointer();
        this.world   = world;
    }

    /** The headless simulation backing this state, exposed for rendering and tests. */
    public PlayWorld getWorld() {
        return world;
    }

    /** Must be called after construction to complete the object graph. */
    public void setPausedState(GameState pausedState) { this.pausedState = pausedState; }

    /** Must be called after construction to complete the object graph. */
    public void setGameOverState(GameState gameOverState) { this.gameOverState = gameOverState; }

    /**
     * Requests that the next BEGIN phase restarts the full match instead of starting a new round.
     * Also resets the internal phase so we always start from BEGIN.
     */
    public void requestRestart() {
        restartRequested = true;
        phase = Phase.BEGIN;
        phaseJustEntered = true;
        requestNextPhase = false;
    }

    @Override
    public void enter() {
        lastSecondNano = System.nanoTime();
    }

    @Override
    public GameState update(InputBridge input) {
        if (input.isJustPressed(GameAction.PAUSE)) {
            return pausedState;
        }

        return switch (phase) {
            case BEGIN -> updateBegin();
            case CONTINUES -> updateContinues(input);
            case ENDS -> updateEnds();
        };
    }

    @Override
    public void exit() { /* retain phase so pause/resume works */ }

    /** Current phase as {@link RoundEnum} for the legacy renderer. */
    public RoundEnum getRenderRoundEnum() {
        return switch (phase) {
            case BEGIN -> RoundEnum.ROUND_BEGIN;
            case CONTINUES -> RoundEnum.ROUND_CONTINUES;
            case ENDS -> RoundEnum.ROUND_ENDS;
        };
    }

    // -------------------------------------------------------------------------
    // Phase handlers

    private GameState updateBegin() {
        counter.tick();
        if (phaseJustEntered) {
            if (restartRequested) {
                doRestartGame();
                restartRequested = false;
            } else {
                settings.startNewRound(screen);
            }
            world.resetRound();
            rebuildAiForCurrentMap();
            phaseJustEntered = false;
        }
        screen.tick();
        if (animationsEnded()) {
            restartAnimations();
            requestNextPhase = false;
            phase = Phase.CONTINUES;
            phaseJustEntered = true;
        }
        return this;
    }

    private GameState updateContinues(InputBridge input) {
        if (phaseJustEntered) {
            settings.setPlayerKeyboardAvailable(true);
            phaseJustEntered = false;
            lastSecondNano = System.nanoTime();
        }
        counter.tick();

        long now = System.nanoTime();
        if (now - lastSecondNano >= 1_000_000_000L) {
            lastSecondNano = now;
            tickRoundSecond();
        }

        if (settings.isPlayerKeyboardAvailable()) {
            PlayInput.apply(input, world);
            aiPlayers.think(world);
        }
        world.step();
        if (requestNextPhase) {
            phase = Phase.ENDS;
            phaseJustEntered = true;
        }
        return this;
    }

    private GameState updateEnds() {
        counter.tick();
        if (phaseJustEntered) {
            settings.setPlayerKeyboardAvailable(false);
            phaseJustEntered = false;
            world.finishRound();
        }
        screen.tick();
        pointer.tick();
        if (animationsEnded()) {
            restartAnimations();
            requestNextPhase = false;
            if (world.isMatchOver()) {
                world.resolveMatchWinners();
                settings.setGameEnd(true);
                return gameOverState;
            }
            phase = Phase.BEGIN;
            phaseJustEntered = true;
        }
        return this;
    }

    // -------------------------------------------------------------------------
    // Helpers

    private void tickRoundSecond() {
        settings.getActualRound().roundTick();
        if (settings.getActualRound().isRoundEnd()) {
            settings.setPlayerKeyboardAvailable(false);
            if (countActiveDiscs() == 0) {
                settings.getActualRound().setRoundEndTimeDelay(settings.getActualRound().getRoundEndTimeDelay() + 1);
                if (settings.getActualRound().getRoundEndTimeDelay() >= settings.getRoundEndDelay()) {
                    requestNextPhase = true;
                }
            }
        }
    }

    private int countActiveDiscs() {
        return world.totalActiveDiscs();
    }

    private boolean animationsEnded() {
        return screen.isAnimationElementEnd()
                && counter.isAnimationElementEnd();
    }

    private void restartAnimations() {
        counter.restartAnimation();
        pointer.restartAnimation();
        screen.restartAnimation();
    }

    private void doRestartGame() {
        settings.restartGame();
        rebuildWorldForSelectedPlayers();
        world.resetMatch();
        pointer.restartGamePointer();
        counter.restartAnimationTime();
        settings.startNewRound(screen);
        log.info("Game restarted");
    }

    /**
     * (Re)builds the AI controllers against the world's CURRENT map. Called after every
     * {@link PlayWorld#resetRound()} (the map regenerates each round) so the AI's targeting binds to the
     * fresh collider, and when the world itself is rebuilt for a new player selection.
     */
    private void rebuildAiForCurrentMap() {
        aiPlayers = AiPlayers.build(world, settings.getAiNumber(), settings.getAiDifficulty(),
                world.config().ai().scanAngles());
    }

    /**
     * Rebuilds {@link #world} so its player count matches the menu selection
     * ({@code settings.getPlayerNumber()}); the initial world is built from {@code game.properties},
     * which would otherwise ignore a 3- or 4-player choice (bases/discs for P3/P4 never appear).
     */
    private void rebuildWorldForSelectedPlayers() {
        GameConfig base = GameConfigLoader.load();
        int selected = Math.max(1, Math.min(4, settings.getPlayerNumber()));
        RoundConfig round = applySelectedRoundSettings(base.round(),
                settings.getRoundLimit(), settings.getRoundTime());
        if (selected != base.playerNumber()) {
            base = base.withPlayerNumber(selected);
        }
        if (round != base.round()) {
            base = base.withRound(round);
        }
        world = new PlayWorld(base);
        rebuildAiForCurrentMap();
        screen.setWorld(world, 0.0);
        pointer.setWorld(world);
    }

    /**
     * Overlays the menu-selected round limit and round time onto the loaded {@link RoundConfig} so the
     * match runs for the number of rounds chosen in the menu instead of the {@code game.properties}
     * default; returns the original config unchanged if the menu values are non-positive or identical.
     */
    static RoundConfig applySelectedRoundSettings(RoundConfig defaults, int selectedRoundLimit, int selectedRoundTime) {
        int roundLimit = selectedRoundLimit > 0 ? selectedRoundLimit : defaults.roundLimit();
        int roundTime = selectedRoundTime > 0 ? selectedRoundTime : defaults.roundTimeSeconds();
        if (roundLimit == defaults.roundLimit() && roundTime == defaults.roundTimeSeconds()) {
            return defaults;
        }
        return new RoundConfig(roundTime, roundLimit, defaults.roundEndDelay(), defaults.animationTime());
    }
}
