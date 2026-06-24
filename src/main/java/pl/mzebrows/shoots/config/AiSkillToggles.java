// src/main/java/pl/mzebrows/shoots/config/AiSkillToggles.java
package pl.mzebrows.shoots.config;

/**
 * Per-skill on/off switches for the AI's behavioural knobs, read once from {@code game.properties} at
 * round start (the AI skill bundle is fixed for the match). A {@code false} switch neutralises that
 * single behaviour without disabling the whole AI: the corresponding knob is forced to its inert value
 * when the {@code AiSkills} bundle is built, so e.g. turning {@code baseAttack} off makes the AI ignore
 * opponent bases entirely while still playing for capture points.
 *
 * @param accuracy        whether aim accuracy is applied (off = max aim error / very loose aim)
 * @param cursorSpeed     whether the cursor-speed knob is applied (off = slowest rotation)
 * @param retake          whether retake stubbornness is applied (off = never insists on a failed target)
 * @param defend          whether the defend-owned tendency is applied (off = never prioritises defence)
 * @param bouncePath      whether the long/trick-shot preference is applied (off = direct shots only)
 * @param powerShot       whether the AI's power-shot tendency is applied (off = never charges power shots)
 * @param baseAttack      whether the AI targets opponents' bases to disrupt them (off = ignores bases)
 */
public record AiSkillToggles(boolean accuracy, boolean cursorSpeed, boolean retake,
                             boolean defend, boolean bouncePath, boolean powerShot, boolean baseAttack) {

    /** All behaviours enabled (the default when no switches are set in {@code game.properties}). */
    public static AiSkillToggles allEnabled() {
        return new AiSkillToggles(true, true, true, true, true, true, true);
    }
}
