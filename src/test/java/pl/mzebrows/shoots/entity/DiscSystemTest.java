// src/test/java/pl/mzebrows/shoots/system/DiscSystemTest.java
package pl.mzebrows.shoots.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.shoots.config.CollisionConfig;
import pl.mzebrows.shoots.config.DiscConfig;
import pl.mzebrows.shoots.config.GridConfig;
import pl.mzebrows.shoots.spatial.GridPathTracer;
import pl.mzebrows.shoots.spatial.TileType;
import pl.mzebrows.shoots.spatial.UniformGridCollider;

/** Verifies disc movement, collision dispatch, and pool retirement wiring in the disc lifecycle. */
class DiscSystemTest {

    private static final int UNIT = 36;
    private static final int SIZE = 25;
    private static final DiscConfig DISC = new DiscConfig(18, 10, 2.0, 7, 3, 3, 4);

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

    private GridPathTracer tracer(TileType[][] tiles) {
        return new GridPathTracer(new UniformGridCollider(tiles, grid, collision), UNIT);
    }

    private ObjectPool<Entity> pool() {
        return new ObjectPool<>(4, Entity::new, Entity::reset);
    }

    @Test
    void movesActiveDiscByOneStep() {
        var combat = new DiscSpawner(pool(), DISC);
        var system = new DiscSystem(tracer(emptyField()), combat);
        Entity disc = combat.spawnDisc(12 * UNIT + UNIT / 2.0, 12 * UNIT, 0, 1); // +Y travel

        double y0 = disc.getY();
        system.update(List.of(disc), DiscSystem.NO_OP);

        assertThat(disc.getY()).isGreaterThan(y0);
        assertThat(disc.getPrevY()).isEqualTo(y0);
    }

    @Test
    void skipsInactiveDiscs() {
        var combat = new DiscSpawner(pool(), DISC);
        var system = new DiscSystem(tracer(emptyField()), combat);
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
        var combat = new DiscSpawner(pool(), DISC);
        var system = new DiscSystem(tracer(tiles), combat);
        var sink = mock(DiscSystem.DiscEventSink.class);
        // Disc spawned inside the capture tile (12,13): a +Y step keeps it there, hit is reported.
        Entity disc = combat.spawnDisc(12 * UNIT + UNIT / 2.0, 13 * UNIT + UNIT / 2.0, 0, 1);

        system.update(List.of(disc), sink);

        verify(sink).onCapturePointHit(disc, 12, 13);
    }

    @Test
    void retiringDiscsMidUpdateDoesNotCorruptIteration() {
        var combat = new DiscSpawner(pool(), DISC);
        var system = new DiscSystem(tracer(emptyField()), combat);
        var discs = new java.util.ArrayList<Entity>();
        for (int k = 0; k < 4; k++) {
            Entity d = combat.spawnDisc(12 * UNIT + UNIT / 2.0, 12 * UNIT + UNIT / 2.0, 0, 1);
            d.setBounces(DISC.maxBounces()); // every disc is already spent -> all retire this step
            discs.add(d);
        }
        DiscSystem.DiscEventSink removingSink = new DiscSystem.DiscEventSink() {
            @Override public boolean onCapturePointHit(Entity disc, int tileX, int tileY) { return false; }
            @Override public void onDiscRetired(Entity disc) { discs.remove(disc); }
        };

        system.update(discs, removingSink); // must not throw

        assertThat(discs).isEmpty();
    }

    @Test
    void discRetiresWhenCaptureHitIsConsumed() {
        var tiles = emptyField();
        tiles[12][13] = TileType.CAPTURE_POINT;
        var combat = new DiscSpawner(pool(), DISC);
        var system = new DiscSystem(tracer(tiles), combat);
        Entity disc = combat.spawnDisc(12 * UNIT + UNIT / 2.0, 13 * UNIT + UNIT / 2.0, 0, 1);
        var discs = new java.util.ArrayList<Entity>();
        discs.add(disc);
        DiscSystem.DiscEventSink consumingSink = new DiscSystem.DiscEventSink() {
            @Override public boolean onCapturePointHit(Entity d, int tileX, int tileY) { return true; }
            @Override public void onDiscRetired(Entity d) { discs.remove(d); }
        };

        system.update(discs, consumingSink);

        assertThat(disc.isActive()).isFalse();
        assertThat(discs).isEmpty();
    }

    @Test
    void discPassesThroughWhenCaptureHitChangesNothing() {
        var tiles = emptyField();
        tiles[12][13] = TileType.CAPTURE_POINT;
        var combat = new DiscSpawner(pool(), DISC);
        var system = new DiscSystem(tracer(tiles), combat);
        Entity disc = combat.spawnDisc(12 * UNIT + UNIT / 2.0, 13 * UNIT + UNIT / 2.0, 0, 1);
        var discs = new java.util.ArrayList<Entity>();
        discs.add(disc);
        DiscSystem.DiscEventSink passThroughSink = new DiscSystem.DiscEventSink() {
            @Override public boolean onCapturePointHit(Entity d, int tileX, int tileY) { return false; }
            @Override public void onDiscRetired(Entity d) { discs.remove(d); }
        };

        system.update(discs, passThroughSink);

        assertThat(disc.isActive()).isTrue();
        assertThat(discs).containsExactly(disc);
    }

    @Test
    void retiresSpentDiscAndReportsIt() {
        var combat = new DiscSpawner(pool(), DISC);
        var system = new DiscSystem(tracer(emptyField()), combat);
        var sink = mock(DiscSystem.DiscEventSink.class);
        Entity disc = combat.spawnDisc(12 * UNIT + UNIT / 2.0, 12 * UNIT + UNIT / 2.0, 0, 1);
        disc.setBounces(DISC.maxBounces()); // already spent

        system.update(List.of(disc), sink);

        assertThat(disc.isActive()).isFalse();
        verify(sink).onDiscRetired(disc);
    }
}
