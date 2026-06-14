// src/test/java/pl/mzebrows/shoots/system/MovementSystemTest.java
package pl.mzebrows.shoots.system;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import pl.mzebrows.shoots.entity.Entity;
import pl.mzebrows.shoots.entity.MovementStrategy;

/** Verifies the movement system dispatches to injected strategies only for active movable entities. */
class MovementSystemTest {

    private final MovementSystem system = new MovementSystem();

    private Entity active(MovementStrategy strategy) {
        var e = new Entity();
        e.setActive(true);
        e.setMovementStrategy(strategy);
        return e;
    }

    @Test
    void invokesStrategyForEachActiveMovableEntity() {
        var s1 = Mockito.mock(MovementStrategy.class);
        var s2 = Mockito.mock(MovementStrategy.class);
        var e1 = active(s1);
        var e2 = active(s2);

        system.update(List.of(e1, e2));

        verify(s1).move(e1);
        verify(s2).move(e2);
    }

    @Test
    void skipsInactiveEntities() {
        var strategy = Mockito.mock(MovementStrategy.class);
        var e = active(strategy);
        e.setActive(false);

        system.update(List.of(e));

        verify(strategy, never()).move(Mockito.any());
    }

    @Test
    void skipsEntitiesWithoutMovementStrategy() {
        var e = new Entity();
        e.setActive(true); // no strategy injected

        system.update(List.of(e)); // must not throw
    }

    @Test
    void snapshotsPreviousPositionBeforeMoving() {
        var e = active(entity -> entity.setX(entity.getX() + 5));
        e.setX(10);

        system.update(List.of(e));

        assertThat(e.getPrevX()).isEqualTo(10); // snapshot taken pre-move
        assertThat(e.getX()).isEqualTo(15);
    }
}
