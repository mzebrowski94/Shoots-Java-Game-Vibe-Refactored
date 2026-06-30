// src/test/java/pl/mzebrows/shoots/loop/FixedTimestepTest.java
package pl.mzebrows.shoots.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Verifies the fixed-timestep accumulator stepping, clamping, and interpolation factor. */
class FixedTimestepTest {

    private static final long STEP = 10_000_000L;          // 10 ms
    private static final long MAX_FRAME = STEP * 5;         // catch up at most 5 steps

    @Test
    void consumesExactlyOneStepPerWholeStepAccumulated() {
        var ts = new FixedTimestep(STEP, MAX_FRAME);

        ts.accumulate(STEP * 3);

        int steps = drain(ts);
        assertThat(steps).isEqualTo(3);
        assertThat(ts.alpha()).isZero();
    }

    @Test
    void retainsRemainderAsInterpolationAlpha() {
        var ts = new FixedTimestep(STEP, MAX_FRAME);

        ts.accumulate(STEP + STEP / 4);   // 1.25 steps

        assertThat(ts.consumeStep()).isTrue();
        assertThat(ts.consumeStep()).isFalse();
        assertThat(ts.alpha()).isEqualTo(0.25, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void clampsLargeFrameToMaxFrameNanosToPreventSpiralOfDeath() {
        var ts = new FixedTimestep(STEP, MAX_FRAME);

        ts.accumulate(STEP * 1000);       // huge stall

        int steps = drain(ts);
        assertThat(steps).isEqualTo(5);   // clamped to MAX_FRAME / STEP
    }

    @Test
    void ignoresNegativeFrameDeltas() {
        var ts = new FixedTimestep(STEP, MAX_FRAME);

        ts.accumulate(-50);

        assertThat(ts.consumeStep()).isFalse();
        assertThat(ts.accumulatedNanos()).isZero();
    }

    @Test
    void ofRateComputesStepFromUpdatesPerSecond() {
        var ts = FixedTimestep.ofRate(100, 4);

        assertThat(ts.stepNanos()).isEqualTo(10_000_000L);
        assertThat(ts.maxFrameNanos()).isEqualTo(40_000_000L);
    }

    @Test
    void rejectsInvalidConstructorArguments() {
        assertThatThrownBy(() -> new FixedTimestep(0, STEP))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FixedTimestep(STEP, STEP - 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FixedTimestep.ofRate(0, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FixedTimestep.ofRate(60, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refundReturnsAConsumedStepSoStallTimeIsNotLost() {
        var ts = new FixedTimestep(STEP, MAX_FRAME);
        ts.accumulate(STEP * 2);

        assertThat(ts.consumeStep()).isTrue(); // pop one step...
        ts.refund();                           // ...but the sim could not advance, so give it back

        // The refunded step is available again: we can still drain two whole steps in total.
        assertThat(drain(ts)).isEqualTo(2);
    }

    @Test
    void refundIsCappedAtMaxFrameNanos() {
        var ts = new FixedTimestep(STEP, MAX_FRAME);
        ts.accumulate(MAX_FRAME);   // accumulator at the clamp
        ts.consumeStep();           // one step out -> 4 steps remain
        ts.refund();
        ts.refund();                // refunding twice must not exceed the clamp

        assertThat(ts.accumulatedNanos()).isLessThanOrEqualTo(MAX_FRAME);
        assertThat(drain(ts)).isEqualTo(5);
    }

    private static int drain(FixedTimestep ts) {
        int steps = 0;
        while (ts.consumeStep()) {
            steps++;
        }
        return steps;
    }
}
