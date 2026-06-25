// src/main/java/pl/mzebrows/shoots/net/ServerControlChannel.java
package pl.mzebrows.shoots.net;

/**
 * A {@link ControlChannel} that turns the host's round-flow transitions into broadcast {@code CONTROL}
 * messages to all clients. The host only ever sends on it (it is the authority), so {@link #poll()} /
 * {@link #peek()} are never used here.
 */
final class ServerControlChannel implements ControlChannel {

    private final TcpServer server;

    ServerControlChannel(TcpServer server) {
        this.server = server;
    }

    @Override
    public void send(ControlEvent event) {
        server.broadcast(new NetMessage.Control(event.frame(), event.kind()));
    }

    @Override
    public ControlEvent poll() {
        return null;
    }

    @Override
    public ControlEvent peek() {
        return null;
    }
}
