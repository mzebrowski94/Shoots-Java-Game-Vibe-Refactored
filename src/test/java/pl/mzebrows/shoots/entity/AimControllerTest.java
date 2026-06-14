// src/test/java/pl/mzebrows/shoots/entity/AimControllerTest.java
package pl.mzebrows.shoots.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** Graphics-free tests for clamped aiming rotation and shot-angle derivation. */
class AimControllerTest {

    @Test
    void currentAngleStartsAtBaseDirection() {
        var aim = new AimController(180, 110, 0.5);
        assertThat(aim.currentAngle()).isEqualTo(180);
    }

    @Test
    void rotateLeftAndRightAdjustAngleBySignedStep() {
        var aim = new AimController(0, 110, 0.5);

        aim.rotateLeft(1);   // +0.5
        assertThat(aim.currentAngle()).isCloseTo(0.5, within(1e-9));

        aim.rotateRight(1);  // -0.5 -> back to 0
        assertThat(aim.currentAngle()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void moveUnitSignInvertsRotationDirection() {
        var aim = new AimController(0, 110, 1.0);

        aim.rotateLeft(-1); // moveUnit -1 flips to -1.0
        assertThat(aim.getRotation()).isCloseTo(-1.0, within(1e-9));
    }

    @Test
    void rotationIsClampedToLimit() {
        var aim = new AimController(0, 5, 2.0);

        for (int i = 0; i < 100; i++) {
            aim.rotateLeft(1);
        }
        assertThat(aim.getRotation()).isEqualTo(5);

        for (int i = 0; i < 100; i++) {
            aim.rotateRight(1);
        }
        assertThat(aim.getRotation()).isEqualTo(-5);
    }

    @Test
    void resetReturnsToNeutralDirection() {
        var aim = new AimController(90, 110, 1.0);
        aim.rotateLeft(5);

        aim.reset();

        assertThat(aim.getRotation()).isZero();
        assertThat(aim.currentAngle()).isEqualTo(90);
    }

    @Test
    void rejectsInvalidConstructorArgs() {
        assertThatThrownBy(() -> new AimController(0, -1, 1.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AimController(0, 110, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
