// src/main/java/pl/mzebrows/shoots/world/BlockHitEffect.java
package pl.mzebrows.shoots.world;

/**
 * Transient "block hit" flash for a single wall tile, decoupled from AWT. Replicates the legacy
 * {@code LightEffect}: a hit first ramps the block up to light grey, then fades the disc owner's
 * colour out over the tile before disappearing.
 *
 * <p>Mutable and reusable: {@link PlayWorld} restarts the effect in place when the same tile is hit
 * again. The renderer reads {@link #phase()} plus {@link #greyLevel()} / {@link #colorAlpha()} to
 * pick the overlay colour each frame, so this class never references {@code Graphics2D}.
 */
public final class BlockHitEffect {

    /** Animation phase: GREY ramps up to light grey, FADE fades the disc colour out, DONE is finished. */
    public enum Phase { GREY, FADE, DONE }

    // Legacy LightEffect constants (per-tick steps).
    private static final int GREY_STEP = 10;
    private static final int GREY_MAX = 170;
    private static final int FADE_START_ALPHA = 230;
    private static final int FADE_STEP = 10;
    private static final int FADE_MIN_ALPHA = 11;

    private final int tileX;
    private final int tileY;

    private int ownerId;
    private Phase phase;
    private int greyLevel;
    private int colorAlpha;

    public BlockHitEffect(int tileX, int tileY, int ownerId) {
        this.tileX = tileX;
        this.tileY = tileY;
        restart(ownerId);
    }

    /** (Re)starts the flash for a fresh hit by {@code ownerId}, reusing this instance. */
    public void restart(int ownerId) {
        this.ownerId = ownerId;
        this.phase = Phase.GREY;
        this.greyLevel = 0;
        this.colorAlpha = FADE_START_ALPHA;
    }

    /** Advances the flash one tick; mirrors the legacy two-phase grey-ramp then colour-fade. */
    public void advance() {
        switch (phase) {
            case GREY -> {
                greyLevel += GREY_STEP;
                if (greyLevel >= GREY_MAX) {
                    greyLevel = GREY_MAX;
                    phase = Phase.FADE;
                }
            }
            case FADE -> {
                colorAlpha -= FADE_STEP;
                if (colorAlpha <= FADE_MIN_ALPHA) {
                    phase = Phase.DONE;
                }
            }
            case DONE -> { /* nothing */ }
        }
    }

    public boolean isDone() {
        return phase == Phase.DONE;
    }

    public int tileX() { return tileX; }

    public int tileY() { return tileY; }

    public int ownerId() { return ownerId; }

    public Phase phase() { return phase; }

    /** Grey channel value (0..170) during the GREY phase. */
    public int greyLevel() { return greyLevel; }

    /** Disc-colour alpha (down to ~11) during the FADE phase. */
    public int colorAlpha() { return colorAlpha; }
}
