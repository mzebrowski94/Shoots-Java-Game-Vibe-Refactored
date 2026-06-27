// src/main/java/pl/mzebrows/shoots/net/LobbyJoiner.java
package pl.mzebrows.shoots.net;

import lombok.extern.slf4j.Slf4j;
import pl.mzebrows.shoots.config.GameConfig;

/**
 * Connects to a host on a background daemon thread (F7d/F7e) so the blocking TCP connect + {@code WELCOME}
 * handshake never freezes the game loop -- the menu polls {@link #status()} each frame and keeps the search
 * spinner animating and ESC responsive. On success it exposes a connected {@link OnlineLobby} (in its
 * waiting-room phase); on failure it records an error message. AWT-free.
 */
@Slf4j
public final class LobbyJoiner implements AutoCloseable {

    /** Connection progress for the search screen. */
    public enum Status { CONNECTING, JOINED, FAILED }

    private final String ip;
    private final int port;

    private volatile Status status = Status.CONNECTING;
    private volatile OnlineLobby lobby;
    private volatile String error;

    /** Begins connecting to {@code ip:port} immediately on a background thread. */
    public LobbyJoiner(GameConfig base, String ip, int port, String name) {
        this.ip = ip;
        this.port = port;
        var thread = new Thread(() -> run(base, name), "lobby-joiner");
        thread.setDaemon(true);
        log.info("Connecting to {}:{}", ip, port);
        thread.start();
    }

    private void run(GameConfig base, String name) {
        try {
            OnlineLobby joined = OnlineLobby.joinAddress(base, ip, port, name);
            lobby = joined;
            status = Status.JOINED;
            log.info("Connected to {}:{} (match {})", ip, port, joined.matchCode());
        } catch (Exception e) {
            error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            status = Status.FAILED;
            log.error("Connect to {}:{} failed: {}", ip, port, error);
        }
    }

    public Status status() {
        return status;
    }

    /** The connected lobby once {@link #status()} is {@code JOINED}, else {@code null}. */
    public OnlineLobby lobby() {
        return lobby;
    }

    public String error() {
        return error;
    }

    public String target() {
        return ip + ":" + port;
    }

    @Override
    public void close() {
        // Only close the lobby if the caller never took it over (failure / cancel before JOINED is consumed).
        if (status == Status.JOINED && lobby != null) {
            return;
        }
        if (lobby != null) {
            lobby.close();
        }
    }
}
