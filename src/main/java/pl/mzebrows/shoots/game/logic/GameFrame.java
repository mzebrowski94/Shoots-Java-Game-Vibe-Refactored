
package pl.mzebrows.shoots.game.logic;

import java.awt.BorderLayout;
import java.awt.Image;
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
        setVisible(true);
        gameScreen.createBufferStrategy(2);
        setLocationByPlatform(false);
        if (icon != null) {
            setIconImage(icon);
        }
        addKeyListener(gameSettings.getInputBridge());

        setIgnoreRepaint(true);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        pack();

        gameScreen.initializeGraphics();
        gameCounter.initializeGraphics();
        gamePointer.initializeGraphics();
    }

    public GameScreen getGameScreen()   { return gameScreen; }
    public GameCounter getGameCounter() { return gameCounter; }
    public GamePointer getGamePointer() { return gamePointer; }
}
