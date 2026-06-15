
package pl.mzebrows.shoots.game.logic;

import pl.mzebrows.shoots.entity.Entity;
import pl.mzebrows.shoots.score.CapturePoint;
import pl.mzebrows.shoots.spatial.TileType;
import pl.mzebrows.shoots.world.PlayWorld;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;

/**
 * Main play-field panel. Renders the live {@link PlayWorld} model (walls, capture points, pooled
 * discs, and the aiming laser) directly each frame — no legacy {@code Drawable} allocations and no
 * reads of the superseded {@code Player}/{@code Disc}/{@code PointList} model.
 */
public class GameScreen extends GameCanvas {

    private static final Color WALL_COLOR = new Color(25, 25, 25);

    private GameMenu menuLayout;

    /** Live simulation pushed by the renderer each frame (null when not playing). */
    private PlayWorld world;
    private double alpha;

    /**
     * Reusable laser-polyline scratch buffers, lazily sized from {@code laser.maxBounces} (origin +
     * N reflection points) on first use so the preview length is config-driven, not hard-coded.
     */
    private int[] laserX;
    private int[] laserY;
    private float dashPhase = 0f;

    GameScreen(GameSettings gameSettings) {
        super(gameSettings);

        width = gS.getDEFAULT_WIDTH();
        hight = gS.getDEFAULT_HIGHT();
        menuLayout = new GameMenu(gS);

        setPreferredSize(new Dimension(width, hight));
        animatedElementLenght = width / 2;
    }

    /** Receives the live model + interpolation factor for this frame from the renderer. */
    public void setWorld(PlayWorld world, double alpha) {
        this.world = world;
        this.alpha = alpha;
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
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        if (roundState == RoundEnum.ROUND_PAUSED) {
            drawRoundPaused();
            menuLayout.drawMenu(g2d, world);
        } else {
            drawRoundContinues();
            if (roundState == RoundEnum.ROUND_BEGIN) {
                drawRoundBegining();
            } else if (roundState == RoundEnum.ROUND_ENDS) {
                drawRoundEnding();
            }
        }
        strategy.show();
    }

    @Override
    public void drawRoundPaused() {
        g2d.setColor(gS.getColorScheme().getMenuStandardColor());
        g2d.fillRect(0, 0, width, hight);
        g2d.setColor(gS.getColorScheme().getDeadLineColor());
        g2d.setFont(textFont);
    }

    @Override
    public void drawRoundContinues() {
        g2d.setColor(gS.getColorScheme().getBackgroudColor());
        g2d.fillRect(0, 0, width, hight);
        if (world == null) {
            return;
        }
        drawWalls(world);
        drawCapturePoints(world);
        drawLasers(world);
        drawDiscs(world);
    }

    /** Draws wall tiles as solid squares; tile (i,j) occupies pixel (i*unit, j*unit). */
    private void drawWalls(PlayWorld world) {
        int unit = world.unit();
        TileType[][] tiles = world.tiles();
        g2d.setColor(WALL_COLOR);
        g2d.setStroke(new BasicStroke());
        for (int i = 0; i < tiles.length; i++) {
            for (int j = 0; j < tiles[i].length; j++) {
                if (tiles[i][j] == TileType.WALL) {
                    g2d.fillRect(i * unit, j * unit, unit, unit);
                }
            }
        }
    }

    /** Draws each capture point: neutral as a marker, captured tinted in the owner's colour by level. */
    private void drawCapturePoints(PlayWorld world) {
        int unit = world.unit();
        dashPhase += 0.12f;
        var dashed = new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                5.0f, new float[]{6f}, dashPhase);
        g2d.setStroke(dashed);
        for (CapturePoint point : world.scoring().points()) {
            int px = point.getTileX() * unit;
            int py = point.getTileY() * unit;
            if (point.isCaptured() && point.getOwnerId() >= 0) {
                Color owner = world.playerColor(point.getOwnerId());
                int level = Math.max(1, point.getLevel());
                g2d.setColor(owner);
                int arcCount = Math.min(level, 4);
                for (int k = 0; k < 4; k++) {
                    g2d.setColor(k < arcCount ? owner : owner.darker().darker());
                    g2d.fillArc(px + 4, py + 4, unit - 8, unit - 8, 55 + k * 90, 70);
                }
                g2d.setColor(Color.gray);
                g2d.fillOval(px + unit / 2 - 7, py + unit / 2 - 7, 14, 14);
                g2d.setColor(owner.darker().darker());
                g2d.drawOval(px + 2, py + 2, unit - 4, unit - 4);
            } else {
                g2d.setColor(Color.black);
                g2d.drawRoundRect(px + 2, py + 2, unit - 4, unit - 4, 10, 10);
                g2d.drawLine(px + 4, py + 4, px + unit - 4, py + unit - 4);
                g2d.drawLine(px + 4, py + unit - 4, px + unit - 4, py + 4);
            }
        }
    }

    /** Draws each player's predicted aiming laser as a dashed polyline in the player colour. */
    private void drawLasers(PlayWorld world) {
        // Origin point + one point per predicted reflection (laser.maxBounces).
        int laserPoints = 1 + world.config().disc().laserMaxBounces();
        if (laserX == null || laserX.length != laserPoints) {
            laserX = new int[laserPoints];
            laserY = new int[laserPoints];
        }
        var dashed = new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                5.0f, new float[]{6f}, dashPhase);
        g2d.setStroke(dashed);
        for (int p = 0; p < world.playerCount(); p++) {
            int n = world.predictLaser(p, laserX, laserY);
            if (n >= 2) {
                g2d.setColor(world.playerColor(p));
                g2d.drawPolyline(laserX, laserY, n);
            }
        }
    }

    /** Draws each active disc as a ring in its owner's colour, interpolated by the loop alpha. */
    private void drawDiscs(PlayWorld world) {
        int big = world.config().disc().bigRadius();
        int small = world.config().disc().smallRadius();
        int halfBig = big / 2;
        int halfSmall = small / 2;
        var discs = world.discs();
        for (int i = 0, n = discs.size(); i < n; i++) {
            Entity disc = discs.get(i);
            if (!disc.isActive()) {
                continue;
            }
            double dx = disc.interpolatedX(alpha);
            double dy = disc.interpolatedY(alpha);
            Area outer = new Area(new Ellipse2D.Double(dx - halfBig, dy - halfBig, big, big));
            outer.subtract(new Area(new Ellipse2D.Double(dx - halfSmall, dy - halfSmall, small, small)));
            g2d.setColor(world.playerColor(disc.getOwnerId()));
            g2d.fill(outer);
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

    public GameSettings getGameSettings() {
        return gS;
    }

    public void setGameSettings(GameSettings gS) {
        this.gS = gS;
    }
}
