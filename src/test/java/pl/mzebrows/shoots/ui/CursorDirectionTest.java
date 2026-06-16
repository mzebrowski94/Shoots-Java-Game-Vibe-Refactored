// src/test/java/pl/mzebrows/shoots/ui/CursorDirectionTest.java
package pl.mzebrows.shoots.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import pl.mzebrows.shoots.entity.BounceMovementStrategy;
import pl.mzebrows.shoots.entity.Entity;

/**
 * Guards that the aim-cursor direction vector used by {@code GameScreen.drawCursors}
 * (dir = (-sin θ, cos θ) in standard math, i.e. sin/cos of -θ) matches the disc travel direction, so
 * the drawn arrow points exactly where a fired disc actually goes.
 */
class CursorDirectionTest {

    /** The same direction the cursor arrow uses: components of the disc movement for a given angle. */
    private static double[] cursorDir(double angleDeg) {
        double a = Math.toRadians(-angleDeg);
        return new double[]{Math.sin(a), Math.cos(a)};
    }

    /** The actual disc step direction from BounceMovementStrategy for unit speed, dir +1/+1. */
    private static double[] discStep(double angleDeg) {
        var e = new Entity();
        e.setX(0);
        e.setY(0);
        e.setAngle(angleDeg);
        e.setMoveSpeed(1.0);
        e.setDirectionX(1);
        e.setDirectionY(1);
        new BounceMovementStrategy().move(e);
        return new double[]{e.getX(), e.getY()};
    }

    @Test
    void cursorDirectionMatchesDiscTravelForVariousAngles() {
        for (double angle = -170; angle <= 170; angle += 17) {
            double[] cursor = cursorDir(angle);
            double[] disc = discStep(angle);
            assertThat(cursor[0]).isCloseTo(disc[0], within(1e-9));
            assertThat(cursor[1]).isCloseTo(disc[1], within(1e-9));
        }
    }

    @Test
    void angleZeroPointsAlongPositiveY() {
        // Disc convention: angle 0 -> x += 0, y += 1.
        double[] cursor = cursorDir(0);
        assertThat(cursor[0]).isCloseTo(0.0, within(1e-9));
        assertThat(cursor[1]).isCloseTo(1.0, within(1e-9));
    }
}
