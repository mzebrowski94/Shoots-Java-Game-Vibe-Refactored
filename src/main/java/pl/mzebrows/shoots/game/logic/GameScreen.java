
package pl.mzebrows.shoots.game.logic;

import pl.mzebrows.shoots.game.logic.Drawables.PointField;
import pl.mzebrows.shoots.game.logic.Drawables.Block;
import pl.mzebrows.shoots.game.logic.Drawables.Drawable;
import pl.mzebrows.shoots.game.logic.Drawables.DrawableEffect;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;

/**
 * Klasa rozrzeszająca klasę abstrakcyjną GameCanvas, jest to głowny ekran gry w
 * którym rysowana jest aktualnie obywająca się rozgrywka
 *
 * @author Mateusz Żebrowski, Nr albumu: 95281
 */
public class GameScreen extends GameCanvas {

    MapMatrix matrixMap;
    private ArrayList<Drawable> drawList = null;
    private ArrayList<DrawableEffect> effectList;
    int playerIterator;
    GameMenu menuLayout;

    GameScreen(GameSettings gameSettings) {
        super(gameSettings);

        width = gS.getDEFAULT_WIDTH();
        hight = gS.getDEFAULT_HIGHT();
        menuLayout = new GameMenu(gS);

        System.out.println("-GameScreen");
        setPreferredSize(new Dimension(width, hight));

        matrixMap = gS.getMapMatrix();
        playerIterator = 0;
        drawList = new ArrayList<>();
        effectList = new ArrayList<>();

        animatedElementLenght = width / 2;
    }

    @Override
    public void initializeGraphics() {
        strategy = getBufferStrategy();
        if (strategy == null) {
            this.createBufferStrategy(3);
            strategy = getBufferStrategy();
        }
        graphics = strategy.getDrawGraphics();
        g2d = (Graphics2D) graphics;
    }

    @Override
    public void drawUpdate(RoundEnum roundState) {

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        //g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
        //RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        if (roundState == RoundEnum.ROUND_PAUSED) {
            drawRoundPaused();
            menuLayout.drawMenu(g2d);

        } else if (roundState != RoundEnum.ROUND_PAUSED) {
            drawRoundContinues();

            if (roundState == RoundEnum.ROUND_BEGIN) {
                drawRoundBegining();

            } else if (roundState == RoundEnum.ROUND_ENDS) {
                drawRoundEnding();

            }
        }
        strategy.show();
    }

    /**
     * Metoda odpowiedzialna za dodanie obiektu do listy rysowanych obiektów
     *
     * @param drawable pobiera obiekt rozszerzający interfejs typu Drawable
     * @return zwraca listę rysowanych obiektów
     */
    public boolean addGameObject(Drawable drawable) {
        return drawList.add(drawable);
    }

    /**
     * etoda odpowiedzialna za usunięcie obiektu z listy rysowanych obiektów
     *
     * @param drawable pobiera obiekt rozszerzający interfejs typu Drawable
     * @return zwraca listę rysowanych obiektów
     */
    public boolean removeGameObject(Drawable drawable) {
        return drawList.remove(drawable);
    }

    /**
     * Metoda służaca do ponownego rozmieszczenia elemtnów gry na mapie gry
     */
    public void reInitializeMapPanel() {
        playerIterator = 0;
        drawList.clear();
        effectList.clear();
        initiailizeMapPanel();
    }

    /**
     * Metoda służaca do odczytania z tablicy położenia poszególnych elementów
     * gry oraz rozmieszczenia ich na mapie gry
     */
    private void initiailizeMapPanel() {
        for (int i = 0; i < matrixMap.getLength(); i++) {
            for (int j = 0; j < matrixMap.getLength(); j++) {
                if (matrixMap.mapMatrix[i][j] == 1) {
                    addBlock(i * 36, j * 36);
                } else if (matrixMap.mapMatrix[i][j] == 2) {
                    addPointField(i, j);
                } else if (matrixMap.mapMatrix[i][j] == 3) {
                    addPlayerBase();
                }
            }
        }

    }

    private void addPlayerBase() {
        drawList.add(gS.getPlayer(playerIterator).getPlayerBase());
        drawList.add(gS.getPlayer(playerIterator).getPlayerCursor());
        playerIterator++;
        if (playerIterator == 4) {
            playerIterator = 0;
        }
    }

    /**
     * Metoda zmieniająca stan obiektu typu PointField w zależoności czy udało
     * się przejąć dany punkt w grze
     *
     * @param colisionPoint przyjmuje wartości dotyczące punktu biorącego udział
     * w kolizji
     * @param player pobiera informacje o graczu
     * @param colisionTimes zawiera informację o tym ile razy obiekt typu Disc
     * uległ kolizji zanim zetknął się z obiektem PointField
     * @return zwraca wartość boolean informującą czy udało się przejąć dany punkt
     */
    public boolean setPointField(ColisionPoint colisionPoint, Player player, int colisionTimes) {
        return gS.getActualRound().getPointList().checkPointFiledErned(colisionPoint, player, colisionTimes);
    }

    public GameSettings getGameSettings() {
        return gS;
    }

    public void setGameSettings(GameSettings gS) {
        this.gS = gS;
    }

    private void addBlock(int x, int y) {
        drawList.add(new Block(x, y));
    }

    private void addPointField(int x, int y) {
        gS.getActualRound().getPointList().getPointFields().add(new PointField(x, y));
    }

    public ArrayList<Drawable> getDrawList() {
        return drawList;
    }

    public ArrayList<DrawableEffect> getEffectList() {
        return effectList;
    }

    @Override
    public void drawRoundPaused() {
        g2d.setColor(gS.getColorScheme().getMenuStandardColor());
        g2d.fillRect(0, 0, width, hight);
        //g2d.setColor(gS.getColorScheme().getBackgroundFontColor());
        //g2d.drawString("PAUSED", gS.getDEFAULT_WIDTH()/2 + textOffset, gS.getDEFAULT_HIGHT()/2);
        g2d.setColor(gS.getColorScheme().getDeadLineColor());
        g2d.setFont(textFont);
    }

    @Override
    public void drawRoundContinues() {
        g2d.setColor(gS.getColorScheme().getBackgroudColor());
        g2d.fillRect(0, 0, width, hight);
        //OtherDrawables
        if (drawList != null) {
            for (Drawable shape : drawList) {
                shape.draw(g2d);
            }
        }

        //Lasers
        if (gS.getPlayerList() != null) {
            for (int i = 0; i < gS.getPlayerList().size(); i++) {
                gS.getPlayerList().get(i).getPlayerLaser().draw(g2d);
            }
        }

        //PointFields
        if (gS.getActualRound().getPointList().getPointFields() != null) {
            for (Drawable shape : gS.getActualRound().getPointList().getPointFields()) {
                shape.draw(g2d);
            }
        }

        //Discs
        if (gS.getPlayerList() != null) {
            for (int i = 0; i < gS.getPlayerList().size(); i++) {
                for (int j = 0; j < gS.getPlayerList().get(i).getPlayerDiscs().size(); j++) {
                    gS.getPlayerList().get(i).getPlayerDiscs().get(j).draw(g2d);
                }
            }
        }

        //Effects
        if (effectList != null) {
            for (int i = 0; i < effectList.size(); i++) {
                if (effectList.get(i) != null && effectList.get(i).isEffect()) {
                    effectList.get(i).draw(g2d);
                } else {
                    effectList.remove(i);
                }
            }
        }
    }

    @Override
    public void drawRoundBegining() {
        g2d.setColor(gS.getColorScheme().getStandardColor());
        g2d.fillRect(0, 0, animatedElementLenght - animatedElementElapsed, hight);
        g2d.fillRect(animatedElementLenght + animatedElementElapsed, 0, animatedElementLenght - animatedElementElapsed, hight);
        g2d.fillRect(0, 0, width, animatedElementLenght - animatedElementElapsed);
        g2d.fillRect(0, animatedElementLenght + animatedElementElapsed, width, animatedElementLenght - animatedElementElapsed);

        if (animatedElementElapsed > animatedElementLenght) {
            animationElementEnd = animationEnd;
        }
    }

    @Override
    public void drawRoundEnding() {
        g2d.setColor(gS.getColorScheme().getStandardColor());
        //System.out.println(animatedElementElapsed);
        //System.out.println(animatedElementLenght);
        g2d.fillRect(0, 0, animatedElementElapsed, hight);
        g2d.fillRect(width - animatedElementElapsed, 0, animatedElementElapsed, hight);
        g2d.fillRect(0, 0, width, animatedElementElapsed);
        g2d.fillRect(0, width - animatedElementElapsed, hight, animatedElementElapsed);

        if (animatedElementElapsed > animatedElementLenght) {
            animationElementEnd = animationEnd;
        }
    }

    @Override
    public void initializeLayout() {

    }

    public GameMenu getMenuLayout() {
        return menuLayout;
    }

    public void setMenuLayout(GameMenu menuLayout) {
        this.menuLayout = menuLayout;
    }

}
