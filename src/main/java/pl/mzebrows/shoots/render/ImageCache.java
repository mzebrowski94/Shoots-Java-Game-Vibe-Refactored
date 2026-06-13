// src/main/java/pl/mzebrows/shoots/render/ImageCache.java
package pl.mzebrows.shoots.render;

import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads images once from the classpath and caches them, converting to a hardware-friendly
 * compatible image when a {@link GraphicsConfiguration} is supplied. Missing or unreadable
 * resources are logged and skipped (graceful fallback), never thrown into the game loop.
 */
public final class ImageCache {

    private static final Logger log = LoggerFactory.getLogger(ImageCache.class);

    private final GraphicsConfiguration gc;
    private final Map<String, BufferedImage> cache = new HashMap<>();

    /** @param gc graphics config used to create compatible images; {@code null} in headless contexts */
    public ImageCache(GraphicsConfiguration gc) {
        this.gc = gc;
    }

    /**
     * Returns the cached image for a classpath resource, loading it on first request.
     *
     * @param resourcePath classpath-relative path, e.g. {@code "images/game.png"}
     * @return the image, or empty if it could not be loaded
     */
    public Optional<BufferedImage> get(String resourcePath) {
        if (cache.containsKey(resourcePath)) {
            return Optional.ofNullable(cache.get(resourcePath));
        }
        BufferedImage loaded = load(resourcePath);
        cache.put(resourcePath, loaded);
        return Optional.ofNullable(loaded);
    }

    private BufferedImage load(String resourcePath) {
        log.debug("Loading image resource: {}", resourcePath);
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(resourcePath)) {
            if (in == null) {
                log.error("Image resource not found on classpath: {}", resourcePath);
                return null;
            }
            BufferedImage raw = ImageIO.read(in);
            if (raw == null) {
                log.error("Image resource could not be decoded: {}", resourcePath);
                return null;
            }
            return toCompatible(raw);
        } catch (IOException e) {
            log.error("Failed to load image resource: {}", resourcePath, e);
            return null;
        }
    }

    private BufferedImage toCompatible(BufferedImage raw) {
        if (gc == null) {
            return raw;
        }
        BufferedImage compatible = gc.createCompatibleImage(
                raw.getWidth(), raw.getHeight(), Transparency.TRANSLUCENT);
        var g = compatible.createGraphics();
        try {
            g.drawImage(raw, 0, 0, null);
        } finally {
            g.dispose();
        }
        return compatible;
    }

    /** Number of resolved (non-null) images currently held. */
    public int size() {
        return (int) cache.values().stream().filter(v -> v != null).count();
    }

    /** Drops all cached images. */
    public void clear() {
        cache.clear();
    }

    /** Convenience for window icons: the underlying {@link Image} if present. */
    public Optional<Image> icon(String resourcePath) {
        return get(resourcePath).map(img -> (Image) img);
    }
}
