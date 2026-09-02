package top.boluofan.musictv.remote.playlistimport;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public final class PlaylistImportRemoteSession implements AutoCloseable {
    private static final long EXPIRY_MINUTES = 5L;
    private static final ScheduledExecutorService EXPIRY = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "playlist-import-expiry");
                    thread.setDaemon(true);
                    return thread;
                }
            });

    private final Factory factory;
    private Server active;
    private ScheduledFuture<?> expiry;
    private long generation;

    public PlaylistImportRemoteSession(Factory factory) {
        this.factory = factory;
    }

    public synchronized void start(Listener listener) throws IOException {
        close();
        long sessionGeneration = ++generation;
        Server server = factory.create(link -> deliver(sessionGeneration, listener, link));
        active = server;
        try {
            server.start();
            expiry = EXPIRY.schedule(() -> expire(sessionGeneration),
                    EXPIRY_MINUTES, TimeUnit.MINUTES);
        } catch (IOException error) {
            active = null;
            generation++;
            server.stop();
            throw error;
        }
    }

    private void deliver(long sessionGeneration, Listener listener, String link) {
        Server server;
        synchronized (this) {
            if (sessionGeneration != generation || active == null) return;
            server = active;
            active = null;
            generation++;
            cancelExpiry();
        }
        server.stop();
        listener.onPlaylistLink(link);
    }

    private void expire(long sessionGeneration) {
        synchronized (this) {
            if (sessionGeneration != generation) return;
        }
        close();
    }

    @Override
    public synchronized void close() {
        generation++;
        cancelExpiry();
        if (active == null) return;
        Server server = active;
        active = null;
        server.stop();
    }

    private void cancelExpiry() {
        if (expiry != null) expiry.cancel(false);
        expiry = null;
    }

    public interface Factory {
        Server create(Listener listener);
    }

    public interface Server {
        void start() throws IOException;
        void stop();
    }

    public interface Listener {
        void onPlaylistLink(String link);
    }
}
