package top.boluofan.musictv.remote.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class SearchRemoteSessionTest {
    @Test
    public void startReplacesPreviousAndIgnoresItsLateCallback() throws Exception {
        FakeFactory factory = new FakeFactory();
        SearchRemoteSession session = new SearchRemoteSession(factory);
        List<String> received = new ArrayList<>();

        session.start((keyword, source) -> received.add(keyword));
        FakeServer first = factory.servers.get(0);
        session.start((keyword, source) -> received.add(keyword));
        FakeServer second = factory.servers.get(1);
        first.deliver("old", "kw");
        second.deliver("new", "tx");

        assertEquals(1, first.stopCount);
        assertEquals(1, second.stopCount);
        assertEquals(java.util.Collections.singletonList("new"), received);
        assertFalse(session.isActive());
    }

    @Test
    public void callbackIsConsumedOnlyOnce() throws Exception {
        FakeFactory factory = new FakeFactory();
        SearchRemoteSession session = new SearchRemoteSession(factory);
        List<String> received = new ArrayList<>();
        session.start((keyword, source) -> received.add(keyword + ":" + source));

        FakeServer server = factory.servers.get(0);
        server.deliver("first", "kw");
        server.deliver("second", "tx");

        assertEquals(java.util.Collections.singletonList("first:kw"), received);
        assertEquals(1, server.stopCount);
    }

    @Test
    public void closeIsIdempotentAndSuppressesLateCallback() throws Exception {
        FakeFactory factory = new FakeFactory();
        SearchRemoteSession session = new SearchRemoteSession(factory);
        List<String> received = new ArrayList<>();
        session.start((keyword, source) -> received.add(keyword));
        FakeServer server = factory.servers.get(0);

        session.close();
        session.close();
        server.deliver("late", "kw");

        assertEquals(1, server.stopCount);
        assertTrue(received.isEmpty());
        assertFalse(session.isActive());
    }

    @Test
    public void failedStartStopsAndClearsServer() {
        FakeFactory factory = new FakeFactory();
        factory.failNext = true;
        SearchRemoteSession session = new SearchRemoteSession(factory);

        try {
            session.start((keyword, source) -> { });
            fail("Expected bind failure");
        } catch (IOException expected) {
            assertEquals("bind", expected.getMessage());
        }

        assertEquals(1, factory.servers.get(0).stopCount);
        assertFalse(session.isActive());
    }

    private static final class FakeFactory implements SearchRemoteSession.Factory {
        final List<FakeServer> servers = new ArrayList<>();
        boolean failNext;

        @Override
        public SearchRemoteSession.Server create(SearchRemoteSession.Listener listener) {
            FakeServer server = new FakeServer(listener, failNext);
            failNext = false;
            servers.add(server);
            return server;
        }
    }

    private static final class FakeServer implements SearchRemoteSession.Server {
        private final SearchRemoteSession.Listener listener;
        private final boolean failStart;
        int stopCount;

        FakeServer(SearchRemoteSession.Listener listener, boolean failStart) {
            this.listener = listener;
            this.failStart = failStart;
        }

        @Override
        public void start() throws IOException {
            if (failStart) throw new IOException("bind");
        }

        @Override
        public void stop() {
            stopCount++;
        }

        void deliver(String keyword, String source) {
            listener.onSearch(keyword, source);
        }
    }
}
