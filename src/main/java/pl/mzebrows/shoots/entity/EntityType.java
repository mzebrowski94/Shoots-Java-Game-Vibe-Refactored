// src/main/java/pl/mzebrows/shoots/entity/EntityType.java
package pl.mzebrows.shoots.entity;

/** Archetype of a pooled entity; drives system dispatch without subclassing. */
public enum EntityType {
    DISC,
    BLOCK,
    CAPTURE_POINT,
    PLAYER_BASE,
    ENEMY
}
