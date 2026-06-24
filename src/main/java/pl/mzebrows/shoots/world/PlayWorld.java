// src/main/java/pl/mzebrows/shoots/world/PlayWorld.java
package pl.mzebrows.shoots.world;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import lombok.extern.slf4j.Slf4j;
import pl.mzebrows.shoots.config.GameConfig;
import pl.mzebrows.shoots.entity.AimController;
import pl.mzebrows.shoots.entity.DiscAttackStrategy;
import pl.mzebrows.shoots.entity.Entity;
import pl.mzebrows.shoots.entity.EntitySpawner;
import pl.mzebrows.shoots.entity.LaserPredictor;
import pl.mzebrows.shoots.pool.ObjectPool;
import pl.mzebrows.shoots.score.CaptureScoring;
import pl.mzebrows.shoots.spatial.GridPathTracer;
import pl.mzebrows.shoots.spatial.MapGenerator;
import pl.mzebrows.shoots.spatial.SpatialCollider;
import pl.mzebrows.shoots.spatial.TileType;
import pl.mzebrows.shoots.spatial.UniformGridCollider;
import pl.mzebrows.shoots.system.CombatSystem;
import pl.mzebrows.shoots.system.DiscSystem;

/**
 * Headless, AWT-free facade over the cluster 4-7 model that drives one round of play: aiming,
 * shooting (per-player cap), pooled disc physics, wall bounces, and capture-point scoring.
 *
 * <p>Owns the {@link UniformGridCollider} (built from a {@link MapGenerator} map), a single pooled
 * disc {@link List}, per-player {@link AimController} + {@link DiscAttackStrategy}, and a
 * {@link CaptureScoring}. The renderer reads state through the queryable getters; nothing here
 * references {@code Graphics2D}, so the whole simulation is unit-testable without a graphics context.
 */
@Slf4j
public final class PlayWorld {

    /** Per-player aim intent for a single step, decoupled from raw key codes. */
    public enum AimInput { NONE, LEFT, RIGHT }

    /** Fixed firing direction and pixel origin of a player's base, derived from the map. */
    public record BasePlacement(int playerId, int tileX, int tileY, int pixelX, int pixelY, int shootDirection) { }

    private static final int[] SHOOT_DIRECTIONS = {180, 0, -90, 90};

    /**
     * Whether a player's LEFT/RIGHT keys are mirrored, so each HUMAN player's key turns the cursor
     * toward their own left/right given the seated orientation of their base (legacy
     * {@code Player.moveUnit}: P1/P3 = -1 are mirrored vs P2/P4 = +1). Indexed by 0-based player id.
     * This is a human-input concern only; the AI drives {@link #applyInput} with absolute LEFT/RIGHT.
     */
    private static final boolean[] AIM_KEYS_MIRRORED = {true, false, true, false};

    /** Whether {@code playerId}'s LEFT/RIGHT aim keys should be swapped to match their seated view. */
    public static boolean aimKeysMirrored(int playerId) {
        return AIM_KEYS_MIRRORED[playerId];
    }

    /** Max cursor rotation each way from the base firing direction, in degrees (legacy ±110 = 220° arc). */
    private static final double AIM_ROTATION_LIMIT_DEG = 110.0;

    /** Simulation steps per second; mirrors {@code GameLoop.UPDATES_PER_SECOND} (charge is timed in steps). */
    private static final int SIMULATION_STEPS_PER_SECOND = 120;

    private final GameConfig config;
    private final int playerCount;
    private final long seed;

    private SpatialCollider collider;
    private GridPathTracer tracer;
    private TileType[][] tiles;

    private final CombatSystem combatSystem;
    private final DiscSystem discSystem;
    private final CaptureScoring scoring = new CaptureScoring();
    private final MatchFlow matchFlow;
    private LaserPredictor laserPredictor;

    private final ObjectPool<Entity> discPool;
    private final List<Entity> discs;

    /** Active block-hit flashes (transient wall-tile effects), advanced each step and pruned when done. */
    private final List<BlockHitEffect> blockHits = new ArrayList<>();
    private final Map<Long, BlockHitEffect> blockHitByTile = new java.util.HashMap<>();

    /**
     * Disc-instance -> owning player, kept by identity. {@link CombatSystem#retire} returns the disc
     * to the pool (which resets its fields, wiping {@code ownerId}) before the retirement sink runs,
     * so the owner must be tracked here rather than read off the entity post-retire.
     */
    private final Map<Entity, Integer> discOwners = new IdentityHashMap<>();

    private final AimController[] aim;
    private final DiscAttackStrategy[] attack;
    private BasePlacement[] bases;

    /** Per-player power-shot charge state (human input only; AI fires power shots directly). */
    private final int[] chargeTicks;
    private final boolean[] chargeConsumed;
    private final boolean[] charging;
    private final boolean[] shootHeldPrev;
    private final int chargeThresholdTicks;
    private final boolean powerEnabled;

    /** Base seed for map generation; each round's map uses mix(baseMapSeed, roundIndex). */
    private final long baseMapSeed;
    /** 0-based index of the current round, advanced each resetRound() so each round gets a new map. */
    private int roundIndex;

    /** Reusable scratch source used only to feed {@link DiscAttackStrategy#attack}. */
    private final Entity fireSource = new Entity();

    private final DiscSystem.DiscEventSink sink = new DiscSystem.DiscEventSink() {
        @Override public boolean onCapturePointHit(Entity disc, int tileX, int tileY) {
            // A power disc applies several capture levels per hit (captureStrength); a normal disc 1.
            return scoring.resolveHit(tileX, tileY, disc.getOwnerId(), disc.getCaptureStrength());
        }
        @Override public void onWallHit(Entity disc, int tileX, int tileY) {
            spawnBlockHit(tileX, tileY, disc.getOwnerId());
        }
        @Override public void onDiscRetired(Entity disc) {
            Integer owner = discOwners.remove(disc);
            if (owner != null && owner >= 0 && owner < attack.length) {
                attack[owner].onDiscRetired();
            }
            discs.remove(disc);
        }
    };

    /**
     * Spawns through {@link CombatSystem} and immediately tracks the disc in our list, so the
     * tracked list is always the authoritative set of live discs.
     */
    private final EntitySpawner trackingSpawner = new EntitySpawner() {
        @Override public Entity spawnDisc(double x, double y, double angle, int ownerId, boolean powered) {
            Entity disc = combatSystem.spawnDisc(x, y, angle, ownerId, powered);
            if (disc != null) {
                discs.add(disc);
                discOwners.put(disc, ownerId);
            }
            return disc;
        }
    };

    /** Builds a world from the config's resolved master seed, so the whole round is reproducible. */
    public PlayWorld(GameConfig config) {
        this(config, config.seed());
    }

    /** Builds a world from an explicit master seed (seeds the map and is exposed to the AI). */
    public PlayWorld(GameConfig config, long seed) {
        this(config, seed, new Random(seed));
    }

    /**
     * Builds a world with a caller-supplied {@link Random} so tests can drive the map directly; the
     * recorded {@code seed} is {@code 0} (such worlds are not meant to be reproduced from a seed).
     */
    public PlayWorld(GameConfig config, Random random) {
        this(config, 0L, random);
    }

    private PlayWorld(GameConfig config, long seed, Random random) {
        log.info("Initialising world. Seed: {}", seed);
        this.config = config;
        this.seed = seed;
        this.playerCount = config.playerNumber();
        // Base map seed: the master seed for production worlds, or a value drawn from the caller's
        // Random for test worlds (seed==0). Each round derives its own map seed from this + roundIndex.
        this.baseMapSeed = seed != 0 ? seed : random.nextLong();
        this.roundIndex = 0;

        int unit = config.grid().unit();
        int maxDiscsPerPlayer = config.disc().maxPerPlayer();
        this.discPool = new ObjectPool<>(maxDiscsPerPlayer * playerCount, Entity::new, Entity::reset);
        this.discs = new ArrayList<>(discPool.capacity());
        this.combatSystem = new CombatSystem(discPool, config.disc(), config.power());
        this.matchFlow = new MatchFlow(playerCount, config.round());

        this.aim = new AimController[playerCount];
        this.attack = new DiscAttackStrategy[playerCount];
        for (int p = 0; p < playerCount; p++) {
            attack[p] = new DiscAttackStrategy(maxDiscsPerPlayer);
        }

        // Power-shot charge state (human input drives this; AI fires power shots directly).
        this.chargeTicks = new int[playerCount];
        this.chargeConsumed = new boolean[playerCount];
        this.charging = new boolean[playerCount];
        this.shootHeldPrev = new boolean[playerCount];
        this.powerEnabled = config.power().enabled();
        this.chargeThresholdTicks =
                Math.max(1, (int) Math.round(config.power().chargeSeconds() * SIMULATION_STEPS_PER_SECOND));

        // Build the round-0 map and everything that depends on it (collider, tracer, laser, bases, points).
        buildMap(mapSeedFor(0));
        // DiscSystem advances discs along the analytic tracer; it is rebound on each map regeneration.
        this.discSystem = new DiscSystem(tracer, combatSystem);
    }

    /** Per-round map seed derived from the base seed, so each round gets a fresh yet reproducible layout. */
    private long mapSeedFor(int round) {
        long h = baseMapSeed ^ (0x9E3779B97F4A7C15L * (round + 1));
        h ^= (h >>> 30);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 27);
        return h;
    }

    /**
     * (Re)builds the map and everything derived from it for the given {@code mapSeed}: tiles, collider,
     * laser predictor, per-player bases + aim controllers, and the capture-point registry. Called once
     * at construction and again from {@link #resetRound()} so every round is a different (reproducible)
     * map. {@code discSystem} is rebound to the new collider here too (after the first build).
     */
    private void buildMap(long mapSeed) {
        int unit = config.grid().unit();
        this.tiles = new MapGenerator(config.grid(), new Random(mapSeed)).generate(playerCount);
        this.collider = new UniformGridCollider(tiles, config.grid(), config.collision());
        this.tracer = new GridPathTracer(collider, unit);
        this.laserPredictor = new LaserPredictor(tracer);
        this.bases = locateBases(unit);
        if (aim != null) {
            for (int p = 0; p < playerCount; p++) {
                aim[p] = new AimController(bases[p].shootDirection(), AIM_ROTATION_LIMIT_DEG, 1.0);
            }
        }
        if (discSystem != null) {
            discSystem.setTracer(tracer);
        }
        scoring.clear();
        registerCapturePoints();
    }

    // -- per-step API -------------------------------------------------------

    /** Applies this step's aim intent and an optional shoot trigger for {@code playerId}. */
    public void applyInput(int playerId, AimInput aimInput, boolean shoot) {
        AimController controller = aim[playerId];
        switch (aimInput) {
            case LEFT -> controller.rotateLeft(1.0);
            case RIGHT -> controller.rotateRight(1.0);
            case NONE -> { /* hold aim */ }
        }
        if (shoot) {
            fire(playerId);
        }
    }

    /** Advances all active discs by one fixed step (movement + collision + retire), then refreshes scores. */
    public void step() {
        discSystem.update(discs, sink);
        advanceBlockHits();
        matchFlow.syncCurrentPoints(scoring);
    }

    /** Finalises the current round: banks points and awards the round winner(s). */
    public void finishRound() {
        matchFlow.syncCurrentPoints(scoring);
        matchFlow.finishRound();
    }

    /** Whether the configured round limit has been reached. */
    public boolean isMatchOver() {
        return matchFlow.isMatchOver();
    }

    /** Resolves and flags the overall match winner(s); call once the match is over. */
    public java.util.List<pl.mzebrows.shoots.score.PlayerScore> resolveMatchWinners() {
        return matchFlow.resolveWinners();
    }

    /** Resets the entire match for a new game (zeroes every score tally and the round counter). */
    public void resetMatch() {
        matchFlow.resetMatch();
        roundIndex = 0;
        resetRound();
    }

    /** Fires one normal disc for {@code playerId} if under the per-player cap and the pool has room. */
    public boolean fire(int playerId) {
        return fireDisc(playerId, false);
    }

    /**
     * Fires one charged power disc for {@code playerId} (faster, more bounces, stronger capture). Falls
     * back to a normal disc when the power shot is disabled in config. Used by the human charge release
     * and directly by the AI; subject to the same per-player disc cap as a normal shot.
     */
    public boolean firePower(int playerId) {
        return fireDisc(playerId, powerEnabled);
    }

    private boolean fireDisc(int playerId, boolean powered) {
        BasePlacement base = bases[playerId];
        fireSource.reset();
        fireSource.setX(base.pixelX());
        fireSource.setY(base.pixelY());
        fireSource.setAngle(aim[playerId].currentAngle());
        fireSource.setOwnerId(playerId);
        fireSource.setPowered(powered);
        return attack[playerId].attack(fireSource, trackingSpawner);
    }

    /**
     * Drives a human player's shoot key for this step (hold-to-charge, auto-fire when full):
     * <ul>
     *   <li>press (rising edge): fires a normal disc immediately (classic feel) and starts charging;</li>
     *   <li>hold: fills the charge ring and auto-releases ONE power disc the moment it fills;</li>
     *   <li>release: clears the charge.</li>
     * </ul>
     * The AI does not use this path -- it fires via {@link #fire}/{@link #firePower} directly.
     */
    public void applyShoot(int playerId, boolean shootHeld) {
        boolean prev = shootHeldPrev[playerId];
        if (shootHeld && !prev) {
            fire(playerId);
            chargeTicks[playerId] = 0;
            chargeConsumed[playerId] = false;
            charging[playerId] = powerEnabled;
        } else if (shootHeld) {
            if (charging[playerId] && !chargeConsumed[playerId]) {
                chargeTicks[playerId]++;
                if (chargeTicks[playerId] >= chargeThresholdTicks) {
                    firePower(playerId);
                    chargeConsumed[playerId] = true;
                    charging[playerId] = false;
                }
            }
        } else {
            chargeTicks[playerId] = 0;
            chargeConsumed[playerId] = false;
            charging[playerId] = false;
        }
        shootHeldPrev[playerId] = shootHeld;
    }

    /** Power-shot charge fill for {@code playerId} in {@code [0,1]} (0 when not charging), for the renderer. */
    public double chargeProgress(int playerId) {
        if (!charging[playerId] || chargeThresholdTicks <= 0) {
            return 0.0;
        }
        double p = (double) chargeTicks[playerId] / chargeThresholdTicks;
        return p < 0.0 ? 0.0 : Math.min(p, 1.0);
    }

    /** Predicts the laser polyline for {@code playerId} into caller-supplied arrays (no allocation). */
    public int predictLaser(int playerId, int[] xs, int[] ys) {
        BasePlacement base = bases[playerId];
        return laserPredictor.predict(base.pixelX(), base.pixelY(),
                aim[playerId].currentAngle(), config.disc().moveSpeed(), xs, ys);
    }

    /**
     * Retires all in-flight discs, resets aim + scoring, and REGENERATES the map for a new round, so
     * every round is a different (but reproducible) layout. The map seed is derived from the base seed
     * and the round index, then the index is advanced so the next round differs again.
     */
    public void resetRound() {
        for (int i = discs.size() - 1; i >= 0; i--) {
            combatSystem.retire(discs.get(i));
        }
        discs.clear();
        discOwners.clear();
        blockHits.clear();
        blockHitByTile.clear();
        for (int p = 0; p < playerCount; p++) {
            while (attack[p].activeDiscs() > 0) {
                attack[p].onDiscRetired();
            }
            chargeTicks[p] = 0;
            chargeConsumed[p] = false;
            charging[p] = false;
            shootHeldPrev[p] = false;
        }
        // New map for this round (also rebuilds collider/laser/bases and re-registers capture points,
        // and resets each player's aim to the neutral firing direction).
        buildMap(mapSeedFor(roundIndex));
        roundIndex++;
        matchFlow.resetRound();
    }

    // -- queries (read by the renderer) -------------------------------------

    public int playerCount() { return playerCount; }

    /** The master seed this world was built from (0 when built from an explicit {@link Random}). */
    public long seed() { return seed; }

    /** Live round/match score driver, the queryable contract for the render layer. */
    public MatchFlow matchFlow() { return matchFlow; }

    /** The game configuration (read by the render layer for geometry/colours). */
    public GameConfig config() { return config; }

    /** Tile/grid unit size in pixels. */
    public int unit() { return config.grid().unit(); }

    /** AWT colour for a 0-based player id, from the configured palette. */
    public java.awt.Color playerColor(int playerId) {
        return config.palette().playerColor(playerId + 1).toAwt();
    }

    public TileType[][] tiles() { return tiles; }

    public List<Entity> discs() { return discs; }

    /** Active block-hit flashes for the renderer to draw over wall tiles. */
    public List<BlockHitEffect> blockHits() { return blockHits; }

    public CaptureScoring scoring() { return scoring; }

    /** The collision grid (tile-content queries). */
    public SpatialCollider collider() { return collider; }

    /** The analytic path tracer (read by the AI's reachability walk so it uses the live reflection math). */
    public GridPathTracer tracer() { return tracer; }

    public AimController aimOf(int playerId) { return aim[playerId]; }

    public BasePlacement baseOf(int playerId) { return bases[playerId]; }

    /** Number of this player's discs currently in flight. */
    public int activeDiscs(int playerId) { return attack[playerId].activeDiscs(); }

    /** Total active discs across all players. */
    public int totalActiveDiscs() {
        int n = 0;
        for (int p = 0; p < playerCount; p++) {
            n += attack[p].activeDiscs();
        }
        return n;
    }

    // -- internals ----------------------------------------------------------

       /** Starts (or restarts in place) the hit flash on a wall tile in the disc owner's colour. */
    private void spawnBlockHit(int tileX, int tileY, int ownerId) {
        long key = (((long) tileX) << 32) ^ (tileY & 0xFFFFFFFFL);
        BlockHitEffect existing = blockHitByTile.get(key);
        if (existing != null) {
            existing.restart(ownerId); // same tile hit again -> retrigger the flash, no allocation
            return;
        }
        var effect = new BlockHitEffect(tileX, tileY, ownerId);
        blockHits.add(effect);
        blockHitByTile.put(key, effect);
    }

    /** Advances every active block-hit flash one tick and prunes finished ones (index loop, no iterator). */
    private void advanceBlockHits() {
        for (int i = blockHits.size() - 1; i >= 0; i--) {
            BlockHitEffect effect = blockHits.get(i);
            effect.advance();
            if (effect.isDone()) {
                long key = (((long) effect.tileX()) << 32) ^ (effect.tileY() & 0xFFFFFFFFL);
                blockHitByTile.remove(key);
                blockHits.remove(i);
            }
        }
    }

    private void registerCapturePoints() {
        for (int i = 0; i < tiles.length; i++) {
            for (int j = 0; j < tiles[i].length; j++) {
                if (tiles[i][j] == TileType.CAPTURE_POINT) {
                    scoring.register(i, j);
                }
            }
        }
    }

    /**
     * Maps each {@code playerId} to its FIXED base by player id, NOT by map scan order: player N always
     * uses {@link MapGenerator#baseCentre(int)} (P0 bottom, P1 top, P2 left, P3 right) and the matching
     * {@link #SHOOT_DIRECTIONS} entry. This keeps each player's spawn side and neutral aim (pointing at
     * the map centre) identical regardless of how many players are in the match.
     *
     * <p>Convention: entity X = first tile index, Y = second; the collider indexes
     * {@code tiles[getX()/unit][getY()/unit]} and walls draw {@code tiles[i][j]} at pixel
     * {@code (i*unit, j*unit)}, so the spawn/laser origin is the CENTRE of the base tile.
     */
    private BasePlacement[] locateBases(int unit) {
        var result = new BasePlacement[playerCount];
        for (int p = 0; p < playerCount; p++) {
            int[] centre = MapGenerator.baseCentre(p);
            int tileX = centre[0];
            int tileY = centre[1];
            result[p] = new BasePlacement(p, tileX, tileY,
                    tileX * unit + unit / 2, tileY * unit + unit / 2,
                    SHOOT_DIRECTIONS[p]);
        }
        return result;
    }
}
