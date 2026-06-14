// src/main/java/pl/mzebrows/shoots/entity/AiStrategy.java
package pl.mzebrows.shoots.entity;

/** Optional runtime-swappable decision logic (e.g. enemy targeting) run before movement/attack. */
@FunctionalInterface
public interface AiStrategy {

    /** Updates the entity's intent (angle, direction, attack triggers) for the coming step. */
    void think(Entity entity);
}
