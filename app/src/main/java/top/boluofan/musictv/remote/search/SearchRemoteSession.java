package top.boluofan.musictv.remote.search;

import java.io.IOException;

public final class SearchRemoteSession implements AutoCloseable {
    private final Factory factory;
    private Server active;
    private long generation;

    public SearchRemoteSession(Factory factory) {
        this.factory = factory;
    }

    public synchronized void start(Listener listener) throws IOException {
        close();
        long sessionGeneration = ++generation;
        Server server = factory.create((keyword, source) ->
                deliver(sessionGeneration, listener, keyword, source));
        active = server;
        try {
            server.start();
        } catch (IOException error) {
            if (active == server) {
                active = null;
                generation++;
            }
            server.stop();
            throw error;
        }
    }

    private void deliver(long sessionGeneration, Listener listener,
                         String keyword, String source) {
        Server server;
        synchronized (this) {
            if (sessionGeneration != generation || active == null) return;
            server = active;
            active = null;
            generation++;
        }
        server.stop();
        listener.onSearch(keyword, source);
    }

    @Override
    public synchronized void close() {
        generation++;
        if (active == null) return;
        Server server = active;
        active = null;
        server.stop();
    }

    synchronized boolean isActive() {
        return active != null;
    }

    public interface Factory {
        Server create(Listener listener);
    }

    public interface Server {
        void start() throws IOException;
        void stop();
    }

    public interface Listener {
        void onSearch(String keyword, String source);
    }
}
