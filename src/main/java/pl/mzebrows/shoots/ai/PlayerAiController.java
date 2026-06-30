// src/main/java/pl/mzebrows/shoots/ai/PlayerAiController.java
package pl.mzebrows.shoots.ai;

import java.util.Random;

import pl.mzebrows.shoots.entity.AimController;
import pl.mzebrows.shoots.score.CapturePoint;
import pl.mzebrows.shoots.world.PlayWorld;

/**
 * One computer-controlled player. It is "just another input source": every tick it inspects the world
 * and drives the SAME {@link PlayWorld#applyInput} / {@link PlayWorld#fire} API a human uses, so no
 * physics is special-cased.
 *
 * <p>Decision making is utility-style and cheap. On a difficulty-scaled interval it scans a fixed set
 * of candidate firing angles across the aim arc (via {@link AiTargeting}, reusing the live bounce
 * math), scores the capture points those angles can reach by the active {@link AiSkills} knobs, and
 * caches the best target angle. Every frame in between it merely nudges its aim toward that cached
 * angle ({@code cursorSpeedFactor}) and fires when roughly aligned -- subject to accuracy perturbation,
 * volley pacing, and the disc caps. All randomness comes from a seeded {@link Random}, so a given seed
 * reproduces the AI's play.
 *
 * <p>Hard rule (not a knob): candidate angles whose first reflection lands on one of this base's own
 * flanking blocks are discarded -- shooting them is pointless.
 */
public final class PlayerAiController {

    /** Below this absolute angle error (degrees) the AI considers itself aligned and may fire. */
    private static final double ALIGN_TOLERANCE_DEG = 3.0;

    /** Worst-case aim error (degrees) a 0-accuracy AI may introduce; scaled down by accuracy. */
    private static final double MAX_AIM_ERROR_DEG = 18.0;

    private final int playerId;
    private final AiSkills skills;
    private final AiTargeting targeting;
    private final int scanAngles;
    private final Random rng;

    // Cached decision state between re-evaluations.
    private double targetAngle;
    private boolean hasTarget;
    /** Bounce-path length of the cached target; drives the "long range -> power shot" decision. */
    private int targetBounces = 0;
    /** Whether the cached target is an opponent base (disruption shot) rather than a capture point. */
    private boolean targetIsBase = false;

    private int decisionCountdown;
    private int cooldownCountdown;
    private int volleyRemaining;

    public PlayerAiController(int playerId, AiSkills skills, AiTargeting targeting, int scanAngles, long seed) {
        if (scanAngles < 2) {
            throw new IllegalArgumentException("scanAngles must be >= 2: " + scanAngles);
        }
        this.playerId = playerId;
        this.skills = skills;
        this.targeting = targeting;
        this.scanAngles = scanAngles;
        this.rng = new Random(seed ^ (0x2545F4914F6CDD1DL * (playerId + 1)));
        this.targetAngle = Double.NaN;
    }

    public int playerId() {
        return playerId;
    }

    public AiSkills skills() {
        return skills;
    }

    /**
     * Offsets the first decision tick so multiple AIs don't re-evaluate on the same frame; clamped to
     * the decision interval. Call once right after construction.
     */
    public void primeDecisionOffset(int ticks) {
        this.decisionCountdown = Math.floorMod(ticks, Math.max(1, skills.decisionIntervalTicks()));
    }

    /** Advances this AI by one fixed step against {@code world}, issuing aim + shoot input. */
    public void think(PlayWorld world) {
        if (decisionCountdown > 0) {
            decisionCountdown--;
        } else {
            chooseTarget(world);
            decisionCountdown = skills.decisionIntervalTicks();
        }
        if (cooldownCountdown > 0) {
            cooldownCountdown--;
        }
        act(world);
    }

    // -- decision -----------------------------------------------------------

    private void chooseTarget(PlayWorld world) {
        var base = world.baseOf(playerId);
        AimController aim = world.aimOf(playerId);
        double speed = world.config().disc().moveSpeed();
        double centre = aim.getShootDirection();
        double limit = aim.getRotationLimit();

        double bestScore = Double.NEGATIVE_INFINITY;
        double bestAngle = Double.NaN;
        int bestBounces = 0;
        boolean bestIsBase = false;
        boolean found = false;

        // Base targeting (the AI aggressiveness behaviour) folds into the same scan: when enabled, an
        // angle that reaches a disruptable opponent base becomes a candidate too, scored by the
        // baseAttackTendency knob. When disabled, bases are passed through and only capture points score.
        boolean baseAttack = baseAttackEnabled(world);

        for (int i = 0; i < scanAngles; i++) {
            double frac = scanAngles == 1 ? 0.5 : (double) i / (scanAngles - 1);
            double angle = centre - limit + frac * (2 * limit);

            // Flank-block filter: skip angles whose first reflection is an own-flank wall tile next to the base.
            if (isOwnFlankFirstHit(world, base, angle, speed)) {
                continue;
            }

            var reach = baseAttack
                    ? targeting.reachIncludingBases(base.pixelX(), base.pixelY(), angle, speed)
                    : targeting.reach(base.pixelX(), base.pixelY(), angle, speed);
            if (!reach.reached()) {
                continue;
            }

            double score;
            if (reach.base()) {
                // Only a DISRUPTABLE opponent base is worth a shot (not our own, not one already immune
                // or disrupted), and only if the disc can actually COMPLETE the path: a disc is retired
                // the instant its bounce budget is spent, so a base needing the full budget to reach is
                // never struck live -- require strictly fewer bounces than the disc's budget.
                if (reach.bounces() >= world.config().disc().maxBounces()) {
                    continue;
                }
                if (!isDisruptableOpponentBase(world, reach.tileX(), reach.tileY())) {
                    continue;
                }
                score = scoreBase(reach.bounces());
            } else {
                CapturePoint point = world.scoring().at(reach.tileX(), reach.tileY());
                if (point == null) {
                    continue;
                }
                score = scorePoint(point, reach.bounces());
            }

            if (score > bestScore) {
                bestScore = score;
                bestAngle = angle;
                bestBounces = reach.bounces();
                bestIsBase = reach.base();
                found = true;
            }
        }

        if (found) {
            // Accuracy as a REAL miss: perturb the aim by a Gaussian scaled by (1 - accuracy), so a
            // low-accuracy AI genuinely sends discs off-target rather than just firing less often.
            double error = rng.nextGaussian() * (1.0 - skills.accuracy()) * MAX_AIM_ERROR_DEG;
            this.targetAngle = bestAngle + error;
            this.targetBounces = bestBounces;
            this.targetIsBase = bestIsBase;
            this.hasTarget = true;
        } else if (!skipsRetainingTarget()) {
            // keep the previous target (stubbornness) if we found nothing new this scan
            this.hasTarget = !Double.isNaN(targetAngle);
        } else {
            this.hasTarget = false;
        }
    }

    private boolean skipsRetainingTarget() {
        // A non-stubborn AI drops a stale target; a stubborn one keeps retrying it.
        return rng.nextDouble() > skills.retakeStubbornness();
    }

    /** Utility score for a reachable capture point, blending value, defend/contest bias, and path cost. */
    private double scorePoint(CapturePoint point, int bounces) {
        double score = 0.0;
        boolean mine = point.isCaptured() && point.getOwnerId() == playerId;
        boolean enemy = point.isCaptured() && point.getOwnerId() != playerId;

        // Base value by target mode.
        switch (skills.targetMode()) {
            case NEAREST -> score += 10.0;
            case HIGHEST_VALUE -> score += (CapturePoint.MAX_LEVEL + 1)
                    - (point.isCaptured() ? point.getLevel() : 0);
            case CONTESTED -> score += enemy ? 10.0 : 4.0;
        }
        // Defend tendency: an owned point being eroded (level dropped below max) is worth defending.
        if (mine && point.getLevel() < CapturePoint.MAX_LEVEL) {
            score += skills.defendTendency() * 8.0;
        }
        // Contesting an enemy point scales with stubbornness/contest preference.
        if (enemy) {
            score += skills.retakeStubbornness() * 6.0;
        }
        // Path-length cost: prefer short paths unless the AI likes trick shots.
        double bouncePenalty = (1.0 - skills.bouncePathPreference()) * bounces;
        score -= bouncePenalty;
        return score;
    }

    /**
     * Utility score for disrupting an opponent base. Scales with the AI's {@code baseAttackTendency} so
     * only aggressive AIs prefer a disruption shot over a capture point; still discounts long paths.
     */
    private double scoreBase(int bounces) {
        double aggression = skills.baseAttackTendency();
        if (aggression <= 0.0) {
            return Double.NEGATIVE_INFINITY; // toggle/knob off -> never pick a base
        }
        double score = aggression * 14.0; // a worthwhile disruption competes with a good capture point
        score -= (1.0 - skills.bouncePathPreference()) * bounces;
        return score;
    }

    /** Whether base-attack is allowed this match (config master switch + the per-AI tendency knob). */
    private boolean baseAttackEnabled(PlayWorld world) {
        return world.config().ai().baseAttackEnabled() && skills.baseAttackTendency() > 0.0;
    }

    /**
     * Whether the tile ({@code tileX},{@code tileY}) is an opponent base that can be disrupted right now:
     * it must belong to a DIFFERENT player and that player must not already be disrupted or immune.
     */
    private boolean isDisruptableOpponentBase(PlayWorld world, int tileX, int tileY) {
        for (int p = 0; p < world.playerCount(); p++) {
            if (p == playerId) {
                continue;
            }
            var b = world.baseOf(p);
            if (b.tileX() == tileX && b.tileY() == tileY) {
                return !world.isDisrupted(p) && !world.isImmune(p);
            }
        }
        return false;
    }

    // -- execution ----------------------------------------------------------

    private void act(PlayWorld world) {
        if (!hasTarget || Double.isNaN(targetAngle)) {
            world.applyInput(playerId, PlayWorld.AimInput.NONE, false);
            return;
        }
        AimController aim = world.aimOf(playerId);
        double current = aim.currentAngle();
        double error = targetAngle - current;

        PlayWorld.AimInput move = PlayWorld.AimInput.NONE;
        // cursorSpeedFactor gates how often we actually rotate (slower AIs lag toward the target).
        boolean mayRotate = rng.nextDouble() < Math.max(0.05, skills.cursorSpeedFactor());
        if (Math.abs(error) > ALIGN_TOLERANCE_DEG && mayRotate) {
            move = error > 0 ? PlayWorld.AimInput.LEFT : PlayWorld.AimInput.RIGHT;
        }

        boolean shoot = false;
        if (Math.abs(error) <= ALIGN_TOLERANCE_DEG && cooldownCountdown == 0
                && world.activeDiscs(playerId) < skills.maxDiscsInFlight()) {
            if (volleyRemaining <= 0) {
                volleyRemaining = skills.maxDiscsPerShot();
            }
            shoot = true; // accuracy is already baked into targetAngle as an aim error
            volleyRemaining--;
            if (volleyRemaining <= 0) {
                cooldownCountdown = skills.volleyCooldownTicks();
            }
        }

        // Aim is applied via applyInput; firing is issued explicitly so a long-range shot can be a
        // charged power disc (AI bypasses the human hold-to-charge UX and fires it directly).
        world.applyInput(playerId, move, false);
        if (shoot) {
            if (wantsPowerShot(world)) {
                world.firePower(playerId);
            } else {
                world.fire(playerId);
            }
        }
    }

    /**
     * Whether the current shot should be a charged power disc: only for "long range" targets (bounce
     * path at least {@code ai.powerShotMinBounces}) and gated by the per-AI {@code powerShotTendency},
     * so stronger difficulties fire power shots on distant targets more often.
     */
    private boolean wantsPowerShot(PlayWorld world) {
        if (targetIsBase) {
            return false; // a disruption shot gains nothing from power capture strength
        }
        if (!world.config().power().enabled() || !world.config().ai().powerShotEnabled()) {
            return false;
        }
        if (targetBounces < world.config().ai().powerShotMinBounces()) {
            return false;
        }
        return rng.nextDouble() < skills.powerShotTendency();
    }

    /** First-reflection-is-own-flank test used to honour the "never shoot flanking blocks" rule. */
    private boolean isOwnFlankFirstHit(PlayWorld world, PlayWorld.BasePlacement base, double angle, double speed) {
        long wall = targeting.firstWallTile(base.pixelX(), base.pixelY(), angle, speed);
        // Own flank blocks sit 2 tiles to each side of the base, perpendicular to the firing lane.
        for (long flank : ownFlankTiles(base)) {
            if (wall == flank) {
                return true;
            }
        }
        return false;
    }

    private long[] ownFlankTiles(PlayWorld.BasePlacement base) {
        int cx = base.tileX();
        int cy = base.tileY();
        int dir = base.shootDirection();
        // Firing horizontally (dir 0 or 180) -> flanks are above/below; vertically -> flanks are left/right.
        boolean horizontal = dir == 0 || dir == 180;
        if (horizontal) {
            return new long[]{
                    AiTargeting.packTile(cx, cy - 2),
                    AiTargeting.packTile(cx, cy + 2)};
        }
        return new long[]{
                AiTargeting.packTile(cx - 2, cy),
                AiTargeting.packTile(cx + 2, cy)};
    }
}
