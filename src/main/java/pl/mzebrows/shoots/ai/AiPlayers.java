// src/main/java/pl/mzebrows/shoots/ai/AiPlayers.java
package pl.mzebrows.shoots.ai;

import java.util.ArrayList;
import java.util.List;

import pl.mzebrows.shoots.world.PlayWorld;

/**
 * Owns the {@link PlayerAiController}s for one match and steps them each frame. AI players occupy the
 * HIGHEST player slots so humans keep the low slots (e.g. 1 human + 2 AI in a 3-player game -> P1 is
 * human, P2/P3 are AI); the count is clamped to the world's player count.
 *
 * <p>Controllers self-throttle via their {@code decisionIntervalTicks}; this holder gives each AI a
 * different starting phase offset so their (re-)evaluations don't all land on the same tick, keeping the
 * worst-case per-frame scan cost flat with multiple AIs.
 */
public final class AiPlayers {

    private final List<PlayerAiController> controllers;

    private AiPlayers(List<PlayerAiController> controllers) {
        this.controllers = controllers;
    }

    /** No AI players (all-human match). */
    public static AiPlayers none() {
        return new AiPlayers(List.of());
    }

    /**
     * Builds controllers for the top {@code aiCount} slots of {@code world}, each with seed-derived
     * skills for {@code difficulty}. {@code scanAngles} is the candidate-angle scan resolution.
     */
    public static AiPlayers build(PlayWorld world, int aiCount, AiDifficulty difficulty, int scanAngles) {
        int players = world.playerCount();
        int clamped = Math.max(0, Math.min(aiCount, players));
        if (clamped == 0) {
            return none();
        }
        int firstAiSlot = players - clamped;
        long seed = world.seed();
        int maxBounces = world.config().disc().maxBounces();
        int maxPerShot = world.config().disc().maxPerShot();
        int maxPerPlayer = world.config().disc().maxPerPlayer();

        List<PlayerAiController> list = new ArrayList<>(clamped);
        for (int playerId = firstAiSlot; playerId < players; playerId++) {
            // Clamp the AI's disc usage to the configured caps so config remains the authority.
            AiSkills skills = clampDiscCaps(AiSkillsFactory.create(difficulty, seed, playerId),
                    maxPerPlayer, maxPerShot);
            var targeting = new AiTargeting(world.tracer(), maxBounces);
            var controller = new PlayerAiController(playerId, skills, targeting, scanAngles, seed);
            // Stagger first decisions across AIs so their scans don't all land on the same tick.
            controller.primeDecisionOffset((playerId - firstAiSlot) * 4);
            list.add(controller);
        }
        return new AiPlayers(list);
    }

    /** Whether any AI players are present. */
    public boolean isEmpty() {
        return controllers.isEmpty();
    }

    /** Number of AI players. */
    public int count() {
        return controllers.size();
    }

    /** Whether the given slot is controlled by an AI. */
    public boolean isAiSlot(int playerId) {
        for (PlayerAiController c : controllers) {
            if (c.playerId() == playerId) {
                return true;
            }
        }
        return false;
    }

    /** Drives every AI one tick against {@code world} (call before {@code world.step()}). */
    public void think(PlayWorld world) {
        for (int i = 0; i < controllers.size(); i++) {
            controllers.get(i).think(world);
        }
    }

    /** The controllers (read-only view for tests). */
    public List<PlayerAiController> controllers() {
        return List.copyOf(controllers);
    }

    /** Rebuilds skills with disc caps clamped to the world's configured limits. */
    private static AiSkills clampDiscCaps(AiSkills s, int maxPerPlayer, int maxPerShot) {
        int inFlight = Math.min(s.maxDiscsInFlight(), maxPerPlayer);
        int perShot = Math.min(s.maxDiscsPerShot(), Math.min(maxPerShot, inFlight));
        if (inFlight == s.maxDiscsInFlight() && perShot == s.maxDiscsPerShot()) {
            return s;
        }
        return new AiSkills(s.accuracy(), s.cursorSpeedFactor(), inFlight, perShot,
                s.retakeStubbornness(), s.defendTendency(), s.bouncePathPreference(),
                s.decisionIntervalTicks(), s.volleyCooldownTicks(), s.targetMode(), s.powerShotTendency());
    }
}

