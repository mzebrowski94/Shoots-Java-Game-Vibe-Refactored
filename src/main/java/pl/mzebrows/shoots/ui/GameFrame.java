// pl/mzebrows/shoots/ui/GameFrame.java
package pl.mzebrows.shoots.ui;

import pl.mzebrows.shoots.app.GameSettings;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import javax.swing.JFrame;
import javax.swing.JPanel;

import lombok.Getter;

/**
 * Game window: hosts the play screen, top round-timer counter, and side score panel.
 *
 * <p>The window is decoupled from the grid/pixel sizing and is fully resizable (#1): the three panels are
 * laid out at a fixed LOGICAL resolution inside a {@code gamePanel}, which is centred in the frame by a
 * {@link GridBagLayout}; on every resize the panels are rescaled by a single uniform factor (preserving
 * aspect, letterboxing the leftover space) and each panel renders its logical content through that scale.
 * The simulation is untouched -- only the AWT view scales -- so gameplay and online determinism are unaffected.
 */
public class GameFrame extends JFrame {

    @Getter private final GameScreen gameScreen;
    @Getter private final GameCounter gameCounter;
    @Getter private final GamePointer gamePointer;

    /** Holds the three panels at the logical layout; centred in the frame so spare space letterboxes. */
    private final JPanel gamePanel;

    // Logical (design) dimensions, in pixels, that the playfield + panels are authored at.
    private final int logicalScreenW;
    private final int logicalScreenH;
    private final int logicalCounterH;
    private final int logicalPointerW;
    private final int logicalTotalW;
    private final int logicalTotalH;

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

        this.logicalScreenW = gameSettings.getDefaultWidth();
        this.logicalScreenH = gameSettings.getDefaultHeight();
        this.logicalCounterH = gameSettings.getDefaultCounterHeight();
        this.logicalPointerW = gameSettings.getDefaultPointerWidth();
        this.logicalTotalW = logicalScreenW + logicalPointerW;
        this.logicalTotalH = logicalCounterH + logicalScreenH;

        // The three panels live inside gamePanel at the logical layout; gamePanel is centred by the frame.
        gamePanel = new JPanel(new BorderLayout());
        gamePanel.setBackground(gameSettings.getColorScheme().getBackgroundColor());
        gamePanel.add(gameScreen, BorderLayout.CENTER);
        gamePanel.add(gameCounter, BorderLayout.NORTH);
        gamePanel.add(gamePointer, BorderLayout.EAST);
        gamePanel.setPreferredSize(new Dimension(logicalTotalW, logicalTotalH));

        Container content = getContentPane();
        content.setBackground(gameSettings.getColorScheme().getBackgroundColor());
        content.setLayout(new GridBagLayout()); // single child, default constraints -> centred = letterbox
        content.add(gamePanel);

        this.setFocusable(true);
        // Resizable now: the BufferStrategy is recreated per panel when its size changes (GameCanvas).
        setResizable(true);
        setMinimumSize(new Dimension(logicalTotalW / 3, logicalTotalH / 3));
        if (icon != null) {
            setIconImage(icon);
        }
        addKeyListener(gameSettings.getInputBridge());

        setIgnoreRepaint(true);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        pack();
        fitAndCenterToScreen();

        // Rescale the panels whenever the window size changes (uniform scale + letterbox).
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                rescale();
            }
        });

        setVisible(true);
        gameScreen.createBufferStrategy(2);

        gameScreen.initializeGraphics();
        gameCounter.initializeGraphics();
        gamePointer.initializeGraphics();

        rescale(); // establish the initial scale to the packed size
    }

    /**
     * Recomputes the uniform scale from the current content size and applies it to the panels (preferred
     * sizes + per-panel render scale), then revalidates so the layout re-centres the scaled gamePanel.
     */
    private void rescale() {
        Container content = getContentPane();
        int availW = content.getWidth();
        int availH = content.getHeight();
        if (availW <= 0 || availH <= 0) {
            return;
        }
        double scale = Math.min((double) availW / logicalTotalW, (double) availH / logicalTotalH);
        if (scale <= 0.0) {
            return;
        }
        gameCounter.setRenderScale(scale);
        gamePointer.setRenderScale(scale);
        gameScreen.setRenderScale(scale);

        gameCounter.setPreferredSize(new Dimension(px(logicalTotalW, scale), px(logicalCounterH, scale)));
        gamePointer.setPreferredSize(new Dimension(px(logicalPointerW, scale), px(logicalScreenH, scale)));
        gameScreen.setPreferredSize(new Dimension(px(logicalScreenW, scale), px(logicalScreenH, scale)));
        gamePanel.setPreferredSize(new Dimension(px(logicalTotalW, scale), px(logicalTotalH, scale)));
        gamePanel.revalidate();
    }

    private static int px(int logical, double scale) {
        return (int) Math.round(logical * scale);
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
}
