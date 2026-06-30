// src/test/java/pl/mzebrows/shoots/ui/GameMenuControlsTest.java
package pl.mzebrows.shoots.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Font;
import java.awt.event.KeyEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.mzebrows.shoots.input.InputBridge;

/**
 * Navigation tests for the new CONTROLS menu option and its overlay screen toggle. Pure logic via
 * {@code checkMenuInput} -- no Graphics2D involved.
 */
class GameMenuControlsTest {

    private GameMenu menu;
    private InputBridge input;

    @BeforeEach
    void setUp() {
        var settings = new GameSettings();
        // The bundled TTFs may not load in a headless/CI run, leaving the menu font null; GameMenu's
        // constructor derives from it, so guarantee a non-null font for this logic-only test.
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

    /** Presses and releases a key across two polls so isJustPressed fires once, then drives the menu. */
    private MenuEnum press(int code) {
        input.keyPressed(key(code));
        input.poll();
        MenuEnum result = menu.checkMenuInput(input);
        input.keyReleased(key(code));
        input.poll();
        return result;
    }

    @Test
    void confirmingControlsOpensThePanel() {
        // START_NEW_GAME -> PLAY_ONLINE -> GAMEPLAY_OPTIONS -> PLAYER -> AI_NUMBER -> AI_DIFFICULTY -> CONTROLS
        for (int i = 0; i < 6; i++) {
            press(KeyEvent.VK_DOWN);
        }
        MenuEnum chosen = press(KeyEvent.VK_ENTER);
        assertThat(chosen).isEqualTo(MenuEnum.CONTROLS);
        assertThat(menu.isShowingControls()).isTrue();
    }

    @Test
    void enterReturnsFromTheControlsPanel() {
        for (int i = 0; i < 6; i++) {
            press(KeyEvent.VK_DOWN);
        }
        press(KeyEvent.VK_ENTER);            // open controls
        assertThat(menu.isShowingControls()).isTrue();

        MenuEnum whileOpen = press(KeyEvent.VK_ENTER); // ENTER returns
        assertThat(whileOpen).isEqualTo(MenuEnum.NO_OPTION);
        assertThat(menu.isShowingControls()).isFalse();
    }

    @Test
    void escapeAlsoClosesTheControlsPanel() {
        for (int i = 0; i < 6; i++) {
            press(KeyEvent.VK_DOWN);
        }
        press(KeyEvent.VK_ENTER);            // open controls
        assertThat(menu.isShowingControls()).isTrue();

        press(KeyEvent.VK_ESCAPE);           // ESC returns
        assertThat(menu.isShowingControls()).isFalse();
    }

    @Test
    void whileControlsOpenMenuNavigationIsSwallowed() {
        for (int i = 0; i < 6; i++) {
            press(KeyEvent.VK_DOWN);
        }
        press(KeyEvent.VK_ENTER);            // open controls
        MenuEnum chosen = press(KeyEvent.VK_DOWN);
        assertThat(chosen).isEqualTo(MenuEnum.NO_OPTION);
        assertThat(menu.isShowingControls()).isTrue();
    }
}
