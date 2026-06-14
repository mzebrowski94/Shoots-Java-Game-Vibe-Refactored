// src/test/java/pl/mzebrows/shoots/entity/EntityTest.java
package pl.mzebrows.shoots.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** Verifies entity reset semantics and render interpolation between previous and current state. */
class EntityTest {

    @Test
    void resetClearsAllStateForPoolReuse() {
        var e = new Entity();
        e.setType(EntityType.DISC);
        e.setActive(true);
        e.setX(10);
        e.setY(20);
        e.setBounces(3);
        e.setMovementStrategy(new BounceMovementStrategy());

        e.reset();

        assertThat(e.getType()).isNull();
        assertThat(e.isActive()).isFalse();
        assertThat(e.getX()).isZero();
        assertThat(e.getY()).isZero();
        assertThat(e.getBounces()).isZero();
        assertThat(e.getOwnerId()).isEqualTo(-1);
        assertThat(e.getDirectionX()).isEqualTo(1);
        assertThat(e.getMovementStrategy()).isNull();
    }

    @Test
    void snapshotCopiesCurrentPositionIntoPreviousFields() {
        var e = new Entity();
        e.setX(5);
        e.setY(7);

        e.snapshot();
        e.setX(9);
        e.setY(11);

        assertThat(e.getPrevX()).isEqualTo(5);
        assertThat(e.getPrevY()).isEqualTo(7);
    }

    @Test
    void interpolatesPositionByAlphaBetweenPrevAndCurrent() {
        var e = new Entity();
        e.setX(0);
        e.setY(0);
        e.snapshot();        // prev = (0,0)
        e.setX(10);
        e.setY(20);          // current = (10,20)

        assertThat(e.interpolatedX(0.0)).isEqualTo(0.0);
        assertThat(e.interpolatedX(1.0)).isEqualTo(10.0);
        assertThat(e.interpolatedX(0.5)).isCloseTo(5.0, within(1e-9));
        assertThat(e.interpolatedY(0.25)).isCloseTo(5.0, within(1e-9));
    }
}
