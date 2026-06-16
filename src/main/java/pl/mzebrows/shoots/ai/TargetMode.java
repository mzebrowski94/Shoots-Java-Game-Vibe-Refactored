// src/main/java/pl/mzebrows/shoots/ai/TargetMode.java
package pl.mzebrows.shoots.ai;

/** How an AI ranks candidate capture points when choosing what to shoot at. */
public enum TargetMode {

    /** Prefer the reachable capture point whose bounce path is shortest (cheapest, most reliable). */
    NEAREST,

    /** Prefer the reachable capture point worth the most points (neutral/low-owned first). */
    HIGHEST_VALUE,

    /** Prefer reachable points that are contested (enemy-owned or being fought over). */
    CONTESTED
}
