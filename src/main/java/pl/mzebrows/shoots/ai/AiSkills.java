// src/main/java/pl/mzebrows/shoots/ai/AiSkills.java
package pl.mzebrows.shoots.ai;

/**
 * One AI player's resolved skill knobs for a match: an immutable bundle the {@code PlayerAiController}
 * (C2) reads to decide and execute shots. Produced by {@code AiSkillsFactory} from
 * {@code (difficulty, seed, playerId)} so the values are deterministic for a given seed.
 *
 * <p>Normalised knobs are in {@code [0,1]} (0 = weakest, 1 = strongest/most-inclined). Counts and tick
 * intervals are concrete: ticks are fixed-timestep steps, so smaller intervals mean a sharper AI.
 *
 * @param accuracy            aim quality in [0,1]; 1 = no perturbation, lower = wider random angle error
 * @param cursorSpeedFactor   rotation speed toward target in [0,1]; scales rotation steps applied per frame
 * @param maxDiscsInFlight    concurrent discs this AI keeps in play (clamped to the world disc cap by C2)
 * @param maxDiscsPerShot     discs released per volley (>=1; clamped to maxDiscsInFlight by C2)
 * @param retakeStubbornness  tendency in [0,1] to keep targeting a point it failed to take
 * @param defendTendency      tendency in [0,1] to react when one of its owned points is being eroded
 * @param bouncePathPreference willingness in [0,1] to attempt longer bounce/trick shots (0 = direct only)
 * @param decisionIntervalTicks ticks between target re-evaluations (>=1; smaller = faster reactions)
 * @param volleyCooldownTicks ticks between successive shots (>=0; paces firing so caps aren't dumped at once)
 * @param targetMode          how candidate capture points are ranked
 * @param powerShotTendency   likelihood in [0,1] of using a charged power shot on a long-range target
 *                            (0 = never; scales up the difficulty ladder so strong AIs use it more)
 * @param baseAttackTendency  inclination in [0,1] to target opponents' bases to disrupt them
 *                            (0 = never; scales up the difficulty ladder so strong AIs are more aggressive)
 */
public record AiSkills(
        double accuracy,
        double cursorSpeedFactor,
        int maxDiscsInFlight,
        int maxDiscsPerShot,
        double retakeStubbornness,
        double defendTendency,
        double bouncePathPreference,
        int decisionIntervalTicks,
        int volleyCooldownTicks,
        TargetMode targetMode,
        double powerShotTendency,
        double baseAttackTendency) {

    public AiSkills {
        requireUnit("accuracy", accuracy);
        requireUnit("cursorSpeedFactor", cursorSpeedFactor);
        requireUnit("retakeStubbornness", retakeStubbornness);
        requireUnit("defendTendency", defendTendency);
        requireUnit("bouncePathPreference", bouncePathPreference);
        requireUnit("powerShotTendency", powerShotTendency);
        requireUnit("baseAttackTendency", baseAttackTendency);
        if (maxDiscsInFlight < 1) {
            throw new IllegalArgumentException("maxDiscsInFlight must be >= 1: " + maxDiscsInFlight);
        }
        if (maxDiscsPerShot < 1) {
            throw new IllegalArgumentException("maxDiscsPerShot must be >= 1: " + maxDiscsPerShot);
        }
        if (decisionIntervalTicks < 1) {
            throw new IllegalArgumentException("decisionIntervalTicks must be >= 1: " + decisionIntervalTicks);
        }
        if (volleyCooldownTicks < 0) {
            throw new IllegalArgumentException("volleyCooldownTicks must be >= 0: " + volleyCooldownTicks);
        }
        if (targetMode == null) {
            throw new IllegalArgumentException("targetMode must not be null");
        }
    }

    private static void requireUnit(String name, double value) {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be in [0,1]: " + value);
        }
    }
}
