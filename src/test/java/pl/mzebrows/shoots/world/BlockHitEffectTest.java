// src/test/java/pl/mzebrows/shoots/world/BlockHitEffectTest.java
package pl.mzebrows.shoots.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Verifies the block-hit flash lifecycle: grey ramp-up, disc-colour fade-out, then done. */
class BlockHitEffectTest {

    @Test
    void startsInGreyPhaseForTheGivenTileAndOwner() {
        var e = new BlockHitEffect(4, 7, 1);

        assertThat(e.tileX()).isEqualTo(4);
        assertThat(e.tileY()).isEqualTo(7);
        assertThat(e.ownerId()).isEqualTo(1);
        assertThat(e.phase()).isEqualTo(BlockHitEffect.Phase.GREY);
        assertThat(e.greyLevel()).isZero();
        assertThat(e.isDone()).isFalse();
    }

    @Test
    void greyRampsUpThenSwitchesToFade() {
        var e = new BlockHitEffect(0, 0, 0);

        // Grey climbs by 10 per tick until it reaches the cap, then the phase flips to FADE.
        int guard = 0;
        while (e.phase() == BlockHitEffect.Phase.GREY && guard++ < 100) {
            e.advance();
        }

        assertThat(e.phase()).isEqualTo(BlockHitEffect.Phase.FADE);
        assertThat(e.greyLevel()).isEqualTo(170);
    }

    @Test
    void fadeReducesAlphaThenFinishes() {
        var e = new BlockHitEffect(0, 0, 0);

        int guard = 0;
        while (!e.isDone() && guard++ < 1000) {
            e.advance();
        }

        assertThat(e.isDone()).isTrue();
        assertThat(e.phase()).isEqualTo(BlockHitEffect.Phase.DONE);
    }

    @Test
    void restartResetsAnInProgressEffectInPlace() {
        var e = new BlockHitEffect(2, 2, 1);
        for (int i = 0; i < 30; i++) {
            e.advance(); // push it well into the fade phase
        }

        e.restart(3); // same tile hit again by another player

        assertThat(e.ownerId()).isEqualTo(3);
        assertThat(e.phase()).isEqualTo(BlockHitEffect.Phase.GREY);
        assertThat(e.greyLevel()).isZero();
        assertThat(e.isDone()).isFalse();
    }
}
