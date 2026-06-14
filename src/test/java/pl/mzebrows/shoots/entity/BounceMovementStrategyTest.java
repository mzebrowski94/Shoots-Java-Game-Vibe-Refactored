// src/test/java/pl/mzebrows/shoots/entity/BounceMovementStrategyTest.java
package pl.mzebrows.shoots.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** Verifies the disc movement matches the legacy angle convention (sin(-a) on X, cos(-a) on Y). */
class BounceMovementStrategyTest {

    private final MovementStrategy strategy = new BounceMovementStrategy();

    private Entity discAt(double x, double y, double angle, double speed) {
        var e = new Entity();
        e.setX(x);
        e.setY(y);
        e.setAngle(angle);
        e.setMoveSpeed(speed);
        e.setDirectionX(1);
        e.setDirectionY(1);
        return e;
    }

    @Test
    void zeroAngleMovesAlongPositiveY() {
        var e = discAt(0, 0, 0, 2);

        strategy.move(e);

        assertThat(e.getX()).isCloseTo(0.0, within(1e-9));
        assertThat(e.getY()).isCloseTo(2.0, within(1e-9));
    }

    @Test
    void ninetyDegreesMovesAlongNegativeX() {
        var e = discAt(0, 0, 90, 2);

        strategy.move(e);

        assertThat(e.getX()).isCloseTo(-2.0, within(1e-9));
        assertThat(e.getY()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void reflectedDirectionFlipsAxisTravel() {
        var e = discAt(0, 0, 90, 2);
        e.setDirectionX(-1); // simulate post-bounce reflection on X

        strategy.move(e);

        assertThat(e.getX()).isCloseTo(2.0, within(1e-9));
    }
}
