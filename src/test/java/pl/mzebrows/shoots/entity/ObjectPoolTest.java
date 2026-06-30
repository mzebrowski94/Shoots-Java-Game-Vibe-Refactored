// src/test/java/pl/mzebrows/shoots/pool/ObjectPoolTest.java
package pl.mzebrows.shoots.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Verifies pool pre-allocation, acquire/release accounting, exhaustion, and the reset hook. */
class ObjectPoolTest {

    private static final class Box {
        int value;
    }

    @Test
    void preAllocatesCapacityInstancesUpFront() {
        var created = new AtomicInteger();
        var pool = new ObjectPool<>(4, () -> {
            created.incrementAndGet();
            return new Box();
        }, _ -> {});

        assertThat(created).hasValue(4);
        assertThat(pool.capacity()).isEqualTo(4);
        assertThat(pool.available()).isEqualTo(4);
        assertThat(pool.inUse()).isZero();
    }

    @Test
    void acquireDoesNotAllocateNewInstances() {
        var created = new AtomicInteger();
        var pool = new ObjectPool<>(2, () -> {
            created.incrementAndGet();
            return new Box();
        }, _ -> {});

        pool.acquire();
        pool.acquire();

        assertThat(created).hasValue(2); // no allocation beyond pre-allocation
        assertThat(pool.inUse()).isEqualTo(2);
    }

    @Test
    void returnsNullWhenExhausted() {
        var pool = new ObjectPool<>(1, Box::new, _ -> {});

        assertThat(pool.acquire()).isNotNull();
        assertThat(pool.acquire()).isNull();
        assertThat(pool.available()).isZero();
    }

    @Test
    void releaseRunsResetHookAndMakesInstanceAvailableAgain() {
        var pool = new ObjectPool<Box>(1, Box::new, b -> b.value = 0);

        var box = pool.acquire();
        box.value = 99;
        pool.release(box);

        assertThat(box.value).isZero();           // reset hook ran
        assertThat(pool.available()).isEqualTo(1);
        assertThat(pool.acquire()).isSameAs(box);  // same instance reused
    }

    @Test
    void releaseIgnoresNullAndOverflow() {
        var pool = new ObjectPool<>(1, Box::new, _ -> {});
        var box = pool.acquire();

        pool.release(null);
        pool.release(box);
        pool.release(box); // overflow release must not exceed capacity

        assertThat(pool.available()).isEqualTo(1);
    }

    @Test
    void rejectsNonPositiveCapacity() {
        assertThatThrownBy(() -> new ObjectPool<>(0, Box::new, _ -> {}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
