// src/main/java/pl/mzebrows/shoots/ai/AiSkillsFactory.java
package pl.mzebrows.shoots.ai;

import java.util.Random;

/**
 * Derives a deterministic {@link AiSkills} bundle from {@code (difficulty, seed, playerId)}.
 *
 * <p>For the EASY..VERY_HARD ladder each normalised knob is interpolated from a weak end to a strong
 * end by the difficulty's {@link AiDifficulty#ladderFraction()}, then nudged by a small per-AI
 * deviation so sibling AIs of the same level differ slightly. For {@link AiDifficulty#RANDOM} every
 * knob is drawn across a wide band instead. All randomness comes from a {@link Random} seeded on
 * {@code seed} mixed with {@code playerId}, so the same inputs always yield the same skills (the C0
 * reproducibility guarantee), while different players get independent draws.
 *
 * <p>The weak/strong endpoints below are the built-in defaults; cluster C5 will let
 * {@code game.properties} override them (NORMAL intensities + per-level deviation). Counts/ticks are
 * conservative so even VERY_HARD stays cheap and never dumps its whole disc cap at once.
 */
public final class AiSkillsFactory {

    // Normalised knob endpoints: value at EASY (weak) and at VERY_HARD (strong).
    private static final double ACCURACY_WEAK = 0.45, ACCURACY_STRONG = 0.99;
    private static final double CURSOR_WEAK = 0.30, CURSOR_STRONG = 1.00;
    private static final double RETAKE_WEAK = 0.20, RETAKE_STRONG = 0.85;
    private static final double DEFEND_WEAK = 0.15, DEFEND_STRONG = 0.90;
    private static final double BOUNCE_WEAK = 0.10, BOUNCE_STRONG = 0.95;

    // Tick intervals: weak (EASY) is slow/long, strong (VERY_HARD) is fast/short.
    private static final int DECISION_WEAK = 45, DECISION_STRONG = 10;   // ticks between target re-evals
    private static final int COOLDOWN_WEAK = 40, COOLDOWN_STRONG = 12;   // ticks between shots

    // Disc usage: how many concurrent discs / discs per volley the level is willing to use.
    private static final int DISCS_IN_FLIGHT_WEAK = 1, DISCS_IN_FLIGHT_STRONG = 3;
    private static final int DISCS_PER_SHOT_WEAK = 1, DISCS_PER_SHOT_STRONG = 2;

    /** Magnitude of the per-AI deviation applied to normalised ladder knobs (+/- this, clamped to [0,1]). */
    private static final double LADDER_DEVIATION = 0.08;

    private AiSkillsFactory() { }

    /** Builds the skills for one AI player from the match seed and its player slot. */
    public static AiSkills create(AiDifficulty difficulty, long seed, int playerId) {
        var rng = new Random(mix(seed, playerId));
        return difficulty == AiDifficulty.RANDOM ? randomBand(rng) : ladder(difficulty, rng);
    }

    private static AiSkills ladder(AiDifficulty difficulty, Random rng) {
        double f = difficulty.ladderFraction(); // 0 (EASY) .. 1 (VERY_HARD)
        return new AiSkills(
                deviate(lerp(ACCURACY_WEAK, ACCURACY_STRONG, f), rng),
                deviate(lerp(CURSOR_WEAK, CURSOR_STRONG, f), rng),
                lerpInt(DISCS_IN_FLIGHT_WEAK, DISCS_IN_FLIGHT_STRONG, f),
                lerpInt(DISCS_PER_SHOT_WEAK, DISCS_PER_SHOT_STRONG, f),
                deviate(lerp(RETAKE_WEAK, RETAKE_STRONG, f), rng),
                deviate(lerp(DEFEND_WEAK, DEFEND_STRONG, f), rng),
                deviate(lerp(BOUNCE_WEAK, BOUNCE_STRONG, f), rng),
                lerpInt(DECISION_WEAK, DECISION_STRONG, f),
                lerpInt(COOLDOWN_WEAK, COOLDOWN_STRONG, f),
                modeForLadder(f));
    }

    private static AiSkills randomBand(Random rng) {
        return new AiSkills(
                band(rng, 0.30, 1.00),   // accuracy: never hopeless, up to near-perfect
                band(rng, 0.20, 1.00),   // cursorSpeedFactor
                1 + rng.nextInt(DISCS_IN_FLIGHT_STRONG), // 1..3
                1 + rng.nextInt(DISCS_PER_SHOT_STRONG),  // 1..2
                band(rng, 0.00, 1.00),   // retakeStubbornness
                band(rng, 0.00, 1.00),   // defendTendency
                band(rng, 0.00, 1.00),   // bouncePathPreference
                DECISION_STRONG + rng.nextInt(DECISION_WEAK - DECISION_STRONG + 1),
                COOLDOWN_STRONG + rng.nextInt(COOLDOWN_WEAK - COOLDOWN_STRONG + 1),
                TargetMode.values()[rng.nextInt(TargetMode.values().length)]);
    }

    private static TargetMode modeForLadder(double f) {
        // Weaker AIs go for the nearest/easiest point; stronger AIs prioritise value, then contest.
        if (f < 1.0 / 3.0 - 1e-9) {
            return TargetMode.NEAREST;
        }
        if (f < 2.0 / 3.0 - 1e-9) {
            return TargetMode.HIGHEST_VALUE;
        }
        return TargetMode.CONTESTED;
    }

    private static double lerp(double a, double b, double f) {
        return a + (b - a) * f;
    }

    private static int lerpInt(int a, int b, double f) {
        return (int) Math.round(a + (b - a) * f);
    }

    private static double deviate(double base, Random rng) {
        double v = base + (rng.nextDouble() * 2.0 - 1.0) * LADDER_DEVIATION;
        return clampUnit(v);
    }

    private static double band(Random rng, double lo, double hi) {
        return clampUnit(lo + rng.nextDouble() * (hi - lo));
    }

    private static double clampUnit(double v) {
        if (v < 0.0) {
            return 0.0;
        }
        return Math.min(v, 1.0);
    }

    /** Mixes the match seed with the player slot so each AI gets an independent, reproducible stream. */
    private static long mix(long seed, int playerId) {
        long h = seed ^ (0x9E3779B97F4A7C15L * (playerId + 1));
        h ^= (h >>> 30);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 27);
        return h;
    }
}
