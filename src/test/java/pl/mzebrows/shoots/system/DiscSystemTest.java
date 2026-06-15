// src/test/java/pl/mzebrows/shoots/system/DiscSystemTest.java
package pl.mzebrows.shoots.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.shoots.config.CollisionConfig;
import pl.mzebrows.shoots.config.DiscConfig;
import pl.mzebrows.shoots.config.GridConfig;
import pl.mzebrows.shoots.entity.Entity;
import pl.mzebrows.shoots.pool.ObjectPool;
import pl.mzebrows.shoots.spatial.TileType;
import pl.mzebrows.shoots.spatial.UniformGridCollider;

/** Verifies disc movement, collision dispatch, and pool retirement wiring in the disc lifecycle. */
class DiscSystemTest {

    private static final int UNIT = 36;
    private static final int SIZE = 25;
    private static final DiscConfig DISC = new DiscConfig(18, 10, 2.0, 7, 3);

    private final GridConfig grid = new GridConfig(UNIT, SIZE);
    private final CollisionConfig collision = new CollisionConfig(4);

    private TileType[][] emptyField() {
        var tiles = new TileType[SIZE][SIZE];
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                tiles[i][j] = (i == 0 || j == 0 || i == SIZE - 1 || j == SIZE - 1)
                        ? TileType.WALL : TileType.EMPTY;
            }
        }
        return tiles;
    }

    private ObjectPool<Entity> pool() {
        return new ObjectPool<>(4, Entity::new, Entity::reset);
    }

    @Test
    void movesActiveDiscByOneStep() {
        var combat = new CombatSystem(pool(), DISC);
        var system = new DiscSystem(new UniformGridCollider(emptyField(), grid, collision), combat);
        Entity disc = combat.spawnDisc(12 * UNIT + UNIT / 2.0, 12 * UNIT, 0, 1); // +Y travel

        double y0 = disc.getY();
        system.update(List.of(disc), DiscSystem.NO_OP);

        assertThat(disc.getY()).isGreaterThan(y0);
        assertThat(disc.getPrevY()).isEqualTo(y0);
    }

    @Test
    void skipsInactiveDiscs() {
        var combat = new CombatSystem(pool(), DISC);
        var collider = new UniformGridCollider(emptyField(), grid, collision);
        var system = new DiscSystem(collider, combat);
        var sink = mock(DiscSystem.DiscEventSink.class);
        var inactive = new Entity(); // active == false

        system.update(List.of(inactive), sink);

        verify(sink, never()).onCapturePointHit(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
        verify(sink, never()).onDiscRetired(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void reportsCapturePointHit() {
        var tiles = emptyField();
        tiles[12][13] = TileType.CAPTURE_POINT;
        var combat = new CombatSystem(pool(), DISC);
        var system = new DiscSystem(new UniformGridCollider(tiles, grid, collision), combat);
        var sink = mock(DiscSystem.DiscEventSink.class);
        // Place disc so that after one +Y step it lands inside tile (12,13).
        Entity disc = combat.spawnDisc(12 * UNIT + UNIT / 2.0, 13 * UNIT + UNIT / 2.0, 0, 1);

        system.update(List.of(disc), sink);

        verify(sink).onCapturePointHit(disc, 12, 13);
    }

    @Test
    void retiringDiscsMidUpdateDoesNotCorruptIteration() {
        // Regression: the retirement sink removes the spent disc from the live list (as PlayWorld
        // does). The old forward loop cached size() and threw IndexOutOfBoundsException once a disc
        // was removed mid-iteration (e.g. right after a block bounce exhausted its bounce budget).
        var combat = new CombatSystem(pool(), DISC);
        var system = new DiscSystem(new UniformGridCollider(emptyField(), grid, collision), combat);
        var discs = new java.util.ArrayList<Entity>();
        for (int k = 0; k < 4; k++) {
            Entity d = combat.spawnDisc(12 * UNIT, 12 * UNIT, 0, 1);
            d.setBounces(DISC.maxBounces()); // every disc is already spent -> all retire this step
            discs.add(d);
        }
        // Sink mirrors PlayWorld: remove the retired disc from the list mid-update.
        DiscSystem.DiscEventSink removingSink = new DiscSystem.DiscEventSink() {
            @Override public void onCapturePointHit(Entity disc, int tileX, int tileY) { }
            @Override public void onDiscRetired(Entity disc) { discs.remove(disc); }
        };

        system.update(discs, removingSink); // must not throw

        assertThat(discs).isEmpty();
    }

    @Test
    void retiresSpentDiscAndReportsIt() {
        var combat = new CombatSystem(pool(), DISC);
        var system = new DiscSystem(new UniformGridCollider(emptyField(), grid, collision), combat);
        var sink = mock(DiscSystem.DiscEventSink.class);
        Entity disc = combat.spawnDisc(12 * UNIT, 12 * UNIT, 0, 1);
        disc.setBounces(DISC.maxBounces()); // already spent

        system.update(List.of(disc), sink);

        assertThat(disc.isActive()).isFalse();
        verify(sink).onDiscRetired(disc);
    }
}
