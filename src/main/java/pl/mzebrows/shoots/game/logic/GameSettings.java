
package pl.mzebrows.shoots.game.logic;

import pl.mzebrows.shoots.input.InputBridge;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * GameSettings - Obiekt odpowiadający za ustawienia gry.
 *  Przechowuje między innymi ustawienia:
 *  -ilości rund
 *  -czasu trwania rundy
 *  -stałych odpowiadających za rozmiar okienek
 *  itp.
 * @author Mateusz Żebrowski, Nr albumu: 95281
 */
public class GameSettings {

    ArrayList<Player> playerList;
    PointList pointList;
    ArrayList<Round> roundList;
    int playerNumber;
    InputBridge inputBridge;
    ColisionCalculator colisionCalculator;
    Font gameFont;
    Font menuFont;

    //rozmary okienek
    final int DEFAULT_WIDTH = PSConst.UNIT.getValue() * PSConst.WINDOWWIDTH.getValue();
    final int DEFAULT_HIGHT = PSConst.UNIT.getValue() * PSConst.WINDOWWIDTH.getValue();
    final int DEFAULT_COUNTER_HIGHT = PSConst.UNIT.getValue() * 2;
    final int DEFAULT_POINTER_WIGHT = PSConst.UNIT.getValue() * 4;
    final int DEFAULT_COUNTER_WIDTH = DEFAULT_WIDTH + DEFAULT_POINTER_WIGHT;
    final int DEFAULT_POINTER_HIGHT = PSConst.UNIT.getValue() * PSConst.WINDOWWIDTH.getValue();
    
    //jednostki
    final int SIZE = PSConst.TABLESIZE.getValue();
    final int UNIT = PSConst.UNIT.getValue();
    
    int roundTime;
    MapMatrix mapMatrix;
    int actualRoundNumber;
    boolean playerKeyboardAvailable;
    ColorScheme colorScheme;
    int animationTime;
    int roundEndDelay;
    int roundLimit;
    Round actualRound, previousRound;
    boolean gameEnd;

    /**
     * Konstruktor klasy GameSettings
     */
    public GameSettings() {
        this.playerNumber = 2;
        this.roundLimit = 2;
        this.roundEndDelay = 2;
        this.animationTime = 1;
        this.colorScheme = new ColorScheme();
        this.playerKeyboardAvailable = true;
        this.actualRoundNumber = 0;
        this.roundTime = 15;
        this.roundList = new ArrayList<>();
        this.playerList = new ArrayList<Player>();
        this.pointList = new PointList(this);
        inputBridge = InputBridge.withDefaultKeyMap();
        mapMatrix = new MapMatrix(this);
        colisionCalculator = new ColisionCalculator(this);
        mapMatrix.initializeMap();
        initializeFont();
        initializePlayers(playerNumber, playerList);
    }
    
    /**
     * Metoda używana do zainicjalizowanie danej ilość graczy
     */  
    private ArrayList<Player> initializePlayers(int playerNumber, ArrayList<Player> playerList) {
        playerList.clear();
        if (playerNumber >= 1) {
            initializePlayer(1);
        }
        if (playerNumber >= 2) {
            initializePlayer(2);
        }
        if (playerNumber >= 3) {
            initializePlayer(3);
        }
        if (playerNumber >= 4) {
            initializePlayer(4);
        }

        return playerList; 
    }
    
        
    private void initializePlayer(int number) {
        playerList.add(new Player(number, this));
    }
    
    /**
     * Metoda odpowiadająca za inicjalizację czcionek w grze
     */
    public void initializeFont() {
        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            //create the font to use. Specify the size!
            File loadedGameFont = new File("C:\\Users\\mzebr\\Desktop\\Programowanie\\Shoots-Vibe-Refactor\\Shoots-Vibe-Refactor\\src\\main\\resources\\fonts\\13_Misa.TTF");
            gameFont = Font.createFont(Font.TRUETYPE_FONT, loadedGameFont).deriveFont(12f);
            File loadedMenuFont = new File("C:\\Users\\mzebr\\Desktop\\Programowanie\\Shoots-Vibe-Refactor\\Shoots-Vibe-Refactor\\src\\main\\resources\\fonts\\GeosansLight.ttf");
            menuFont = Font.createFont(Font.TRUETYPE_FONT, loadedMenuFont).deriveFont(25f);

            //register the font
            ge.registerFont(Font.createFont(Font.TRUETYPE_FONT, loadedGameFont));
            ge.registerFont(Font.createFont(Font.TRUETYPE_FONT, loadedMenuFont));
            System.out.println("- Loaded Misa font by Zane Townsend");
            System.out.println("- Code Demo font by Fontfabric");

        } catch (IOException e) {
            e.printStackTrace();
        } catch (FontFormatException e) {
            e.printStackTrace();
        }

    }

    /** Forwards the current input to each player's input handler. */
    public void checkPlayerInput(InputBridge input) {
        for (int i = 0; i < playerList.size(); i++) {
            playerList.get(i).checkPlayerInput(input);
        }
    }

    /**
     * Getter pobierający z listy graczy gracza o podanym indexsie
     * @param x index gracza w lisie graczy
     * @return gracza o podanym indexsie
     */
    public Player getPlayer(int x) {
        if (x == 0 || x == 1 || x == 2) {
            return playerList.get(x);
        } else {
            return playerList.get(3);
        }
    }

    /**
     *  Metoda odpowiedzialna, za utworzenie nowej rundy oraz dodanie jej do listy odbytych rund
     *  Modyfikująca odpowiednio zmienne: actualRound, previousRound
     *  Reinicjalizująca ekran gry
     *  Resetujaca liczbę punktów graczy
     * @param gameScreen wskaźnik na główny ekran gry
     */
    public void startNewRound(GameScreen gameScreen) {
        actualRoundNumber++;
        Round newRound = new Round(this, actualRoundNumber);
        if (roundList != null && !roundList.isEmpty()) {
            previousRound = roundList.get(roundList.size() - 1);
            roundList.add(newRound);
            actualRound = newRound;
        } else {
            roundList.add(newRound);
            actualRound = newRound;
        }

        for (int i = 0; i < playerList.size(); i++) {
            playerList.get(i).resetPlayerCursor();
        }

        actualRound.getPointList().updatePlayerPoints();
        mapMatrix.reInitializeMap();
        gameScreen.reInitializeMapPanel();
    }

    /**
     * Metoda odpowidzialna, za rozpoczęcie nowej gry, resetująca liczbę graczy oraz listę rund
     */
    public void restartGame() {
        actualRoundNumber = 0;
        roundList.clear();
        previousRound = null;
        playerList = initializePlayers(playerNumber, playerList);
    }

    /**
     * Metoda sprawdza czy nastąpił koniec gry oraz którzy gracze wygrali daną partię
     * @return zmienną boolowską oznaczającą czy nastąpił koniec gry
     */
    public boolean checkGameEnd() {

        if (actualRoundNumber >= roundLimit) {
            int roundsWon = 0;
            int pointsErned = 0;

            roundsWon = playerList.get(0).getRoundsWon();

            gameEnd = true;
            for (int i = 0; i < playerList.size(); i++) {
                if(playerList.get(i).getRoundsWon() > roundsWon)
                    roundsWon = playerList.get(i).getRoundsWon();
            }
            
            for(int i = 0; i < playerList.size(); i++)
            {
                if(playerList.get(i).getRoundsWon() == roundsWon)
                {
                    if(playerList.get(i).getAllPointsErned() >  pointsErned)
                        pointsErned = playerList.get(i).getAllPointsErned();
                }
            }
            
            for(int i = 0; i < playerList.size(); i++)
            {
                if(playerList.get(i).getRoundsWon() == roundsWon && playerList.get(i).getAllPointsErned() == pointsErned)
                    playerList.get(i).setWinner(true);
            }

            return true;

        } else {
            return false;
        }

    }


    public MapMatrix getMapMatrix() {
        return mapMatrix;
    }


    public void setMapMatrix(MapMatrix mapMatrix) {
        this.mapMatrix = mapMatrix;
    }


    public ColisionCalculator getColisionCalculator() {
        return colisionCalculator;
    }

    public void setColisionCalculator(ColisionCalculator colisionCalculator) {
        this.colisionCalculator = colisionCalculator;
    }


    public InputBridge getInputBridge() {
        return inputBridge;
    }

    public void setInputBridge(InputBridge inputBridge) {
        this.inputBridge = inputBridge;
    }

    public int getPlayerNumber() {
        return playerNumber;
    }

    public void setPlayerNumber(int playerNumber) {
        this.playerNumber = playerNumber;
    }

    public int getDEFAULT_WIDTH() {
        return DEFAULT_WIDTH;
    }


    public int getDEFAULT_HIGHT() {
        return DEFAULT_HIGHT;
    }

    public int getDEFAULT_COUNTER_HIGHT() {
        return DEFAULT_COUNTER_HIGHT;
    }

    public int getDEFAULT_POINTER_WIGHT() {
        return DEFAULT_POINTER_WIGHT;
    }

    public int getDEFAULT_POINTER_HIGHT() {
        return DEFAULT_POINTER_HIGHT;
    }

    public int getDEFAULT_COUNTER_WIDTH() {
        return DEFAULT_COUNTER_WIDTH;
    }

   
    public int getSIZE() {
        return SIZE;
    }


    public int getUNIT() {
        return UNIT;
    }

 
    public ArrayList<Player> getPlayerList() {
        return playerList;
    }

    public void setPlayerList(ArrayList<Player> playerList) {
        this.playerList = playerList;
    }

 
    public int getRoundTime() {
        return roundTime;
    }

    public void setRoundTime(int roundTime) {
        this.roundTime = roundTime;
    }


    public PointList getPointList() {
        return pointList;
    }


    public void setPointList(PointList pointList) {
        this.pointList = pointList;
    }


    public Font getGameFont() {
        return gameFont;
    }


    public void setGameFont(Font gameFont) {
        this.gameFont = gameFont;
    }


    public int getActualRoundNumber() {
        return actualRoundNumber;
    }


    public void setActualRoundNumber(int actualRoundNumber) {
        this.actualRoundNumber = actualRoundNumber;
    }

    public boolean isPlayerKeyboardAvailable() {
        return playerKeyboardAvailable;
    }


    public void setPlayerKeyboardAvailable(boolean playerKeyboardAvailable) {
        this.playerKeyboardAvailable = playerKeyboardAvailable;
    }


    public ColorScheme getColorScheme() {
        return colorScheme;
    }

  
    public void setColorScheme(ColorScheme colorScheme) {
        this.colorScheme = colorScheme;
    }

    public int getAnimationTime() {
        return animationTime;
    }

 
    public void setAnimationTime(int animationTime) {
        this.animationTime = animationTime;
    }


    public int getRoundEndDelay() {
        return roundEndDelay;
    }


    public void setRoundEndDelay(int roundEndDelay) {
        this.roundEndDelay = roundEndDelay;
    }

    public Round getActualRound() {
        return actualRound;
    }

    public Round getPreviousRound() {
        return previousRound;
    }


    public Font getMenuFont() {
        return menuFont;
    }


    public void setMenuFont(Font menuFont) {
        this.menuFont = menuFont;
    }


    public int getRoundLimit() {
        return roundLimit;
    }


    public void setRoundLimit(int roundLimit) {
        this.roundLimit = roundLimit;
    }


    public ArrayList<Round> getRoundList() {
        return roundList;
    }


    public void setRoundList(ArrayList<Round> roundList) {
        this.roundList = roundList;
    }

    public boolean isGameEnd() {
        return gameEnd;
    }

    public void setGameEnd(boolean gameEnd) {
        this.gameEnd = gameEnd;
    } 
    
}
