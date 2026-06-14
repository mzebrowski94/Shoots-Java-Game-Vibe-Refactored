
package pl.mzebrows.shoots.game.logic;

/**
 * Enum oznaczajacy stany menu gry.
 * Oznacza która opcja menu jest akutalnie wybrana
 * @author Mateusz Żebrowski, Nr albumu: 95281
 */
public enum MenuEnum {

    /**
     * OPCJA - Rozpoczącie nowej gry
     */
    START_NEW_GAME,

    /**
     * OPCJA - Kontynuuj grę
     */
    CONTINUE,

    /**
     * OPCJA - Zakończ grę
     */
    QUIT,

    /**
     * OPCJA - Wybór ilości graczy
     */
    PLAYER_NUMBER_OPTION,

    /**
     * OPCJA  - Wybór ilości rund
     */
    ROUND_NUMBER_OPTION,

    /**
     * OPCJA - Wybór czasu trwana rundy
     */
    ROUND_TIME_OPTION,

    /**
     * OPCJA - Nie wybrano żadnej opcji
     */
    NO_OPTION;
    }