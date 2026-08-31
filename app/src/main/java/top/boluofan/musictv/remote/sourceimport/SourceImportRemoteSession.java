package top.boluofan.musictv.remote.sourceimport;

import java.io.IOException;

public final class SourceImportRemoteSession implements AutoCloseable {
    private final Factory factory;
    private Server active;
    private long generation;

    public SourceImportRemoteSession(Factory factory) {
        this.factory = factory;
    }

    public synchronized void start(Listener listener) throws IOException {
        close();
        long sessionGeneration = ++generation;
        Server server = factory.create(sourceUrl ->
                deliver(sessionGeneration, listener, sourceUrl));
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

    private void deliver(long sessionGeneration, Listener listener, String sourceUrl) {
        Server server;
        synchronized (this) {
            if (sessionGeneration != generation || active == null) return;
            server = active;
            active = null;
            generation++;
        }
        server.stop();
        listener.onSourceUrl(sourceUrl);
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
        void onSourceUrl(String sourceUrl);
    }
}
