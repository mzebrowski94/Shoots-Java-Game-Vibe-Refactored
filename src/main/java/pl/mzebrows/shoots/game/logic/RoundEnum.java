
package pl.mzebrows.shoots.game.logic;

/**
 * Enum oznaczający poszczególne stany rundy
 * @author Mateusz Żebrowski, Nr albumu: 95281
 */
public enum RoundEnum {

    /**
     * Runda rozpoczyna się
     */
    ROUND_BEGIN,

    /**
     * Runda trwa
     */
    ROUND_CONTINUES,

    /**
     * Runda kończy się
     */
    ROUND_ENDS,

    /**
     * Runda została zapauzowana
     */
    ROUND_PAUSED;
}
