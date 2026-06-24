// src/main/java/pl/mzebrows/shoots/entity/EntitySpawner.java
package pl.mzebrows.shoots.entity;

/** Hands out pooled entities to behaviour code, hiding the underlying ObjectPool. */
public interface EntitySpawner {

    /**
     * Acquires a pooled disc fired from ({@code x},{@code y}) at {@code angle} degrees for an owner.
     *
     * @param powered whether to spawn a charged power disc (faster, more bounces, stronger capture)
     * @return the activated entity, or {@code null} if the pool is exhausted
     */
    Entity spawnDisc(double x, double y, double angle, int ownerId, boolean powered);

    /** Spawns a normal (non-powered) disc; convenience over {@link #spawnDisc(double, double, double, int, boolean)}. */
    default Entity spawnDisc(double x, double y, double angle, int ownerId) {
        return spawnDisc(x, y, angle, ownerId, false);
    }
}
