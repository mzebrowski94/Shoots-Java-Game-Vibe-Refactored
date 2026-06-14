// src/main/java/pl/mzebrows/shoots/pool/ObjectPool.java
package pl.mzebrows.shoots.pool;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Fixed-capacity, array-backed pool of reusable instances; {@code acquire()}/{@code release()}
 * perform no allocation, so the hot game loop never calls {@code new}.
 *
 * <p>All instances are created eagerly at construction. {@code acquire()} returns an unused one (or
 * {@code null} when exhausted); {@code release(t)} runs the reset hook and makes it available again.
 * Not thread-safe by design — the game loop owns it on a single thread.
 *
 * @param <T> pooled type
 */
public final class ObjectPool<T> {

    private final T[] items;
    private final Consumer<T> resetHook;
    private int freeCount;

    /**
     * @param capacity  number of instances to pre-allocate
     * @param factory   creates each instance once, at construction
     * @param resetHook clears an instance's state on release (may be a no-op)
     */
    @SuppressWarnings("unchecked")
    public ObjectPool(int capacity, Supplier<T> factory, Consumer<T> resetHook) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive: " + capacity);
        }
        this.resetHook = resetHook;
        this.items = (T[]) new Object[capacity];
        for (int i = 0; i < capacity; i++) {
            items[i] = factory.get();
        }
        this.freeCount = capacity;
    }

    /** Returns a pooled instance, or {@code null} if all instances are currently in use. */
    public T acquire() {
        if (freeCount == 0) {
            return null;
        }
        return items[--freeCount];
    }

    /** Resets {@code instance} and returns it to the pool; ignores a null or already-free overflow. */
    public void release(T instance) {
        if (instance == null || freeCount == items.length) {
            return;
        }
        resetHook.accept(instance);
        items[freeCount++] = instance;
    }

    /** Total number of pre-allocated instances. */
    public int capacity() {
        return items.length;
    }

    /** Number of instances currently available for {@link #acquire()}. */
    public int available() {
        return freeCount;
    }

    /** Number of instances currently handed out. */
    public int inUse() {
        return items.length - freeCount;
    }
}
