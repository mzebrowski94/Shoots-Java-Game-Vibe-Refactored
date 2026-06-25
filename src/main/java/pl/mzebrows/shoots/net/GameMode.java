// src/main/java/pl/mzebrows/shoots/net/GameMode.java
package pl.mzebrows.shoots.net;

/**
 * Which role this peer plays in a match. {@code OFFLINE} is the default single-machine / hotseat path
 * (unchanged behaviour); {@code HOST} owns the authoritative round flow and broadcasts it; {@code CLIENT}
 * follows the host's broadcast transitions. See {@code OnlineMode.md} (cluster F).
 */
public enum GameMode {
    OFFLINE,
    HOST,
    CLIENT
}
