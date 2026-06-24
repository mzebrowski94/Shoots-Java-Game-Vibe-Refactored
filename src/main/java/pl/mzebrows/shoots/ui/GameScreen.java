// pl/mzebrows/shoots/ui/GameScreen.java
package pl.mzebrows.shoots.ui;

import lombok.Getter;
import lombok.Setter;
import pl.mzebrows.shoots.app.GameSettings;

import pl.mzebrows.shoots.entity.Entity;
import pl.mzebrows.shoots.score.CapturePoint;
import pl.mzebrows.shoots.spatial.TileType;
import pl.mzebrows.shoots.world.BlockHitEffect;
import pl.mzebrows.shoots.world.PlayWorld;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;

/**
 * Main play-field panel. Renders the live {@link PlayWorld} model (walls, capture points, pooled
 * discs, and the aiming laser) directly each frame — no legacy {@code Drawable} allocations and no
 * reads of the superseded {@code Player}/{@code Disc}/{@code PointList} model.
 */
public class GameScreen extends GameCanvas {

    private static final Color WALL_COLOR = new Color(25, 25, 25);

    @Getter @Setter private GameMenu menuLayout;

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
    private int baseRotation = 0;

    GameScreen(GameSettings gameSettings) {
        super(gameSettings);

        width = gameSettings.getDefaultWidth();
        height = gameSettings.getDefaultHeight();
        menuLayout = new GameMenu(gameSettings);

        setPreferredSize(new Dimension(width, height));
        animatedElementLength = width / 2;
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
        // Active rendering: re-acquire the draw graphics every frame and repeat while the BufferStrategy
        // reports lost/restored contents. Caching one Graphics at init breaks when the window is moved or
        // its surface is recreated -- the cached context goes stale and the game appears frozen.
        if (strategy == null) {
            return;
        }
        do {
            do {
                g2d = (Graphics2D) strategy.getDrawGraphics();
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
                g2d.dispose();
            } while (strategy.contentsRestored());
            strategy.show();
        } while (strategy.contentsLost());
    }

    @Override
    public void drawRoundPaused() {
        // The menu-standard tint is near-transparent (alpha 10): it is meant to sit OVER a frozen game
        // frame so the field shows through (pause / win-score screens). With per-frame buffer re-acquire
        // the back buffer is not guaranteed to hold the last frame, so when a game exists we redraw the
        // frozen field and overlay the tint (the nicer translucent look). On a fresh start (no round yet)
        // there is nothing behind the menu, so we clear to an opaque background instead.
        if (hasGameBehindMenu()) {
            drawRoundContinues();
        } else {
            g2d.setColor(gameSettings.getColorScheme().getBackgroundColor());
            g2d.fillRect(0, 0, width, height);
        }
        g2d.setColor(gameSettings.getColorScheme().getMenuStandardColor());
        g2d.fillRect(0, 0, width, height);
        g2d.setColor(gameSettings.getColorScheme().getDeadLineColor());
        g2d.setFont(textFont);
    }

    /** True when a game frame should show through the translucent menu (pause mid-game or win screen). */
    private boolean hasGameBehindMenu() {
        return world != null && (gameSettings.getActualRoundNumber() > 0 || gameSettings.isGameEnd());
    }

    @Override
    public void drawRoundContinues() {
        g2d.setColor(gameSettings.getColorScheme().getBackgroundColor());
        g2d.fillRect(0, 0, width, height);
        if (world == null) {
            return;
        }
        drawWalls(world);
        drawBlockHits(world);
        drawCapturePoints(world);
        drawBases(world);
        drawCursors(world);
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

    /**
     * Draws the transient block-hit flash over each struck wall tile, mirroring the legacy
     * {@code LightEffect}: a short grey ramp-up, then the disc owner's colour fading out.
     */
    private void drawBlockHits(PlayWorld world) {
        int unit = world.unit();
        g2d.setStroke(new BasicStroke());
        var effects = world.blockHits();
        for (int i = 0, n = effects.size(); i < n; i++) {
            BlockHitEffect e = effects.get(i);
            int px = e.tileX() * unit;
            int py = e.tileY() * unit;
            if (e.phase() == BlockHitEffect.Phase.GREY) {
                int g = e.greyLevel();
                g2d.setColor(new Color(g, g, g));
            } else {
                Color owner = world.playerColor(e.ownerId());
                int a = Math.max(0, Math.min(255, e.colorAlpha()));
                g2d.setColor(new Color(owner.getRed(), owner.getGreen(), owner.getBlue(), a));
            }
            g2d.fillRect(px, py, unit, unit);
        }
    }

    /**
     * Draws each player base as two counter-rotating dashed concentric rings in the player colour,
     * centred on the base tile (mirrors the legacy {@code PlayerBase} drawable).
     */
    private void drawBases(PlayWorld world) {
        int unit = world.unit();
        int rBig = 25;
        int rSmall = 15;
        var dashed = new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                10.0f, new float[]{6.9f}, 1.0f);
        g2d.setStroke(dashed);
        baseRotation++;
        for (int p = 0; p < world.playerCount(); p++) {
            PlayWorld.BasePlacement base = world.baseOf(p);
            // Screen convention: tile (i,j) -> pixel (i*unit, j*unit); base centre is the tile centre.
            int cx = base.tileX() * unit + unit / 2;
            int cy = base.tileY() * unit + unit / 2;
            g2d.setColor(world.playerColor(p));
            drawRotatedRing(cx, cy, rBig, baseRotation);
            drawRotatedRing(cx, cy, rSmall, -baseRotation);
            drawChargeRing(cx, cy, rBig + 6, world.chargeProgress(p), world.playerColor(p));
        }
    }

    /**
     * Draws the power-shot charge indicator as a ring on the base that fills clockwise from the top in
     * the player colour and brightens as it charges, with a soft white glow as it nears full. No-op
     * when {@code progress <= 0} (not charging), so a normal shot shows nothing.
     */
    private void drawChargeRing(int cx, int cy, int radius, double progress, Color color) {
        if (progress <= 0.0) {
            return;
        }
        var old = g2d.getStroke();
        int d = 2 * radius;
        g2d.setStroke(new BasicStroke(4.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        // Faint full-circle track behind the fill.
        g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 55));
        g2d.drawOval(cx - radius, cy - radius, d, d);
        // Filling sweep, brightening with progress.
        int sweep = (int) Math.round(360.0 * Math.min(1.0, progress));
        int alpha = Math.min(255, (int) (110 + 145 * Math.min(1.0, progress)));
        g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
        g2d.drawArc(cx - radius, cy - radius, d, d, 90, -sweep);
        // Soft glow as it approaches the auto-fire threshold.
        if (progress >= 0.8) {
            int gr = radius + 5;
            int glowAlpha = (int) (90 * ((progress - 0.8) / 0.2));
            g2d.setStroke(new BasicStroke(3.0f));
            g2d.setColor(new Color(255, 255, 255, Math.max(0, Math.min(120, glowAlpha))));
            g2d.drawOval(cx - gr, cy - gr, 2 * gr, 2 * gr);
        }
        g2d.setStroke(old);
    }

    /**
     * Draws each player's aim cursor: a filled arrowhead sitting just outside the base ring and pointing
     * along the current shot angle (ported from the legacy {@code PlayerCursor}). Uses the same disc
     * travel convention as movement/laser (dir = (-sin θ, cos θ)), so the arrow points exactly where a
     * fired disc would go.
     */
    private void drawCursors(PlayWorld world) {
        int unit = world.unit();
        int size = (int) (unit * 0.5);
        int standoff = 2 * size; // distance from base centre to the arrow tip base, matching legacy
        var oldStroke = g2d.getStroke();
        g2d.setStroke(new BasicStroke(1.0f));
        for (int p = 0; p < world.playerCount(); p++) {
            PlayWorld.BasePlacement base = world.baseOf(p);
            int cx = base.tileX() * unit + unit / 2;
            int cy = base.tileY() * unit + unit / 2;

            double angle = Math.toRadians(-world.aimOf(p).currentAngle());
            double dirX = Math.sin(angle);   // disc convention: x uses sin(-angle)
            double dirY = Math.cos(angle);   // disc convention: y uses cos(-angle)
            // Perpendicular unit vector for the arrow's width.
            double perpX = -dirY;
            double perpY = dirX;

            // Arrow geometry: a tip ahead, two barbs to the sides, and a notch behind (4-point head).
            double baseX = cx + dirX * standoff;
            double baseY = cy + dirY * standoff;
            double tipX = baseX + dirX * size;
            double tipY = baseY + dirY * size;
            double leftX = baseX + perpX * size;
            double leftY = baseY + perpY * size;
            double rightX = baseX - perpX * size;
            double rightY = baseY - perpY * size;
            double notchX = baseX + dirX * (0.5 * size);
            double notchY = baseY + dirY * (0.5 * size);

            int[] xs = {(int) leftX, (int) tipX, (int) rightX, (int) notchX};
            int[] ys = {(int) leftY, (int) tipY, (int) rightY, (int) notchY};
            g2d.setColor(world.playerColor(p));
            g2d.fillPolygon(xs, ys, 4);
        }
        g2d.setStroke(oldStroke);
    }

    /** Draws one dashed ring of {@code radius} centred at ({@code cx},{@code cy}), rotated by degrees. */
    private void drawRotatedRing(int cx, int cy, int radius, int rotationDegrees) {
        AffineTransform old = g2d.getTransform();
        g2d.rotate(Math.toRadians(rotationDegrees), cx, cy);
        g2d.draw(new Ellipse2D.Double(cx - radius, cy - radius, 2.0 * radius, 2.0 * radius));
        g2d.setTransform(old);
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
            Color ownerColor = world.playerColor(disc.getOwnerId());
            if (disc.isPowered()) {
                drawPowerGlow(dx, dy, big, ownerColor);
            }
            var outer = new Area(new Ellipse2D.Double(dx - halfBig, dy - halfBig, big, big));
            outer.subtract(new Area(new Ellipse2D.Double(dx - halfSmall, dy - halfSmall, small, small)));
            g2d.setColor(disc.isPowered() ? ownerColor.brighter() : ownerColor);
            g2d.fill(outer);
            if (disc.isPowered()) {
                // Bright core to read as a charged, energised disc.
                g2d.setColor(new Color(255, 255, 255, 210));
                g2d.fill(new Ellipse2D.Double(dx - 3, dy - 3, 6, 6));
            }
        }
    }

    /** Lighting effect for a power disc: a couple of translucent halo rings in the owner colour. */
    private void drawPowerGlow(double dx, double dy, int big, Color owner) {
        var old = g2d.getStroke();
        Color bright = owner.brighter();
        for (int ring = 1; ring <= 2; ring++) {
            int pad = ring * 4;
            int alpha = 130 / ring;
            g2d.setStroke(new BasicStroke(2.0f));
            g2d.setColor(new Color(bright.getRed(), bright.getGreen(), bright.getBlue(), alpha));
            g2d.draw(new Ellipse2D.Double(dx - big / 2.0 - pad, dy - big / 2.0 - pad, big + 2.0 * pad, big + 2.0 * pad));
        }
        g2d.setStroke(old);
    }

    @Override
    public void drawRoundBegining() {
        g2d.setColor(gameSettings.getColorScheme().getStandardColor());
        g2d.fillRect(0, 0, animatedElementLength - animatedElementElapsed, height);
        g2d.fillRect(animatedElementLength + animatedElementElapsed, 0, animatedElementLength - animatedElementElapsed, height);
        g2d.fillRect(0, 0, width, animatedElementLength - animatedElementElapsed);
        g2d.fillRect(0, animatedElementLength + animatedElementElapsed, width, animatedElementLength - animatedElementElapsed);

        if (animatedElementElapsed > animatedElementLength) {
            animationElementEnd = animationEnd;
        }
    }

    @Override
    public void drawRoundEnding() {
        g2d.setColor(gameSettings.getColorScheme().getStandardColor());
        g2d.fillRect(0, 0, animatedElementElapsed, height);
        g2d.fillRect(width - animatedElementElapsed, 0, animatedElementElapsed, height);
        g2d.fillRect(0, 0, width, animatedElementElapsed);
        g2d.fillRect(0, width - animatedElementElapsed, height, animatedElementElapsed);

        if (animatedElementElapsed > animatedElementLength) {
            animationElementEnd = animationEnd;
        }
    }

    @Override
    public void initializeLayout() {
    }
}
