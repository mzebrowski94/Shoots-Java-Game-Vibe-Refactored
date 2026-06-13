// pl/mzebrows/shoots/input/GameAction.java
package pl.mzebrows.shoots.input;

/** Semantic game actions mapped from physical key codes by {@link InputBridge}. */
public enum GameAction {
    P1_ROTATE_LEFT, P1_ROTATE_RIGHT, P1_SHOOT,
    P2_ROTATE_LEFT, P2_ROTATE_RIGHT, P2_SHOOT,
    P3_ROTATE_LEFT, P3_ROTATE_RIGHT, P3_SHOOT,
    P4_ROTATE_LEFT, P4_ROTATE_RIGHT, P4_SHOOT,
    /** Navigate a menu selection upward. Shared with {@link #P1_SHOOT} on VK_UP. */
    NAVIGATE_UP,
    NAVIGATE_DOWN,
    /** Navigate / change a value leftward. Shared with {@link #P1_ROTATE_LEFT} on VK_LEFT. */
    NAVIGATE_LEFT,
    /** Navigate / change a value rightward. Shared with {@link #P1_ROTATE_RIGHT} on VK_RIGHT. */
    NAVIGATE_RIGHT,
    CONFIRM,
    PAUSE
}
