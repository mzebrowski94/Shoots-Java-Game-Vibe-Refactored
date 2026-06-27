// pl/mzebrows/shoots/ui/GameScreen.java
package pl.mzebrows.shoots.ui;

import lombok.Getter;
import lombok.Setter;
import pl.mzebrows.shoots.app.GameSettings;
import pl.mzebrows.shoots.config.ObjectStyle;
import pl.mzebrows.shoots.render.object.BaseRenderer;
import pl.mzebrows.shoots.render.object.BlockHitRenderer;
import pl.mzebrows.shoots.render.object.CapturePointRenderer;
import pl.mzebrows.shoots.render.object.CursorRenderer;
import pl.mzebrows.shoots.render.object.DiscRenderer;
import pl.mzebrows.shoots.render.object.DisruptionRenderer;
import pl.mzebrows.shoots.render.object.LaserRenderer;
import pl.mzebrows.shoots.render.object.MapObjectRenderer;
import pl.mzebrows.shoots.render.object.RenderFrame;
import pl.mzebrows.shoots.render.object.WallRenderer;
import pl.mzebrows.shoots.world.PlayWorld;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;

/**
 * Main play-field panel. Owns the ordered list of {@link MapObjectRenderer}s and drives them each frame
 * over the live {@link PlayWorld}; every object's look lives in its own renderer (no per-object draw code
 * here). Round-phase transitions and the menu overlay remain panel concerns and stay in this class.
 */
public class GameScreen extends GameCanvas {

    @Getter @Setter private GameMenu menuLayout;

    /** Live simulation pushed by the renderer each frame (null when not playing). */
    private PlayWorld world;
    private double alpha;

    /** Non-null while a remote peer has paused an online match: the centred "PLAYER n PAUSED" banner (#3). */
    private volatile String onlinePauseNotice;

    /** Per-frame render state (interpolation factor + animation phases) shared by the object renderers. */
    private final RenderFrame frame = new RenderFrame();

    /** Map-object renderers in back-to-front draw order; extend this list to add a new map object. */
    private final List<MapObjectRenderer> objectRenderers;

    GameScreen(GameSettings gameSettings) {
        super(gameSettings);

        width = gameSettings.getDefaultWidth();
        height = gameSettings.getDefaultHeight();
        menuLayout = new GameMenu(gameSettings);

        setPreferredSize(new Dimension(width, height));
        animatedElementLength = width / 2;

        ObjectStyle style = gameSettings.getGraphics().objects();
        Color wallColor = gameSettings.getColorScheme().getStandardColor();
        this.objectRenderers = List.of(
                new WallRenderer(wallColor),
                new BlockHitRenderer(),
                new CapturePointRenderer(),
                new BaseRenderer(style),
                new DisruptionRenderer(),
                new CursorRenderer(style),
                new LaserRenderer(),
                new DiscRenderer(style));
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
                    if (onlinePauseNotice != null) {
                        drawOnlinePauseNotice();
                    }
                }
                g2d.dispose();
            } while (strategy.contentsRestored());
            strategy.show();
        } while (strategy.contentsLost());
    }

    @Override
    public void drawRoundPaused() {
        // The menu-standard tint is near-transparent: it is meant to sit OVER a frozen game frame so the
        // field shows through (pause / win-score screens). With per-frame buffer re-acquire the back buffer
        // is not guaranteed to hold the last frame, so when a game exists we redraw the frozen field and
        // overlay the tint. On a fresh start (no round yet) there is nothing behind the menu, so we clear
        // to an opaque background instead.
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
        // Advance the shared animation clock once, then draw each map-object layer in order.
        frame.prepare(alpha);
        for (MapObjectRenderer renderer : objectRenderers) {
            renderer.render(g2d, world, frame);
        }
    }

    /** Sets (or clears, with {@code null}) the centred banner shown when a remote peer pauses online (#3). */
    public void setOnlinePauseNotice(String notice) {
        this.onlinePauseNotice = notice;
    }

    /** Dims the frozen field and centres the "PLAYER n PAUSED" banner so paused peers know why play stopped. */
    private void drawOnlinePauseNotice() {
        g2d.setColor(gameSettings.getColorScheme().getMenuStandardColor());
        g2d.fillRect(0, 0, width, height);
        g2d.setColor(gameSettings.getColorScheme().getBackgroundFontColor());
        g2d.setFont(gameSettings.getGameFont().deriveFont(28f));
        var fm = g2d.getFontMetrics();
        int x = (width - fm.stringWidth(onlinePauseNotice)) / 2;
        g2d.drawString(onlinePauseNotice, x, height / 2);
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
