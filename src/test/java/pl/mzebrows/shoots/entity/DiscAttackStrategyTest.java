// src/test/java/pl/mzebrows/shoots/entity/DiscAttackStrategyTest.java
package pl.mzebrows.shoots.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

/** Verifies the disc cap, spawn delegation, and exhaustion handling with a mocked spawner. */
class DiscAttackStrategyTest {

    private Entity source() {
        var e = new Entity();
        e.setX(50);
        e.setY(60);
        e.setAngle(135);
        e.setOwnerId(2);
        return e;
    }

    @Test
    void firesDiscFromSourcePositionAndAngle() {
        var spawner = mock(EntitySpawner.class);
        when(spawner.spawnDisc(50, 60, 135, 2, false)).thenReturn(new Entity());
        var strategy = new DiscAttackStrategy(3);

        boolean fired = strategy.attack(source(), spawner);

        assertThat(fired).isTrue();
        assertThat(strategy.activeDiscs()).isEqualTo(1);
        verify(spawner).spawnDisc(50, 60, 135, 2, false);
    }

    @Test
    void respectsConcurrentDiscCap() {
        var spawner = mock(EntitySpawner.class);
        when(spawner.spawnDisc(50, 60, 135, 2, false)).thenReturn(new Entity());
        var strategy = new DiscAttackStrategy(2);

        assertThat(strategy.attack(source(), spawner)).isTrue();
        assertThat(strategy.attack(source(), spawner)).isTrue();
        assertThat(strategy.attack(source(), spawner)).isFalse(); // capped

        assertThat(strategy.activeDiscs()).isEqualTo(2);
        verify(spawner, times(2)).spawnDisc(50, 60, 135, 2, false);
    }

    @Test
    void retiringADiscFreesASlot() {
        var spawner = mock(EntitySpawner.class);
        when(spawner.spawnDisc(50, 60, 135, 2, false)).thenReturn(new Entity());
        var strategy = new DiscAttackStrategy(1);

        assertThat(strategy.attack(source(), spawner)).isTrue();
        assertThat(strategy.attack(source(), spawner)).isFalse();

        strategy.onDiscRetired();
        assertThat(strategy.activeDiscs()).isZero();
        assertThat(strategy.attack(source(), spawner)).isTrue();
    }

    @Test
    void poolExhaustionDoesNotConsumeASlot() {
        var spawner = mock(EntitySpawner.class);
        when(spawner.spawnDisc(50, 60, 135, 2, false)).thenReturn(null);
        var strategy = new DiscAttackStrategy(3);

        assertThat(strategy.attack(source(), spawner)).isFalse();
        assertThat(strategy.activeDiscs()).isZero();
    }

    @Test
    void rejectsNonPositiveCap() {
        assertThatThrownBy(() -> new DiscAttackStrategy(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
