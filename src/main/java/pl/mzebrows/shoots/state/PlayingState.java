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

    /** Online: this peer opened the pause menu, so on returning it must broadcast RESUME to unfreeze peers (#3). */
    private boolean localPauseInitiated = false;

    /** Whether the most recent {@link #update} actually advanced the sim; false on an online lockstep stall or
     *  remote pause. The game loop reads this to avoid draining real time while the world is frozen (#1). */
    private boolean steppedThisUpdate = true;

    // Round-timing diagnostics (#1): wall-clock start of the current play phase + a one-shot expiry log guard.
    private long roundStartNanos;
    private boolean roundExpiryLogged;

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
        endOnline();
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
        endOnline(); // close any prior session first so its host port is freed before this one runs
        this.online = session;
        this.world = session.world();
        applyOnlineRoundSettings(session.world().config().round());
        counter.restartAnimationTime(); // refresh the on-screen round-timer bar to the online round time (#1)
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
        steppedThisUpdate = true; // assume real progress; the online path clears it on a stall/pause
        // Returned from our own pause menu: tell peers to resume so everyone unfreezes together (#3).
        if (online != null && localPauseInitiated) {
            online.requestPause(false);
            localPauseInitiated = false;
        }

        if (input.isJustPressed(GameAction.PAUSE)) {
            // Online: pausing is match-wide -- freeze every peer (and their round timer) until we resume (#3).
            if (online != null) {
                online.requestPause(true);
                localPauseInitiated = true;
            }
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
        } else if (online != null && online.mode() == GameMode.CLIENT && !online.isConnected()) {
            return abortToOnlineMenu("Host left the game"); // host vanished during the round intro (#5)
        }
        return this;
    }

    private GameState updateContinues(InputBridge input) {
        if (phaseJustEntered) {
            settings.setPlayerKeyboardAvailable(true);
            phaseJustEntered = false;
            stepsSinceRoundSecond = 0;
            roundStartNanos = System.nanoTime();
            roundExpiryLogged = false;
            log.info("Round {} playing: roundTime={}s, roundLimit={}, mode={}",
                    settings.getActualRoundNumber(), settings.getRoundTime(), settings.getRoundLimit(), flow.mode());
        }

        if (online != null) {
            GameState next = advanceOnlinePhase(input);
            if (next != this) {
                return next;
            }
        } else {
            counter.tick();
            advanceOffline(input);
        }

        if (flow.enterEnds(flowFrame, requestNextPhase)) {
            phaseJustEntered = true;
        }
        return this;
    }

    /**
     * Online CONTINUES driver. Refreshes pause/connection status, then: bails to the online menu if the host
     * vanished (#5); freezes the sim AND the round-timer animation while a remote peer has paused, showing a
     * "PLAYER n PAUSED" banner (#3); otherwise advances the lockstep frame normally. Returns a non-{@code this}
     * state only when leaving play (host loss).
     */
    private GameState advanceOnlinePhase(InputBridge input) {
        online.pump(); // refresh pause/connection + drain control even on a frozen/stalled tick

        if (online.mode() == GameMode.CLIENT && !online.isConnected()) {
            return abortToOnlineMenu("Host left the game");
        }

        int pausedBy = online.pausedBy();
        if (pausedBy >= 0 && pausedBy != online.localSlot()) {
            // A different player paused: hold the round timer and the simulation, and show who paused.
            screen.setOnlinePauseNotice("PLAYER " + (pausedBy + 1) + " PAUSED");
            steppedThisUpdate = false; // frozen: no real time should be consumed by the loop
            return this;
        }
        screen.setOnlinePauseNotice(null);

        boolean stepped = advanceOnline(input);
        if (stepped) {
            counter.tick(); // advance the round-timer bar in lockstep with the simulation, not wall-clock
        }
        steppedThisUpdate = stepped; // a lockstep stall (false) tells the loop to refund the consumed step (#1)
        return this;
    }

    /** Whether the last {@link #update} advanced the simulation (false on an online stall/pause); see {@link GameLoop}. */
    public boolean lastUpdateAdvancedSim() {
        return steppedThisUpdate;
    }

    /**
     * Tears down the online session and drops the player onto the PLAY ONLINE connect screen with a notice;
     * the match bookkeeping is reset so the stale match isn't resumable from the menu (#5).
     */
    private GameState abortToOnlineMenu(String message) {
        log.info("Online match aborted: {}", message);
        endOnline();
        flow = RoundFlow.offline();
        settings.setPlayerKeyboardAvailable(false);
        settings.restartGame();
        settings.setGameEnd(false);
        screen.setOnlinePauseNotice(null);
        screen.getMenuLayout().showOnlineDisconnected(message);
        return pausedState;
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
    private boolean advanceOnline(InputBridge input) {
        boolean stepped = online.advance(input);
        if (stepped) {
            if (flow.mode() != GameMode.CLIENT && ++stepsSinceRoundSecond >= STEPS_PER_ROUND_SECOND) {
                stepsSinceRoundSecond = 0;
                tickRoundSecond();
            }
            flowFrame++;
        }
        return stepped;
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
                // Match is decided: the host has already broadcast MATCH_OVER (flushed), so closing now frees
                // its port for a fresh host and drops the client socket cleanly (#6).
                endOnline();
                return gameOverState;
            }
            case NEXT_ROUND -> {
                restartAnimations();
                requestNextPhase = false;
                phaseJustEntered = true;
            }
            case STAY -> {
                if (online != null && online.mode() == GameMode.CLIENT && !online.isConnected()) {
                    return abortToOnlineMenu("Host left the game"); // host vanished during the round outro (#5)
                }
            }
        }
        return this;
    }

    // -------------------------------------------------------------------------
    // Helpers

    /** Closes and clears any active online session (frees the host's port; drops a client's socket). */
    private void endOnline() {
        if (online != null) {
            online.close();
            online = null;
        }
    }

    /**
     * Mirrors the online match's authoritative {@link RoundConfig} (built from the host's menu choices) onto
     * the local {@link GameSettings} so the round clock and the on-screen round-timer reflect the chosen
     * round time / limit on every peer, not each machine's {@code game.properties} default (#7).
     */
    private void applyOnlineRoundSettings(RoundConfig round) {
        settings.setRoundTime(round.roundTimeSeconds());
        settings.setRoundLimit(round.roundLimit());
        settings.setRoundEndDelay(round.roundEndDelay());
        settings.setAnimationTime(round.animationTime());
    }

    private void tickRoundSecond() {
        settings.getActualRound().roundTick();
        log.debug("Round {} clock: {}/{}s elapsed ({} active discs)", settings.getActualRoundNumber(),
                settings.getActualRound().getRoundTime(), settings.getRoundTime(), countActiveDiscs());
        if (settings.getActualRound().isRoundEnd()) {
            settings.setPlayerKeyboardAvailable(false);
            if (!roundExpiryLogged) {
                roundExpiryLogged = true;
                log.info("Round {} timer hit {}s after {} ms wall-clock; waiting for {} disc(s) to settle",
                        settings.getActualRoundNumber(), settings.getRoundTime(),
                        (System.nanoTime() - roundStartNanos) / 1_000_000L, countActiveDiscs());
            }
            if (countActiveDiscs() == 0) {
                settings.getActualRound().setRoundEndTimeDelay(settings.getActualRound().getRoundEndTimeDelay() + 1);
                if (settings.getActualRound().getRoundEndTimeDelay() >= settings.getRoundEndDelay()) {
                    if (!requestNextPhase) {
                        log.info("Round {} ending after {} ms wall-clock (target {}s)", settings.getActualRoundNumber(),
                                (System.nanoTime() - roundStartNanos) / 1_000_000L, settings.getRoundTime());
                    }
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
