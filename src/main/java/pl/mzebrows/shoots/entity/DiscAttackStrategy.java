// src/main/java/pl/mzebrows/shoots/entity/DiscAttackStrategy.java
package pl.mzebrows.shoots.entity;

/**
 * Default player firing behaviour: spawns one disc per trigger from the source entity's position and
 * current {@code angle}, up to a configured concurrent-disc cap per owner.
 *
 * <p>Pure logic over the {@link EntitySpawner} contract (which hides the pool), so shooting can be
 * swapped at runtime and unit-tested without AWT. The cap mirrors the legacy "max 3 discs" rule but
 * is externalised rather than hard-coded in game logic.
 */
public final class DiscAttackStrategy implements AttackStrategy {

    private final int maxConcurrentDiscs;
    private int activeDiscs;

    public DiscAttackStrategy(int maxConcurrentDiscs) {
        if (maxConcurrentDiscs <= 0) {
            throw new IllegalArgumentException("maxConcurrentDiscs must be positive: " + maxConcurrentDiscs);
        }
        this.maxConcurrentDiscs = maxConcurrentDiscs;
    }

    @Override
    public boolean attack(Entity source, EntitySpawner spawner) {
        if (activeDiscs >= maxConcurrentDiscs) {
            return false;
        }
        Entity disc = spawner.spawnDisc(source.getX(), source.getY(), source.getAngle(),
                source.getOwnerId(), source.isPowered());
        if (disc == null) {
            return false;
        }
        activeDiscs++;
        return true;
    }

    /** Notifies the strategy that one of its discs was retired, freeing a slot. */
    public void onDiscRetired() {
        if (activeDiscs > 0) {
            activeDiscs--;
        }
    }

    /** Number of this owner's discs currently in flight. */
    public int activeDiscs() {
        return activeDiscs;
    }
}
