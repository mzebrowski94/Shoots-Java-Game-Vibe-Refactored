// src/main/java/pl/mzebrows/shoots/ai/AiDifficulty.java
package pl.mzebrows.shoots.ai;

import lombok.Getter;

/**
 * Selectable AI difficulty levels. {@code EASY..VERY_HARD} form a monotonic skill ladder; the seed
 * adds a small per-AI deviation within a level (see {@code AiSkillsFactory}). {@code RANDOM} is not a
 * rung on that ladder: each knob is drawn across a wide band (deterministically from the seed), so a
 * RANDOM opponent is unpredictable but still reproducible for a given seed.
 */
@Getter
public enum AiDifficulty {

    /** Every knob randomized within a wide band (seed-deterministic); unpredictable, not a skill rung. */
    RANDOM("RANDOM"),

    /** Weakest ladder rung: low accuracy, slow cursor, rare trick shots, long reaction times. */
    EASY("EASY"),

    /** Baseline ladder rung; knob defaults come from {@code game.properties}. */
    NORMAL("NORMAL"),

    /** Strong ladder rung: high accuracy, fast cursor, willing trick shots, short reaction times. */
    HARD("HARD"),

    /** Strongest ladder rung: near-perfect aim, fastest cursor, full trick-shot range, fastest reactions. */
    VERY_HARD("VERY HARD");

    /** Human-readable label shown in the menu. */
    private final String displayName;

    AiDifficulty(String displayName) {
        this.displayName = displayName;
    }

    /** Position on the EASY..VERY_HARD ladder in [0,1]; {@code RANDOM} reports {@code -1} (off-ladder). */
    public double ladderFraction() {
        return switch (this) {
            case RANDOM -> -1.0;
            case EASY -> 0.0;
            case NORMAL -> 1.0 / 3.0;
            case HARD -> 2.0 / 3.0;
            case VERY_HARD -> 1.0;
        };
    }
}
