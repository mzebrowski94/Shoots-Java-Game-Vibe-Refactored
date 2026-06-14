// src/main/java/pl/mzebrows/shoots/entity/MovementStrategy.java
package pl.mzebrows.shoots.entity;

/** Runtime-swappable movement behaviour; integrates an entity's position over one fixed step. */
@FunctionalInterface
public interface MovementStrategy {

    /** Advances the entity by one update step (caller has already snapshotted previous position). */
    void move(Entity entity);
}
