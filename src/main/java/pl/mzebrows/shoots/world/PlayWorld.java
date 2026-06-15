// src/main/java/pl/mzebrows/shoots/world/PlayWorld.java
package pl.mzebrows.shoots.world;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import pl.mzebrows.shoots.config.GameConfig;
import pl.mzebrows.shoots.entity.AimController;
import pl.mzebrows.shoots.entity.BounceMovementStrategy;
import pl.mzebrows.shoots.entity.DiscAttackStrategy;
import pl.mzebrows.shoots.entity.Entity;
import pl.mzebrows.shoots.entity.EntitySpawner;
import pl.mzebrows.shoots.entity.LaserPredictor;
import pl.mzebrows.shoots.pool.ObjectPool;
import pl.mzebrows.shoots.score.CaptureScoring;
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
public final class PlayWorld {

    /** Per-player aim intent for a single step, decoupled from raw key codes. */
    public enum AimInput { NONE, LEFT, RIGHT }

    /** Fixed firing direction and pixel origin of a player's base, derived from the map. */
    public record BasePlacement(int playerId, int tileX, int tileY, int pixelX, int pixelY, int shootDirection) { }

    private static final int[] SHOOT_DIRECTIONS = {180, 0, -90, 90};

    private final GameConfig config;
    private final int playerCount;

    private final SpatialCollider collider;
    private final TileType[][] tiles;

    private final CombatSystem combatSystem;
    private final DiscSystem discSystem;
    private final CaptureScoring scoring = new CaptureScoring();
    private final MatchFlow matchFlow;
    private final LaserPredictor laserPredictor;

    private final ObjectPool<Entity> discPool;
    private final List<Entity> discs;

    /**
     * Disc-instance -> owning player, kept by identity. {@link CombatSystem#retire} returns the disc
     * to the pool (which resets its fields, wiping {@code ownerId}) before the retirement sink runs,
     * so the owner must be tracked here rather than read off the entity post-retire.
     */
    private final Map<Entity, Integer> discOwners = new IdentityHashMap<>();

    private final AimController[] aim;
    private final DiscAttackStrategy[] attack;
    private final BasePlacement[] bases;

    /** Reusable scratch source used only to feed {@link DiscAttackStrategy#attack}. */
    private final Entity fireSource = new Entity();

    private final DiscSystem.DiscEventSink sink = new DiscSystem.DiscEventSink() {
        @Override public void onCapturePointHit(Entity disc, int tileX, int tileY) {
            scoring.resolveHit(tileX, tileY, disc.getOwnerId(), disc.getBounces());
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
        @Override public Entity spawnDisc(double x, double y, double angle, int ownerId) {
            Entity disc = combatSystem.spawnDisc(x, y, angle, ownerId);
            if (disc != null) {
                discs.add(disc);
                discOwners.put(disc, ownerId);
            }
            return disc;
        }
    };

    /** Builds a world with a fresh, time-seeded map. */
    public PlayWorld(GameConfig config) {
        this(config, new Random());
    }

    /** Builds a world with a seedable {@link Random} so tests are deterministic. */
    public PlayWorld(GameConfig config, Random random) {
        this.config = config;
        this.playerCount = config.playerNumber();

        this.tiles = new MapGenerator(config.grid(), random).generate(playerCount);
        this.collider = new UniformGridCollider(tiles, config.grid(), config.collision());

        int unit = config.grid().unit();
        int maxDiscsPerPlayer = config.disc().maxPerPlayer();
        this.discPool = new ObjectPool<>(maxDiscsPerPlayer * playerCount, Entity::new, Entity::reset);
        this.discs = new ArrayList<>(discPool.capacity());
        this.combatSystem = new CombatSystem(discPool, config.disc());
        this.discSystem = new DiscSystem(collider, combatSystem);
        this.matchFlow = new MatchFlow(playerCount, config.round());
        this.laserPredictor = new LaserPredictor(collider, new BounceMovementStrategy());

        this.aim = new AimController[playerCount];
        this.attack = new DiscAttackStrategy[playerCount];
        this.bases = locateBases(unit);

        for (int p = 0; p < playerCount; p++) {
            aim[p] = new AimController(bases[p].shootDirection(), config.disc().bigRadius() * 5.0, 1.0);
            attack[p] = new DiscAttackStrategy(maxDiscsPerPlayer);
        }

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
        resetRound();
    }

    /** Fires one disc for {@code playerId} if under the per-player cap and the pool has room. */
    public boolean fire(int playerId) {
        BasePlacement base = bases[playerId];
        fireSource.reset();
        fireSource.setX(base.pixelX());
        fireSource.setY(base.pixelY());
        fireSource.setAngle(aim[playerId].currentAngle());
        fireSource.setOwnerId(playerId);
        return attack[playerId].attack(fireSource, trackingSpawner);
    }

    /** Predicts the laser polyline for {@code playerId} into caller-supplied arrays (no allocation). */
    public int predictLaser(int playerId, int[] xs, int[] ys) {
        BasePlacement base = bases[playerId];
        return laserPredictor.predict(base.pixelX(), base.pixelY(),
                aim[playerId].currentAngle(), config.disc().moveSpeed(), xs, ys);
    }

    /** Retires all in-flight discs and resets aim + capture state for a new round. */
    public void resetRound() {
        for (int i = discs.size() - 1; i >= 0; i--) {
            combatSystem.retire(discs.get(i));
        }
        discs.clear();
        discOwners.clear();
        for (int p = 0; p < playerCount; p++) {
            aim[p].reset();
            while (attack[p].activeDiscs() > 0) {
                attack[p].onDiscRetired();
            }
        }
        scoring.resetAll();
        matchFlow.resetRound();
    }

    // -- queries (read by the renderer) -------------------------------------

    public int playerCount() { return playerCount; }

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

    public CaptureScoring scoring() { return scoring; }

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

       private void registerCapturePoints() {
        for (int i = 0; i < tiles.length; i++) {
            for (int j = 0; j < tiles[i].length; j++) {
                if (tiles[i][j] == TileType.CAPTURE_POINT) {
                    scoring.register(i, j);
                }
            }
        }
    }

    private BasePlacement[] locateBases(int unit) {
        var result = new BasePlacement[playerCount];
        int found = 0;
        for (int i = 0; i < tiles.length && found < playerCount; i++) {
            for (int j = 0; j < tiles[i].length && found < playerCount; j++) {
                if (tiles[i][j] == TileType.PLAYER_BASE) {
                    result[found] = new BasePlacement(found, i, j, j * unit, i * unit,
                            SHOOT_DIRECTIONS[Math.min(found, SHOOT_DIRECTIONS.length - 1)]);
                    found++;
                }
            }
        }
        for (int p = found; p < playerCount; p++) {
            result[p] = new BasePlacement(p, 0, 0, 0, 0,
                    SHOOT_DIRECTIONS[Math.min(p, SHOOT_DIRECTIONS.length - 1)]);
        }
        return result;
    }
}
