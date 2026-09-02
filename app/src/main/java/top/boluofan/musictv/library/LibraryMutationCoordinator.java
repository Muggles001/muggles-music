package top.boluofan.musictv.library;

import java.util.ArrayDeque;
import java.util.Queue;

public final class LibraryMutationCoordinator {
    private static final LibraryMutationCoordinator SHARED = new LibraryMutationCoordinator();
    private final Queue<Mutation> pending = new ArrayDeque<>();
    private boolean running;

    public static LibraryMutationCoordinator shared() {
        return SHARED;
    }

    public synchronized void enqueue(Mutation mutation) {
        pending.add(mutation);
        startNextIfIdle();
    }

    private synchronized void startNextIfIdle() {
        if (running) return;
        Mutation next = pending.poll();
        if (next == null) return;
        running = true;
        next.start(this::finish);
    }

    private void finish() {
        synchronized (this) {
            if (!running) return;
            running = false;
        }
        startNextIfIdle();
    }

    public interface Mutation {
        void start(Runnable completion);
    }
}
