// src/main/java/pl/mzebrows/shoots/world/DisruptionSystem.java
package pl.mzebrows.shoots.world;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.IntFunction;

import pl.mzebrows.shoots.entity.Entity;

/**
 * Per-player base-disruption state machine. A disc that reaches a non-immune opponent base PARKS on it
 * and silences that player (no fire/laser) for a configured duration, after which the victim gets a short
 * immunity (grace) window. Holds only the timers + parked-disc refs; base geometry and the actual disc
 * retire are delegated to {@code PlayWorld} via callbacks, so this stays AWT-free and self-contained.
 */
final class DisruptionSystem {

    private final int playerCount;
    private final int disruptDurationTicks;
    private final int graceDurationTicks;
    private final boolean enabled;

    private final IntFunction<PlayWorld.BasePlacement> baseOf;
    private final Consumer<Entity> retireParkedDisc;

    private final int[] disruptTicks;
    private final int[] graceTicks;
    private final Entity[] parkedDisc;
    private final int[] parkedAttacker;

    DisruptionSystem(int playerCount, int disruptDurationTicks, int graceDurationTicks, boolean enabled,
                     IntFunction<PlayWorld.BasePlacement> baseOf, Consumer<Entity> retireParkedDisc) {
        this.playerCount = playerCount;
        this.disruptDurationTicks = disruptDurationTicks;
        this.graceDurationTicks = graceDurationTicks;
        this.enabled = enabled;
        this.baseOf = baseOf;
        this.retireParkedDisc = retireParkedDisc;
        this.disruptTicks = new int[playerCount];
        this.graceTicks = new int[playerCount];
        this.parkedDisc = new Entity[playerCount];
        this.parkedAttacker = new int[playerCount];
        Arrays.fill(parkedAttacker, -1);
    }

    /**
     * Attempts to disrupt the base at ({@code tileX},{@code tileY}) with {@code disc}; returns whether the
     * disc should PARK: only when enabled, the base belongs to a DIFFERENT player, and that victim is
     * neither already disrupted nor immune nor already holding a parked disc. On success the victim is
     * silenced for the configured duration and the disc is snapped onto the base centre.
     */
    boolean tryDisrupt(Entity disc, int tileX, int tileY) {
        if (!enabled) {
            return false;
        }
        int victim = playerAtBase(tileX, tileY);
        int attacker = disc.getOwnerId();
        if (victim < 0 || victim == attacker) {
            return false; // not a base, or the attacker's own base -> pass through
        }
        if (disruptTicks[victim] > 0 || graceTicks[victim] > 0 || parkedDisc[victim] != null) {
            return false; // already disrupted, immune, or holding a disc -> pass through
        }
        disruptTicks[victim] = disruptDurationTicks;
        parkedDisc[victim] = disc;
        parkedAttacker[victim] = attacker;
        // Snap the parked disc to the centre of the victim's base (prevX/Y too, so interpolation won't drift it).
        PlayWorld.BasePlacement vb = baseOf.apply(victim);
        disc.setX(vb.pixelX());
        disc.setY(vb.pixelY());
        disc.snapshot();
        return true;
    }

    /** Advances every player's timers; on disruption end retires the parked disc and grants the grace window. */
    void advance() {
        for (int p = 0; p < playerCount; p++) {
            if (disruptTicks[p] > 0) {
                disruptTicks[p]--;
                if (disruptTicks[p] == 0) {
                    releaseParked(p);
                    graceTicks[p] = graceDurationTicks;
                }
            } else if (graceTicks[p] > 0) {
                graceTicks[p]--;
            }
        }
    }

    /** Clears all disruption/grace state and drops parked-disc refs (round/match reset; discs retired elsewhere). */
    void clear() {
        for (int p = 0; p < playerCount; p++) {
            disruptTicks[p] = 0;
            graceTicks[p] = 0;
            if (parkedDisc[p] != null) {
                parkedDisc[p].setParked(false);
                parkedDisc[p] = null;
            }
            parkedAttacker[p] = -1;
        }
    }

    boolean isDisrupted(int playerId) {
        return disruptTicks[playerId] > 0;
    }

    boolean isImmune(int playerId) {
        return graceTicks[playerId] > 0;
    }

    double disruptionProgress(int playerId) {
        if (disruptTicks[playerId] <= 0 || disruptDurationTicks <= 0) {
            return 0.0;
        }
        return Math.min(1.0, (double) disruptTicks[playerId] / disruptDurationTicks);
    }

    double graceProgress(int playerId) {
        if (graceTicks[playerId] <= 0 || graceDurationTicks <= 0) {
            return 0.0;
        }
        return Math.min(1.0, (double) graceTicks[playerId] / graceDurationTicks);
    }

    /** Attacker id of the disc parked on {@code victimId}'s base, or -1 if none, for the renderer. */
    int parkedDiscOwnerAt(int victimId) {
        return parkedAttacker[victimId];
    }

    private void releaseParked(int p) {
        Entity disc = parkedDisc[p];
        parkedDisc[p] = null;
        parkedAttacker[p] = -1;
        if (disc != null) {
            retireParkedDisc.accept(disc);
        }
    }

    private int playerAtBase(int tileX, int tileY) {
        for (int p = 0; p < playerCount; p++) {
            PlayWorld.BasePlacement b = baseOf.apply(p);
            if (b.tileX() == tileX && b.tileY() == tileY) {
                return p;
            }
        }
        return -1;
    }
}
