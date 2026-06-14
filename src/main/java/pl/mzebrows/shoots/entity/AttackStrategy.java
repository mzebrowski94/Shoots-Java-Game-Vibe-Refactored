// src/main/java/pl/mzebrows/shoots/entity/AttackStrategy.java
package pl.mzebrows.shoots.entity;

/** Runtime-swappable attack behaviour; decides whether and how a source entity fires this step. */
@FunctionalInterface
public interface AttackStrategy {

    /**
     * Resolves an attack for {@code source}, acquiring projectiles from {@code spawner} as needed.
     *
     * @return {@code true} if an attack was produced this step
     */
    boolean attack(Entity source, EntitySpawner spawner);
}
