// src/main/java/pl/mzebrows/shoots/config/AiConfig.java
package pl.mzebrows.shoots.config;

/**
 * Tunables for the computer-controlled players, externalised from {@code game.properties}.
 *
 * @param scanAngles          candidate firing angles scanned per AI decision (>=2; higher = smarter, costlier)
 * @param scanBudgetPerFrame  max AI decision scans allowed per frame across all AIs (>=1; caps worst-case cost)
 * @param skillsEnabled       master switch for the skill knobs; when false the AI plays at flat NORMAL skill
 * @param powerShotEnabled    whether AIs may use the charged power shot at all (gated further per skill knob)
 * @param powerShotMinBounces minimum bounce-path length for a target to count as "long range" and be worth a
 *                            power shot (>=0); short/direct shots stay normal
 */
public record AiConfig(int scanAngles, int scanBudgetPerFrame, boolean skillsEnabled,
                       boolean powerShotEnabled, int powerShotMinBounces) {

    public AiConfig {
        if (scanAngles < 2) {
            throw new IllegalArgumentException("scanAngles must be >= 2: " + scanAngles);
        }
        if (scanBudgetPerFrame < 1) {
            throw new IllegalArgumentException("scanBudgetPerFrame must be >= 1: " + scanBudgetPerFrame);
        }
        if (powerShotMinBounces < 0) {
            throw new IllegalArgumentException("powerShotMinBounces must be >= 0: " + powerShotMinBounces);
        }
    }

    /** Back-compatible constructor: power shots enabled for AI, long-range threshold of 2 bounces. */
    public AiConfig(int scanAngles, int scanBudgetPerFrame, boolean skillsEnabled) {
        this(scanAngles, scanBudgetPerFrame, skillsEnabled, true, 2);
    }
}
