package pl.mzebrows.shoots.render.object;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import pl.mzebrows.shoots.config.GameConfig;
import pl.mzebrows.shoots.config.GameConfigLoader;
import pl.mzebrows.shoots.config.GraphicsConfig;
import pl.mzebrows.shoots.world.PlayWorld;

/**
 * Smoke test for the per-object renderer layer: drives a real headless {@link PlayWorld} through the full
 * ordered renderer list onto an offscreen image and asserts a non-blank frame is produced. With
 * {@code -Drender.dump.dir=...} it also writes a PNG for visual inspection.
 */
class MapObjectRenderersSmokeTest {

    @Test
    void rendererPipelineDrawsANonBlankFrame() throws Exception {
        GameConfig config = GameConfigLoader.load();
        GraphicsConfig gfx = GameConfigLoader.loadGraphics();
        PlayWorld world = new PlayWorld(config, 42L);

        // Populate discs/lasers so every renderer has something to draw.
        for (int p = 0; p < world.playerCount(); p++) {
            world.applyInput(p, PlayWorld.AimInput.RIGHT, false);
            world.fire(p);
        }
        for (int i = 0; i < 8; i++) {
            world.step();
        }

        int size = config.grid().playfieldPixels();
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        Color background = config.palette().background().toAwt();
        g.setColor(background);
        g.fillRect(0, 0, size, size);

        List<MapObjectRenderer> renderers = List.of(
                new WallRenderer(config.palette().standard().toAwt()),
                new BlockHitRenderer(),
                new CapturePointRenderer(),
                new BaseRenderer(gfx.objects()),
                new CursorRenderer(gfx.objects()),
                new LaserRenderer(),
                new DiscRenderer(gfx.objects()));

        RenderFrame frame = new RenderFrame();
        frame.prepare(0.5);
        for (MapObjectRenderer r : renderers) {
            r.render(g, world, frame);
        }
        g.dispose();

        int bgRgb = background.getRGB();
        int nonBackground = 0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (img.getRGB(x, y) != bgRgb) {
                    nonBackground++;
                }
            }
        }
        assertThat(nonBackground).isGreaterThan(1000);

        String dumpDir = System.getProperty("render.dump.dir");
        if (dumpDir != null) {
            ImageIO.write(img, "png", new File(dumpDir, "render_parity.png"));
        }
    }
}
