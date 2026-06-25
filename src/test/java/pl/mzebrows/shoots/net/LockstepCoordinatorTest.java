// src/test/java/pl/mzebrows/shoots/net/LockstepCoordinatorTest.java
package pl.mzebrows.shoots.net;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.mzebrows.shoots.world.PlayWorld;

/** The lockstep gate: complete-in-order release, stalling on a missing slot, out-of-order submission. */
class LockstepCoordinatorTest {

    private TickInput in(PlayWorld.AimInput aim) {
        return new TickInput(aim, false);
    }

    @Test
    void gatesUntilEverySlotHasReported() {
        var coord = new LockstepCoordinator(2, 0);
        coord.submit(0, 0, in(PlayWorld.AimInput.LEFT));

        assertThat(coord.isComplete(0)).isFalse();
        assertThat(coord.tryRelease()).isNull();

        coord.submit(1, 0, in(PlayWorld.AimInput.RIGHT));
        assertThat(coord.isComplete(0)).isTrue();

        InputFrame f = coord.tryRelease();
        assertThat(f).isNotNull();
        assertThat(f.frame()).isZero();
        assertThat(f.slot(0).aim()).isEqualTo(PlayWorld.AimInput.LEFT);
        assertThat(f.slot(1).aim()).isEqualTo(PlayWorld.AimInput.RIGHT);
        // cursor advanced: nothing left to release
        assertThat(coord.nextFrameToRelease()).isEqualTo(1);
        assertThat(coord.tryRelease()).isNull();
    }

    @Test
    void holdsALaterFrameUntilEarlierOnesAreReleased() {
        var coord = new LockstepCoordinator(2, 0);
        // Frame 1 is fully reported, but frame 0 is not -> nothing may release (strict ordering).
        coord.submit(0, 1, in(PlayWorld.AimInput.LEFT));
        coord.submit(1, 1, in(PlayWorld.AimInput.LEFT));
        assertThat(coord.tryRelease()).isNull();

        // Complete frame 0 -> frame 0 then frame 1 release in order.
        coord.submit(0, 0, in(PlayWorld.AimInput.NONE));
        coord.submit(1, 0, in(PlayWorld.AimInput.NONE));
        assertThat(coord.tryRelease().frame()).isZero();
        assertThat(coord.tryRelease().frame()).isEqualTo(1);
        assertThat(coord.tryRelease()).isNull();
    }

    @Test
    void acceptsSlotsOutOfOrderAndIgnoresLateSubmissions() {
        var coord = new LockstepCoordinator(3, 0);
        coord.submit(2, 0, in(PlayWorld.AimInput.RIGHT));
        coord.submit(0, 0, in(PlayWorld.AimInput.LEFT));
        coord.submit(1, 0, in(PlayWorld.AimInput.NONE));

        InputFrame f = coord.tryRelease();
        assertThat(f.slots()).isEqualTo(3);
        assertThat(f.slot(2).aim()).isEqualTo(PlayWorld.AimInput.RIGHT);

        // A submission for an already-released frame is ignored (cannot change the timeline).
        coord.submit(0, 0, in(PlayWorld.AimInput.RIGHT));
        assertThat(coord.tryRelease()).isNull();
        assertThat(coord.nextFrameToRelease()).isEqualTo(1);
    }

    @Test
    void rejectsOutOfRangeSlot() {
        var coord = new LockstepCoordinator(2, 3);
        assertThat(coord.inputDelayFrames()).isEqualTo(3);
        try {
            coord.submit(2, 0, in(PlayWorld.AimInput.NONE));
            assertThat(false).as("expected IllegalArgumentException").isTrue();
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }
}
