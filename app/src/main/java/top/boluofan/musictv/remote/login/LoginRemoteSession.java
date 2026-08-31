package top.boluofan.musictv.remote.login;

import java.io.IOException;

public final class LoginRemoteSession implements AutoCloseable {
    private final Factory factory;
    private Server active;
    private long generation;

    public LoginRemoteSession(Factory factory) {
        this.factory = factory;
    }

    public synchronized void start(Listener listener) throws IOException {
        close();
        long sessionGeneration = ++generation;
        Server server = factory.create((url, username, password, token) ->
                deliver(sessionGeneration, listener, url, username, password, token));
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

    private void deliver(long sessionGeneration, Listener listener, String url,
                         String username, String password, String token) {
        Server server;
        synchronized (this) {
            if (sessionGeneration != generation || active == null) return;
            server = active;
            active = null;
            generation++;
        }
        server.stop();
        listener.onLogin(url, username, password, token);
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
        void onLogin(String url, String username, String password, String token);
    }
}
