// src/main/java/pl/mzebrows/shoots/loop/FixedTimestep.java
package pl.mzebrows.shoots.loop;

/**
 * AWT-free fixed-timestep accumulator: decouples simulation steps from frame timing.
 *
 * <p>Each frame, the caller feeds the wall-clock delta (nanoseconds) into {@link #accumulate(long)};
 * the elapsed time is clamped to {@link #maxFrameNanos} (spiral-of-death guard) and added to an
 * accumulator. {@link #consumeStep()} then pops one fixed step at a time until the accumulator drops
 * below the step size, and {@link #alpha()} reports how far we are into the next, unfinished step so
 * the renderer can interpolate between the previous and current simulation state.
 */
public final class FixedTimestep {

    private final long stepNanos;
    private final long maxFrameNanos;

    private long accumulator;

    /**
     * @param stepNanos     duration of one fixed simulation step, in nanoseconds (must be positive)
     * @param maxFrameNanos upper clamp on a single frame's elapsed time, in nanoseconds
     *                      (must be {@code >= stepNanos}); caps catch-up work after a stall
     */
    public FixedTimestep(long stepNanos, long maxFrameNanos) {
        if (stepNanos <= 0) {
            throw new IllegalArgumentException("stepNanos must be positive: " + stepNanos);
        }
        if (maxFrameNanos < stepNanos) {
            throw new IllegalArgumentException(
                    "maxFrameNanos (" + maxFrameNanos + ") must be >= stepNanos (" + stepNanos + ")");
        }
        this.stepNanos = stepNanos;
        this.maxFrameNanos = maxFrameNanos;
    }

    /** Builds a fixed-timestep from an updates-per-second rate and a max catch-up multiplier. */
    public static FixedTimestep ofRate(int updatesPerSecond, int maxCatchUpSteps) {
        if (updatesPerSecond <= 0) {
            throw new IllegalArgumentException("updatesPerSecond must be positive: " + updatesPerSecond);
        }
        if (maxCatchUpSteps <= 0) {
            throw new IllegalArgumentException("maxCatchUpSteps must be positive: " + maxCatchUpSteps);
        }
        long step = 1_000_000_000L / updatesPerSecond;
        return new FixedTimestep(step, step * maxCatchUpSteps);
    }

    /** Adds (clamped) elapsed wall-clock time to the accumulator before stepping. */
    public void accumulate(long frameNanos) {
        if (frameNanos < 0) {
            return;
        }
        accumulator += Math.min(frameNanos, maxFrameNanos);
    }

    /**
     * Pops one fixed step if a whole step has accumulated.
     *
     * @return {@code true} if the caller should run one simulation update, {@code false} when drained
     */
    public boolean consumeStep() {
        if (accumulator >= stepNanos) {
            accumulator -= stepNanos;
            return true;
        }
        return false;
    }

    /** Render interpolation factor in {@code [0,1)}: fraction of the next step already elapsed. */
    public double alpha() {
        return (double) accumulator / stepNanos;
    }

    public long stepNanos() {
        return stepNanos;
    }

    public long maxFrameNanos() {
        return maxFrameNanos;
    }

    /** Remaining un-stepped time in the accumulator, in nanoseconds. */
    public long accumulatedNanos() {
        return accumulator;
    }
}
