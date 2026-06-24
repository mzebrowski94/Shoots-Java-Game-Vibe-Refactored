// src/main/java/pl/mzebrows/shoots/render/object/RenderFrame.java
package pl.mzebrows.shoots.render.object;

/**
 * Mutable per-frame render state shared by the object renderers: the loop interpolation {@code alpha}
 * plus the dash/rotation animation phases advanced once per drawn frame. Centralised so every object
 * animates off the same clock (dashed strokes and the base rings stay in sync).
 */
public final class RenderFrame {

    /** Phase advance per frame for dashed strokes (capture points + lasers). */
    private static final float DASH_ADVANCE = 0.12f;

    private double alpha;
    private float dashPhase;
    private int baseRotation;

    /** Sets this frame's interpolation factor and advances the shared animation phases by one frame. */
    public void prepare(double alpha) {
        this.alpha = alpha;
        this.dashPhase += DASH_ADVANCE;
        this.baseRotation++;
    }

    public double alpha() {
        return alpha;
    }

    public float dashPhase() {
        return dashPhase;
    }

    public int baseRotation() {
        return baseRotation;
    }
}
