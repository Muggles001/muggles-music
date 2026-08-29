package top.boluofan.musictv.player.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

public final class AsyncConnectionTest {
    private final Deque<SettableFuture<String>> futures = new ArrayDeque<>();
    private final List<ListenableFuture<String>> released = new ArrayList<>();
    private final List<String> connected = new ArrayList<>();
    private final List<Throwable> failures = new ArrayList<>();
    private final AtomicBoolean hostActive = new AtomicBoolean(true);
    private final AsyncConnection<String> connection = new AsyncConnection<>(
            futures::removeFirst,
            released::add,
            Runnable::run,
            hostActive::get,
            connected::add,
            failures::add);

    @Test
    public void disconnectBeforeCompleteReleasesAndIgnoresCompletion() {
        SettableFuture<String> pending = enqueueFuture();

        connection.connect();
        connection.disconnect();
        pending.set("old");

        assertEquals(Collections.singletonList(pending), released);
        assertEquals(Collections.emptyList(), connected);
    }

    @Test
    public void staleCompletionCannotReplaceReconnectedValue() {
        SettableFuture<String> first = enqueueFuture();
        SettableFuture<String> second = enqueueFuture();

        connection.connect();
        connection.connect();
        first.set("old");
        second.set("new");

        assertEquals(Collections.singletonList(first), released);
        assertEquals(Collections.singletonList("new"), connected);
    }

    @Test
    public void reconnectAfterDisconnectConnectsNewFuture() {
        SettableFuture<String> first = enqueueFuture();
        SettableFuture<String> second = enqueueFuture();

        connection.connect();
        first.set("first");
        connection.disconnect();
        connection.connect();
        second.set("second");

        assertEquals(Collections.singletonList(first), released);
        assertEquals(Arrays.asList("first", "second"), connected);
    }

    @Test
    public void failureReleasesFutureAndReportsCause() {
        SettableFuture<String> pending = enqueueFuture();
        IllegalStateException failure = new IllegalStateException("failed");

        connection.connect();
        pending.setException(failure);

        assertEquals(Collections.singletonList(pending), released);
        assertEquals(1, failures.size());
        assertSame(failure, failures.get(0));
    }

    @Test
    public void disconnectAfterSuccessIsIdempotent() {
        SettableFuture<String> pending = enqueueFuture();

        connection.connect();
        pending.set("connected");
        connection.disconnect();
        connection.disconnect();

        assertEquals(Collections.singletonList(pending), released);
        assertEquals(Collections.singletonList("connected"), connected);
    }

    @Test
    public void inactiveHostReleasesCompletedFutureWithoutCallback() {
        SettableFuture<String> pending = enqueueFuture();

        connection.connect();
        hostActive.set(false);
        pending.set("unused");

        assertEquals(Collections.singletonList(pending), released);
        assertEquals(Collections.emptyList(), connected);
    }

    private SettableFuture<String> enqueueFuture() {
        SettableFuture<String> future = SettableFuture.create();
        futures.add(future);
        return future;
    }
}
