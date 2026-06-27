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
import pl.mzebrows.shoots.net.GameMode;
import pl.mzebrows.shoots.net.OnlineSession;
import pl.mzebrows.shoots.net.RoundFlow;
import pl.mzebrows.shoots.world.PlayInput;
import pl.mzebrows.shoots.world.PlayWorld;

/**
 * Active gameplay state: manages the ROUND_BEGIN -> ROUND_CONTINUES -> ROUND_ENDS cycle.
 * Transitions out to {@link PausedState} on {@link GameAction#PAUSE} or to
 * {@link GameOverState} when the match ends.
 *
 * <p>Phase ownership is delegated to a {@link RoundFlow}: OFFLINE (the default) decides every transition
 * locally exactly as before; HOST decides locally AND broadcasts each transition; CLIENT follows the
 * host's broadcast transitions instead of its own render-coupled timers (see {@code OnlineMode.md}, F2).
 * The local transition CONDITIONS are unchanged -- only who may act on them differs by mode.
 */
public final class PlayingState implements GameState {

    private static final Logger log = LoggerFactory.getLogger(PlayingState.class);

    private final GameSettings settings;
    private final GameScreen screen;
    private final GameCounter counter;
    private final GamePointer pointer;

    /** Headless simulation of the new model (aiming, pooled discs, bounces, capture scoring). */
    private PlayWorld world;
    /** Computer-controlled players occupying the highest slots (empty when all-human). */
    private AiPlayers aiPlayers = AiPlayers.none();

    /** Non-null in online mode: drives the match from the network instead of local hotseat input. */
    private OnlineSession online;

    /** Owns the BEGIN/CONTINUES/ENDS phase; mode-aware (OFFLINE by default, or HOST/CLIENT for online).
     *  Mutable so an online match can be started into this state at runtime from the menu (see {@link #startOnline}). */
    private RoundFlow flow;

    private GameState pausedState;
    private GameState gameOverState;

    private boolean phaseJustEntered = true;
    private boolean requestNextPhase = false;

    /**
     * Simulation step rate; mirrors {@code GameLoop.UPDATES_PER_SECOND} (and {@code PlayWorld}'s step
     * rate). The round clock is counted in SIM STEPS, not wall-clock, so it advances identically on
     * every machine -- the determinism prerequisite for online lockstep (see {@code OnlineMode.md}, F0).
     */
    private static final int STEPS_PER_ROUND_SECOND = 120;

    // Simulation steps elapsed within the current round-second (tick-based, replaces a wall-clock timer).
    private int stepsSinceRoundSecond = 0;

    // Monotonic sim-frame counter used to stamp host-broadcast control events (advances during play).
    private long flowFrame = 0;

    private boolean restartRequested = false;

    public PlayingState(GameSettings settings, GameFrame frame) {
        this(settings, frame, new PlayWorld(GameConfigLoader.load()));
    }

    /** Constructor allowing a pre-built {@link PlayWorld} (used by tests with a seeded map); OFFLINE flow. */
    public PlayingState(GameSettings settings, GameFrame frame, PlayWorld world) {
        this(settings, frame, world, RoundFlow.offline());
    }

    /** Full constructor: a pre-built world and a {@link RoundFlow} (OFFLINE / HOST / CLIENT). */
    public PlayingState(GameSettings settings, GameFrame frame, PlayWorld world, RoundFlow flow) {
        this.settings = settings;
        this.screen  = frame.getGameScreen();
        this.counter = frame.getGameCounter();
        this.pointer = frame.getGamePointer();
        this.world   = world;
        this.flow    = flow;
    }

    /** The headless simulation backing this state, exposed for rendering and tests. */
    public PlayWorld getWorld() {
        return world;
    }

    /** Must be called after construction to complete the object graph. */
    public void setPausedState(GameState pausedState) { this.pausedState = pausedState; }

    /** Must be called after construction to complete the object graph. */
    public void setGameOverState(GameState gameOverState) { this.gameOverState = gameOverState; }

    /** Puts this state into online mode (host/client); {@code null} keeps offline/hotseat play. */
    public void setOnlineSession(OnlineSession online) { this.online = online; }

    /**
     * Requests that the next BEGIN phase restarts the full match instead of starting a new round.
     * Also resets the internal phase so we always start from BEGIN.
     */
    public void requestRestart() {
        // Returning to a local match from a prior online one: drop the network session + restore offline flow.
        online = null;
        flow = RoundFlow.offline();
        restartRequested = true;
        flow.reset();
        phaseJustEntered = true;
        requestNextPhase = false;
    }

    /**
     * Starts an online match into this state at runtime (selected from the menu's waiting room). Adopts the
     * session's shared world + host/client {@link RoundFlow}, resets round bookkeeping to a fresh match, and
     * points the screen/pointer at the networked world. No AI runs online.
     */
    public void startOnline(OnlineSession session) {
        this.online = session;
        this.world = session.world();
        this.flow = session.flow();
        flow.reset();
        settings.restartGame();
        settings.setAiNumber(0);
        settings.setGameEnd(false);
        phaseJustEntered = true;
        requestNextPhase = false;
        restartRequested = false;
        flowFrame = 0;
        stepsSinceRoundSecond = 0;
        screen.setWorld(world, 0.0);
        pointer.setWorld(world);
        log.info("Online match started: mode {}, local slot {}", session.mode(), session.localSlot());
    }

    @Override
    public void enter() {
        stepsSinceRoundSecond = 0;
    }

    @Override
    public GameState update(InputBridge input) {
        if (input.isJustPressed(GameAction.PAUSE)) {
            return pausedState;
        }

        return switch (flow.phase()) {
            case BEGIN -> updateBegin();
            case CONTINUES -> updateContinues(input);
            case ENDS -> updateEnds();
        };
    }

    @Override
    public void exit() { /* retain phase so pause/resume works */ }

    /** Current phase as {@link RoundEnum} for the legacy renderer. */
    public RoundEnum getRenderRoundEnum() {
        return switch (flow.phase()) {
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
        if (online != null) {
            online.pump(); // receive the host's phase CONTROLs even though no input/step happens in BEGIN
        }
        if (flow.enterContinues(flowFrame, animationsEnded())) {
            restartAnimations();
            requestNextPhase = false;
            phaseJustEntered = true;
        }
        return this;
    }

    private GameState updateContinues(InputBridge input) {
        if (phaseJustEntered) {
            settings.setPlayerKeyboardAvailable(true);
            phaseJustEntered = false;
            stepsSinceRoundSecond = 0;
        }
        counter.tick();

        if (online != null) {
            advanceOnline(input);
        } else {
            advanceOffline(input);
        }

        if (flow.enterEnds(flowFrame, requestNextPhase)) {
            phaseJustEntered = true;
        }
        return this;
    }

    /** Offline / hotseat advance: the unchanged single-machine path (round timer, local input, one step). */
    private void advanceOffline(InputBridge input) {
        // Count sim steps (this runs once per fixed step), not wall-clock, so the round clock is
        // deterministic. The host/offline peer owns the round timer; a CLIENT follows the host's ENTER_ENDS.
        if (flow.mode() != GameMode.CLIENT && ++stepsSinceRoundSecond >= STEPS_PER_ROUND_SECOND) {
            stepsSinceRoundSecond = 0;
            tickRoundSecond();
        }
        if (settings.isPlayerKeyboardAvailable()) {
            PlayInput.apply(input, world);
            aiPlayers.think(world);
        }
        world.step();
        flowFrame++;
    }

    /**
     * Online advance: the {@link OnlineSession} exchanges this command frame and steps the shared world
     * only once every peer's input is in (lockstep). The round clock + frame counter advance only on a
     * real step; AI is not run online. Phase transitions still flow through the session's host-driven
     * {@code flow} (a CLIENT follows the broadcast CONTROLs).
     */
    private void advanceOnline(InputBridge input) {
        if (online.advance(input)) {
            if (flow.mode() != GameMode.CLIENT && ++stepsSinceRoundSecond >= STEPS_PER_ROUND_SECOND) {
                stepsSinceRoundSecond = 0;
                tickRoundSecond();
            }
            flowFrame++;
        }
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
        if (online != null) {
            online.pump(); // receive the host's NEXT_ROUND / MATCH_OVER even though no step happens in ENDS
        }
        switch (flow.resolveEnds(flowFrame, animationsEnded(), world.isMatchOver())) {
            case MATCH_OVER -> {
                restartAnimations();
                requestNextPhase = false;
                world.resolveMatchWinners();
                settings.setGameEnd(true);
                return gameOverState;
            }
            case NEXT_ROUND -> {
                restartAnimations();
                requestNextPhase = false;
                phaseJustEntered = true;
            }
            case STAY -> { /* still animating the round-end screen */ }
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
