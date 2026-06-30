// src/test/java/pl/mzebrows/shoots/ui/GameMenuOnlineTest.java
package pl.mzebrows.shoots.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Font;
import java.awt.event.KeyEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import pl.mzebrows.shoots.input.InputBridge;

/**
 * F7a/F7b navigation tests for the PLAY ONLINE entry and its connect sub-screen. Pure logic via
 * {@code checkMenuInput} (no Graphics2D, no sockets opened -- HOST/JOIN are not confirmed here).
 */
class GameMenuOnlineTest {

    private GameMenu menu;
    private InputBridge input;

    @BeforeEach
    void setUp() {
        var settings = new GameSettings();
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

    private MenuEnum press(int code) {
        input.keyPressed(key(code));
        input.poll();
        MenuEnum result = menu.checkMenuInput(input);
        input.keyReleased(key(code));
        input.poll();
        return result;
    }

    @Test
    void playOnlineSitsDirectlyBelowStartNewGame() {
        // Menu opens on START_NEW_GAME; one DOWN reaches PLAY ONLINE.
        press(KeyEvent.VK_DOWN);
        assertThat(menu.getMenuOption()).isEqualTo(MenuEnum.PLAY_ONLINE);
    }

    @Test
    void confirmingPlayOnlineOpensTheConnectScreen() {
        press(KeyEvent.VK_DOWN);                 // -> PLAY_ONLINE
        MenuEnum chosen = press(KeyEvent.VK_ENTER);
        assertThat(chosen).isEqualTo(MenuEnum.PLAY_ONLINE);
        assertThat(menu.isOnline()).isTrue();
        assertThat(menu.getOnlineScreen()).isEqualTo(GameMenu.OnlineScreen.CONNECT_MENU);
    }

    @Test
    void connectScreenSwallowsNavigationAndEscapeReturnsToMainMenu() {
        press(KeyEvent.VK_DOWN);
        press(KeyEvent.VK_ENTER);                // open connect screen
        assertThat(menu.isOnline()).isTrue();

        // Navigation is swallowed (no main-menu option escapes while the overlay is open).
        MenuEnum swallowed = press(KeyEvent.VK_DOWN);
        assertThat(swallowed).isEqualTo(MenuEnum.NO_OPTION);
        assertThat(menu.connectIndex).isEqualTo(1); // moved HOST -> JOIN LAN within the sub-screen

        press(KeyEvent.VK_ESCAPE);               // ESC returns to the main menu
        assertThat(menu.isOnline()).isFalse();
        assertThat(menu.getOnlineScreen()).isEqualTo(GameMenu.OnlineScreen.NONE);
    }
    /** Advances the menu one tick with no key pressed (drives the online sub-screen's network polling). */
    private void idlePump() {
        input.poll();
        menu.checkMenuInput(input);
    }

    @Test
    @Timeout(30)
    void joinOnlineConnectFailureShowsErrorWithoutCrashing() throws Exception {
        press(KeyEvent.VK_DOWN);          // -> PLAY_ONLINE
        press(KeyEvent.VK_ENTER);         // open connect screen
        press(KeyEvent.VK_DOWN);          // HOST -> JOIN LAN
        press(KeyEvent.VK_DOWN);          // JOIN LAN -> JOIN ONLINE
        press(KeyEvent.VK_ENTER);         // start the background connect (nothing is listening -> refused)
        assertThat(menu.getOnlineScreen()).isEqualTo(GameMenu.OnlineScreen.JOIN_ONLINE_SEARCH);

        // Pump idle frames until the connect resolves; this must NOT throw (regression: NPE on null joiner).
        long deadline = System.nanoTime() + 20_000L * 1_000_000L;
        while (menu.onlineError() == null && System.nanoTime() < deadline) {
            idlePump();
            Thread.sleep(5);
        }
        assertThat(menu.onlineError()).as("a refused connect records an error").isNotNull();
        assertThat(menu.getOnlineScreen()).isEqualTo(GameMenu.OnlineScreen.JOIN_ONLINE_SEARCH);

        // Further idle frames keep working (joiner is now null), then ESC returns to the connect menu.
        idlePump();
        idlePump();
        press(KeyEvent.VK_ESCAPE);
        assertThat(menu.getOnlineScreen()).isEqualTo(GameMenu.OnlineScreen.CONNECT_MENU);
    }

}
