
package pl.mzebrows.shoots.game.logic;

import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;

/**
 * Klasa rozrzeszająca klasę abstrakcyjną GameCanvas, odpowidająca za boczny panel w grze, tzw. panel statystyk
 * @author Mateusz Żebrowski, Nr albumu: 95281
 */
public class GamePointer extends GameCanvas {

    Font roundTextFont;
    Font authorTextFont;
    int maxPointAmount;

    //LayoutSizes
    int frameWidth = 5;
    int frameHight = 16;
    int borderSize = 6;
    int size = 16;
    int freeSpace = 30;
    int pointBarOffset = 3;
    int statsStartPosiotionWidth = 10;
    int statsStartPosiotionHight = 250;
    int roundPositionWidth = 30;
    int roundPositionHight = 100;
    int pointBarSize;
    double pointPercent;
    int actualHight = 0;
    int actualLeftWidth = 0;
    int actualPlayerIndex;
    int winBlockSize = 30;
    int winBlockPositionHight = 650;
    ArrayList<Integer> playerPointBarsList;
    ArrayList<Integer> playerPointBarElapsed;

    GamePointer(GameSettings gameSettings) {
        super(gameSettings);
        fontSize = 25;
        textOffset = 1;
        this.gS = gameSettings;
        maxPointAmount = gS.getPointList().getMaxPointsAmount();

        setPreferredSize(new Dimension(gS.getDEFAULT_POINTER_WIGHT(), gS.getDEFAULT_POINTER_HIGHT()));
        playerPointBarsList = new ArrayList<>();
        playerPointBarElapsed = new ArrayList<>();
        for (int i = 0; i < gS.getPlayerList().size(); i++) {
            playerPointBarsList.add(0);
            playerPointBarElapsed.add(0);
        }

        width = gS.getDEFAULT_POINTER_WIGHT();
        hight = gS.getDEFAULT_POINTER_HIGHT();
        pointBarSize = width - (5 * borderSize) - (statsStartPosiotionWidth + borderSize);
        textFont = gS.getGameFont();
        standardColor = gS.getColorScheme().getStandardColor();
        roundTimeInSeconds = gS.getRoundTime();
    }

    /**
     * Metoda służąca do restartu pasków obrazujących ilość zdobytych punktów
     */
    public void restartGamePointer() {
        for (int i = 0; i < gS.getPlayerList().size(); i++) {
            playerPointBarsList.add(0);
            playerPointBarElapsed.add(0);
        }
    }

    @Override
    public void initializeLayout() {
        //DATA

        //Text
        textFont = new Font(gS.getGameFont().getFontName(), 100, fontSize);
        g2d.setFont(textFont);
        roundTextFont = textFont.deriveFont(80f);
        authorTextFont = textFont.deriveFont(20f);
        //FIRST RENDER
        // drawUpdate();
    }

    @Override
    public void drawUpdate(RoundEnum roundState) {
        Graphics2D g2d = (Graphics2D) graphics;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (roundState == RoundEnum.ROUND_PAUSED) {
            drawRoundPaused();
        } else {
            g2d.setColor(gS.getColorScheme().getBackgroudColor());
            g2d.fillRect(0, 0, gS.getDEFAULT_POINTER_WIGHT(), gS.getDEFAULT_POINTER_HIGHT());
            drawBorder(g2d);
            drawRoundCounter(g2d);
            drawPlayerStats(g2d, roundState);
            drawRoundCounterBlocks(g2d, roundState);
            drawAuthor(g2d);
            gS.getPointList().setChanged(false);
        }

        strategy.show();

    }

    /**
     * Metoda rysująca w prawym dolnym rogu ekranu nazwę autora gry
     * @param g2d parametr pobierający obiekt Graphic2D który rysuje odpowiednie
     * elementy na ekranie gry
     */
    public void drawAuthor(Graphics2D g2d) {
        g2d.setColor(standardColor);
        g2d.setFont(authorTextFont);
        g2d.drawString("By:", borderSize + textOffset, hight - freeSpace * 2);
        g2d.drawString("Mateusz", borderSize + textOffset, hight - freeSpace - 10);
        g2d.drawString("Zebrowski", borderSize + textOffset, hight - freeSpace + 10);
        g2d.setFont(textFont);
    }

    /**
     * Metoda rysująca na panelu numer aktualnie odbywanej rundy
     * @param g2d parametr pobierający obiekt Graphic2D który rysuje odpowiednie
     * elementy na ekranie gry
     */
    public void drawRoundCounter(Graphics2D g2d) {
        g2d.setColor(gS.getColorScheme().getBackgroundFontColor());
        g2d.drawString("Round", roundPositionWidth + textOffset, roundPositionHight + textOffset);
        g2d.setColor(standardColor);
        g2d.drawString("Round", roundPositionWidth, roundPositionHight);

        g2d.setFont(roundTextFont);
        g2d.setColor(gS.getColorScheme().getBackgroundFontColor());
        g2d.drawString("}", roundPositionWidth + freeSpace + textOffset - 10, roundPositionHight + freeSpace * 2 + textOffset);
        g2d.drawString("" + gS.getActualRoundNumber(), roundPositionWidth + freeSpace - 15 + textOffset, roundPositionHight + freeSpace * 2 + textOffset);
        g2d.setColor(standardColor);
        g2d.drawString("}", roundPositionWidth + freeSpace - 10, roundPositionHight + freeSpace * 2);
        g2d.drawString("" + gS.getActualRoundNumber(), roundPositionWidth + freeSpace - 15, roundPositionHight + freeSpace * 2);
        g2d.setFont(textFont);
    }

    /**
     * Metoda rysująca paski obrazujące ilość zdobytych punktów w aktualnie granej rundzie
     * @param g2d parametr pobierający obiekt Graphic2D który rysuje odpowiednie
     * elementy na ekranie gry
     * @param roundState argument pobierający aktualny stan rundy
     */
    public void drawPlayerStats(Graphics2D g2d, RoundEnum roundState) {

        for (int i = 0; i < gS.getPlayerList().size(); i++) {
            actualPlayerIndex = i;
            actualLeftWidth = statsStartPosiotionWidth + borderSize;
            int actualRightWidth = width - (4 * borderSize);
            actualHight = statsStartPosiotionHight + freeSpace + (i * 3 * freeSpace);
            pointPercent = (double) gS.getPlayerList().get(i).getPoints() / maxPointAmount;
            playerPointBarsList.set(i, (int) (pointBarSize * pointPercent));

            g2d.setColor(gS.getPlayerList().get(i).getColor());
            g2d.drawString(gS.getPlayerList().get(i).getName(), statsStartPosiotionWidth + textOffset, actualHight + textOffset);
            g2d.setColor(standardColor);
            g2d.drawString(gS.getPlayerList().get(i).getName(), statsStartPosiotionWidth, actualHight);

            g2d.fillRect(actualLeftWidth, actualHight + freeSpace, frameWidth, 16);
            g2d.fillRect(actualRightWidth, actualHight + freeSpace, frameWidth, 16);

            if (gS.getPreviousRound() != null) {
                double prevoiusPointPercent = (double) gS.getPreviousRound().getPlayerPointsList().get(i) / maxPointAmount;
                g2d.setColor(gS.getColorScheme().getBackgroundPointBarColor());
                g2d.fillRect(actualLeftWidth + frameWidth, actualHight + freeSpace + 3, (int) (pointBarSize * prevoiusPointPercent), 10);
            }
            if (roundState == RoundEnum.ROUND_CONTINUES) {
                drawRoundContinues();
            } else if (roundState == RoundEnum.ROUND_BEGIN) {
                drawRoundBegining();
            } else if (roundState == RoundEnum.ROUND_ENDS) {
                drawRoundEnding();
            }

        }
    }

    /**
     * Metoda rysująca lewą i prawą ramkę pasków punktów
     * @param g2d parametr pobierający obiekt Graphic2D który rysuje odpowiednie
     * elementy na ekranie gry
     * @param roundState argument pobierający aktualny stan rundy
     */
    public void drawRoundCounterBlocks(Graphics2D g2d, RoundEnum roundState) {

       
            for (int i = 0; i < gS.getPlayerList().size(); i++) {
               

                    g2d.setColor(gS.getColorScheme().getWinBlockColor());
                    g2d.fillRect((width / 2) - winBlockSize / 2, winBlockPositionHight + (i * (freeSpace + 10)), winBlockSize, winBlockSize);
                    g2d.setColor(standardColor);
                    g2d.setStroke(new BasicStroke(borderSize / 2));
                    g2d.drawRect((width / 2) - winBlockSize / 2, winBlockPositionHight + (i * (freeSpace + 10)), winBlockSize, winBlockSize);
                    g2d.setColor(gS.getPlayerList().get(i).getColor().darker());
                    g2d.drawString(""+gS.getPlayerList().get(i).getRoundsWon(), (width / 2) - winBlockSize / 4 , winBlockPositionHight + winBlockSize - 5 + (i*winBlockSize + i*10));
                    
            }
        
    }

    @Override
    public void tick() {
        timeElapsed += 0.012;

        for (int i = 0; i < gS.getPlayerList().size(); i++) {
            playerPointBarElapsed.set(i, (int) (playerPointBarsList.get(i) * (timeElapsed * 1f / animationTime * 1f)));
            if (timeElapsed > animationTime) {
                animationEnd = true;
            }
        }
    }

    /**
     * Metoda rysująca ramkę panelu GamePointer
     * @param g2d parametr pobierający obiekt Graphic2D który rysuje odpowiednie
     * elementy na ekranie gry
     */
    public void drawBorder(Graphics2D g2d) {
        g2d.setColor(standardColor);
        g2d.fillRect(0, 0, borderSize, hight);
        g2d.fillRect(width - borderSize, 0, borderSize, hight);
        //g2d.fillRect(0, 0, width, borderSize);
        g2d.fillRect(0, hight - borderSize, width, borderSize);
        g2d.fillRect(0, hight - freeSpace * 3, width, borderSize);

    }

    @Override
    public void drawRoundPaused() {
        g2d.setColor(gS.getColorScheme().getMenuStandardColor());
        g2d.fillRect(0, 0, gS.getDEFAULT_POINTER_WIGHT(), gS.getDEFAULT_POINTER_HIGHT());
    }

    @Override
    public void drawRoundContinues() {
        ///POINT BAR
        g2d.setColor(gS.getPlayerList().get(actualPlayerIndex).getColor().darker());
        g2d.fillRect(actualLeftWidth + frameWidth, actualHight + freeSpace + 3 + pointBarOffset, playerPointBarsList.get(actualPlayerIndex) + pointBarOffset, 10);
        g2d.setColor(gS.getPlayerList().get(actualPlayerIndex).getColor());
        g2d.fillRect(actualLeftWidth + frameWidth, actualHight + freeSpace + 3, playerPointBarsList.get(actualPlayerIndex), 10);
    }

    @Override
    public void drawRoundBegining() {
        //System.out.println("ROUND STATS BEGIN");
        animationElementEnd = true;
    }

    @Override
    public void drawRoundEnding() {
        //System.out.println("ROUND STATS END");
        g2d.setColor(gS.getPlayerList().get(actualPlayerIndex).getColor().darker());
        g2d.fillRect(actualLeftWidth + frameWidth, actualHight + freeSpace + 3 + pointBarOffset, playerPointBarsList.get(actualPlayerIndex) - playerPointBarElapsed.get(actualPlayerIndex) + pointBarOffset, 10);
        g2d.setColor(gS.getPlayerList().get(actualPlayerIndex).getColor());
        g2d.fillRect(actualLeftWidth + frameWidth, actualHight + freeSpace + 3, playerPointBarsList.get(actualPlayerIndex) - playerPointBarElapsed.get(actualPlayerIndex), 10);
    }

}
