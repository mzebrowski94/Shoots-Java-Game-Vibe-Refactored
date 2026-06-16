// src/test/java/pl/mzebrows/shoots/ui/GameMenuAiOptionsTest.java
package pl.mzebrows.shoots.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Font;
import java.awt.event.KeyEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.mzebrows.shoots.ai.AiDifficulty;
import pl.mzebrows.shoots.app.GameSettings;
import pl.mzebrows.shoots.input.InputBridge;

/** Navigation + value tests for the AI-player count and AI-difficulty menu options (logic only). */
class GameMenuAiOptionsTest {

    private GameMenu menu;
    private GameSettings settings;
    private InputBridge input;

    @BeforeEach
    void setUp() {
        settings = new GameSettings();
        if (settings.getMenuFont() == null) {
            settings.setMenuFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        }
        menu = new GameMenu(settings);
        input = InputBridge.withDefaultKeyMap();
    }

    private static KeyEvent key(int code) {
        var e = mock(KeyEvent.class);
        when(e.getKeyCode()).thenReturn(code);
        return e;
    }

    private void press(int code) {
        input.keyPressed(key(code));
        input.poll();
        menu.checkMenuInput(input);
        input.keyReleased(key(code));
        input.poll();
    }

    /** Navigate from the initial START_NEW_GAME down to the AI_NUMBER option (4 DOWN presses). */
    private void gotoAiNumber() {
        for (int i = 0; i < 4; i++) {
            press(KeyEvent.VK_DOWN);
        }
        assertThat(menu.getMenuOption()).isEqualTo(MenuEnum.AI_NUMBER_OPTION);
    }

    @Test
    void navigatesToBothAiOptionsInOrder() {
        gotoAiNumber();
        press(KeyEvent.VK_DOWN);
        assertThat(menu.getMenuOption()).isEqualTo(MenuEnum.AI_DIFFICULTY_OPTION);
        press(KeyEvent.VK_DOWN);
        assertThat(menu.getMenuOption()).isEqualTo(MenuEnum.CONTROLS);
    }

    @Test
    void aiNumberIncrementsAndIsClampedToPlayerCount() {
        gotoAiNumber();
        // Default playerNumber is 2, so AI count maxes at 2.
        press(KeyEvent.VK_RIGHT);
        assertThat(menu.getAiNumber()).isEqualTo(1);
        press(KeyEvent.VK_RIGHT);
        assertThat(menu.getAiNumber()).isEqualTo(2);
        // Wraps back to 0 at the top.
        press(KeyEvent.VK_RIGHT);
        assertThat(menu.getAiNumber()).isEqualTo(0);
    }

    @Test
    void aiDifficultyCyclesThroughAllLevels() {
        gotoAiNumber();
        press(KeyEvent.VK_DOWN); // AI_DIFFICULTY
        assertThat(menu.getAiDifficulty()).isEqualTo(AiDifficulty.NORMAL);

        press(KeyEvent.VK_RIGHT);
        assertThat(menu.getAiDifficulty()).isEqualTo(AiDifficulty.HARD);
        press(KeyEvent.VK_RIGHT);
        assertThat(menu.getAiDifficulty()).isEqualTo(AiDifficulty.VERY_HARD);
        press(KeyEvent.VK_RIGHT); // wraps to first
        assertThat(menu.getAiDifficulty()).isEqualTo(AiDifficulty.RANDOM);
        press(KeyEvent.VK_LEFT); // back to last
        assertThat(menu.getAiDifficulty()).isEqualTo(AiDifficulty.VERY_HARD);
    }

    @Test
    void confirmingStartNewGamePushesAiSettingsIntoGameSettings() {
        gotoAiNumber();
        press(KeyEvent.VK_RIGHT); // aiNumber -> 1
        press(KeyEvent.VK_DOWN);  // AI_DIFFICULTY
        press(KeyEvent.VK_RIGHT); // NORMAL -> HARD

        // Go back up to START_NEW_GAME and confirm.
        for (int i = 0; i < 5; i++) {
            press(KeyEvent.VK_UP);
        }
        assertThat(menu.getMenuOption()).isEqualTo(MenuEnum.START_NEW_GAME);
        press(KeyEvent.VK_ENTER);

        assertThat(settings.getAiNumber()).isEqualTo(1);
        assertThat(settings.getAiDifficulty()).isEqualTo(AiDifficulty.HARD);
    }

    @Test
    void aiCountIsClampedWhenPushedEvenIfPlayerCountIsLowered() {
        gotoAiNumber();
        press(KeyEvent.VK_RIGHT);
        press(KeyEvent.VK_RIGHT); // aiNumber -> 2 (player count 2)

        for (int i = 0; i < 4; i++) {
            press(KeyEvent.VK_UP);
        }
        assertThat(menu.getMenuOption()).isEqualTo(MenuEnum.START_NEW_GAME);
        press(KeyEvent.VK_ENTER);

        assertThat(settings.getAiNumber()).isLessThanOrEqualTo(settings.getPlayerNumber());
    }
}
