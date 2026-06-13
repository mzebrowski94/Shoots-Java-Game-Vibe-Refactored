
package pl.mzebrows.shoots.game.logic;

import java.awt.BorderLayout;
import java.awt.Image;
import javax.swing.ImageIcon;
import javax.swing.JFrame;

/**
 * Klasa rozszerzająca JFrame stanowiąca okienko gry
 * @author Mateusz Żebrowski, Nr albumu: 95281
 */
public class GameFrame extends JFrame { 
    GameScreen gameScreen;
    GameCounter gameCounter;
    GamePointer gamePointer;
    
    public GameFrame(GameSettings gameSettings)
    {
        setTitle("Projekt1");            // Tytuł
        //centerFrame();
        //JFRAME GAME MENU
        gameScreen = new GameScreen(gameSettings);
        gameCounter = new GameCounter(gameSettings);
        gamePointer = new GamePointer(gameSettings);
              
        add(gameScreen, BorderLayout.CENTER);
        add(gameCounter, BorderLayout.NORTH);
        add(gamePointer, BorderLayout.EAST);
        this.setFocusable(true);
        setVisible(true);
        gameScreen.createBufferStrategy(2);
        setLocationByPlatform(false);                                            // NULL ustawia na środek , TRUE daje wybór systemowy
        Image icon = new ImageIcon("C:\\Users\\mzebr\\Desktop\\Programowanie\\Shoots-Vibe-Refactor\\Shoots-Vibe-Refactor\\src\\main\\resources\\images\\game.png").getImage();
        setIconImage(icon);
        addKeyListener(gameSettings.getInputBridge());
        
        System.out.println("-MainFrame");
        setIgnoreRepaint( true );
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
