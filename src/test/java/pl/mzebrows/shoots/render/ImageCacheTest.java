// src/test/java/pl/mzebrows/shoots/render/ImageCacheTest.java
package pl.mzebrows.shoots.render;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Verifies classpath loading, caching, and graceful fallback of {@link ImageCache} (headless). */
class ImageCacheTest {

    @Test
    void loadsExistingResourceFromClasspath() {
        var cache = new ImageCache(null);   // headless: no GraphicsConfiguration

        var image = cache.get("images/game.png");

        assertThat(image).isPresent();
        assertThat(image.get().getWidth()).isPositive();
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    void cachesSoRepeatedGetReturnsSameInstance() {
        var cache = new ImageCache(null);

        var first = cache.get("images/game.png");
        var second = cache.get("images/game.png");

        assertThat(first).isPresent();
        assertThat(second).containsSame(first.get());
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    void missingResourceFallsBackToEmptyWithoutThrowing() {
        var cache = new ImageCache(null);

        var image = cache.get("images/does-not-exist.png");

        assertThat(image).isEmpty();
        assertThat(cache.size()).isZero();
    }

    @Test
    void clearDropsCachedImages() {
        var cache = new ImageCache(null);
        cache.get("images/game.png");

        cache.clear();

        assertThat(cache.size()).isZero();
    }
}
