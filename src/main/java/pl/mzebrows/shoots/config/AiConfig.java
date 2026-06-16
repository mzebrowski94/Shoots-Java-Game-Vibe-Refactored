// src/main/java/pl/mzebrows/shoots/config/AiConfig.java
package pl.mzebrows.shoots.config;

/**
 * Tunables for the computer-controlled players, externalised from {@code game.properties}.
 *
 * @param scanAngles          candidate firing angles scanned per AI decision (>=2; higher = smarter, costlier)
 * @param scanBudgetPerFrame  max AI decision scans allowed per frame across all AIs (>=1; caps worst-case cost)
 * @param skillsEnabled       master switch for the skill knobs; when false the AI plays at flat NORMAL skill
 */
public record AiConfig(int scanAngles, int scanBudgetPerFrame, boolean skillsEnabled) {

    public AiConfig {
        if (scanAngles < 2) {
            throw new IllegalArgumentException("scanAngles must be >= 2: " + scanAngles);
        }
        if (scanBudgetPerFrame < 1) {
            throw new IllegalArgumentException("scanBudgetPerFrame must be >= 1: " + scanBudgetPerFrame);
        }
    }
}
