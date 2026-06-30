// src/test/java/pl/mzebrows/shoots/system/DiscSpawnerTest.java
package pl.mzebrows.shoots.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.mzebrows.shoots.config.DiscConfig;

/** Verifies disc spawning from the pool, config-driven stats, exhaustion, and retirement. */
class DiscSpawnerTest {

    private static final DiscConfig DISC = new DiscConfig(18, 10, 2.0, 7, 3, 3, 4);

    private ObjectPool<Entity> pool(int capacity) {
        return new ObjectPool<>(capacity, Entity::new, Entity::reset);
    }

    @Test
    void spawnsActiveDiscWithConfigDrivenStats() {
        var combat = new DiscSpawner(pool(2), DISC);

        Entity disc = combat.spawnDisc(100, 200, 45, 3);

        assertThat(disc).isNotNull();
        assertThat(disc.getType()).isEqualTo(EntityType.DISC);
        assertThat(disc.isActive()).isTrue();
        assertThat(disc.getX()).isEqualTo(100);
        assertThat(disc.getY()).isEqualTo(200);
        assertThat(disc.getAngle()).isEqualTo(45);
        assertThat(disc.getOwnerId()).isEqualTo(3);
        assertThat(disc.getMoveSpeed()).isEqualTo(2.0);
        assertThat(disc.getRadius()).isEqualTo(18);
        assertThat(disc.getMaxBounces()).isEqualTo(7);
    }

    @Test
    void returnsNullWhenPoolExhausted() {
        var combat = new DiscSpawner(pool(1), DISC);

        assertThat(combat.spawnDisc(0, 0, 0, 0)).isNotNull();
        assertThat(combat.spawnDisc(0, 0, 0, 0)).isNull();
    }

    @Test
    void isSpentReflectsBounceBudget() {
        var combat = new DiscSpawner(pool(1), DISC);
        Entity disc = combat.spawnDisc(0, 0, 0, 0);

        assertThat(combat.isSpent(disc)).isFalse();
        disc.setBounces(7);
        assertThat(combat.isSpent(disc)).isTrue();
    }

    @Test
    void retireDeactivatesAndReturnsDiscToPool() {
        var pool = pool(1);
        var combat = new DiscSpawner(pool, DISC);
        Entity disc = combat.spawnDisc(0, 0, 0, 0);
        assertThat(pool.available()).isZero();

        combat.retire(disc);

        assertThat(disc.isActive()).isFalse();
        assertThat(pool.available()).isEqualTo(1);
    }
}
