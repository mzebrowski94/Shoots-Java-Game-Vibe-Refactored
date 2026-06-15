
package pl.mzebrows.shoots.game.logic;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import javax.swing.JFrame;

/** Game window: hosts the play screen, top round-timer counter, and side score panel. */
public class GameFrame extends JFrame {
    GameScreen gameScreen;
    GameCounter gameCounter;
    GamePointer gamePointer;

    /**
     * @param gameSettings shared game state/config
     * @param icon         window icon loaded from the classpath cache, or {@code null} if unavailable
     */
    public GameFrame(GameSettings gameSettings, Image icon)
    {
        setTitle("Project Shooots!");
        gameScreen = new GameScreen(gameSettings);
        gameCounter = new GameCounter(gameSettings);
        gamePointer = new GamePointer(gameSettings);

        add(gameScreen, BorderLayout.CENTER);
        add(gameCounter, BorderLayout.NORTH);
        add(gamePointer, BorderLayout.EAST);
        this.setFocusable(true);
        // Fixed-size active-rendering surface: resizing would invalidate the BufferStrategy.
        setResizable(false);
        if (icon != null) {
            setIconImage(icon);
        }
        addKeyListener(gameSettings.getInputBridge());

        setIgnoreRepaint(true);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        pack();
        fitAndCenterToScreen();

        setVisible(true);
        gameScreen.createBufferStrategy(2);

        gameScreen.initializeGraphics();
        gameCounter.initializeGraphics();
        gamePointer.initializeGraphics();
    }

    /**
     * Centres the window on the default screen, keeping it inside the usable desktop area (excluding the
     * taskbar). On screens too small for the full window (e.g. laptops) it anchors the top-left to the
     * usable origin so the top counter and as much of the field as possible stays on-screen instead of
     * being centred and clipped at both edges.
     */
    private void fitAndCenterToScreen() {
        GraphicsConfiguration gc = getGraphicsConfiguration() != null
                ? getGraphicsConfiguration()
                : GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .getDefaultScreenDevice().getDefaultConfiguration();
        Rectangle screen = gc.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        int usableX = screen.x + insets.left;
        int usableY = screen.y + insets.top;
        int usableW = screen.width - insets.left - insets.right;
        int usableH = screen.height - insets.top - insets.bottom;

        Dimension win = getSize();
        int x = (win.width <= usableW)
                ? usableX + (usableW - win.width) / 2
                : usableX;
        int y = (win.height <= usableH)
                ? usableY + (usableH - win.height) / 2
                : usableY;
        setLocation(x, y);
    }

    public GameScreen getGameScreen()   { return gameScreen; }
    public GameCounter getGameCounter() { return gameCounter; }
    public GamePointer getGamePointer() { return gamePointer; }
}
