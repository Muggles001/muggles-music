package top.boluofan.musictv.remote.sourceimport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class SourceImportRemoteSessionTest {
    @Test
    public void startReplacesPreviousAndIgnoresItsLateCallback() throws Exception {
        FakeFactory factory = new FakeFactory();
        SourceImportRemoteSession session = new SourceImportRemoteSession(factory);
        List<String> received = new ArrayList<>();

        session.start(received::add);
        FakeServer first = factory.servers.get(0);
        session.start(received::add);
        FakeServer second = factory.servers.get(1);
        first.deliver("https://example.test/old.js");
        second.deliver("https://example.test/new.js");

        assertEquals(1, first.stopCount);
        assertEquals(1, second.stopCount);
        assertEquals(java.util.Collections.singletonList(
                "https://example.test/new.js"), received);
        assertFalse(session.isActive());
    }

    @Test
    public void callbackIsConsumedOnlyOnce() throws Exception {
        FakeFactory factory = new FakeFactory();
        SourceImportRemoteSession session = new SourceImportRemoteSession(factory);
        List<String> received = new ArrayList<>();
        session.start(received::add);

        FakeServer server = factory.servers.get(0);
        server.deliver("https://example.test/first.js");
        server.deliver("https://example.test/second.js");

        assertEquals(java.util.Collections.singletonList(
                "https://example.test/first.js"), received);
        assertEquals(1, server.stopCount);
    }

    @Test
    public void closeIsIdempotentAndSuppressesLateCallback() throws Exception {
        FakeFactory factory = new FakeFactory();
        SourceImportRemoteSession session = new SourceImportRemoteSession(factory);
        List<String> received = new ArrayList<>();
        session.start(received::add);
        FakeServer server = factory.servers.get(0);

        session.close();
        session.close();
        server.deliver("https://example.test/late.js");

        assertEquals(1, server.stopCount);
        assertTrue(received.isEmpty());
        assertFalse(session.isActive());
    }

    @Test
    public void failedStartStopsAndClearsServer() {
        FakeFactory factory = new FakeFactory();
        factory.failNext = true;
        SourceImportRemoteSession session = new SourceImportRemoteSession(factory);

        try {
            session.start(sourceUrl -> { });
            fail("Expected bind failure");
        } catch (IOException expected) {
            assertEquals("bind", expected.getMessage());
        }

        assertEquals(1, factory.servers.get(0).stopCount);
        assertFalse(session.isActive());
    }

    private static final class FakeFactory implements SourceImportRemoteSession.Factory {
        final List<FakeServer> servers = new ArrayList<>();
        boolean failNext;

        @Override
        public SourceImportRemoteSession.Server create(
                SourceImportRemoteSession.Listener listener) {
            FakeServer server = new FakeServer(listener, failNext);
            failNext = false;
            servers.add(server);
            return server;
        }
    }

    private static final class FakeServer implements SourceImportRemoteSession.Server {
        private final SourceImportRemoteSession.Listener listener;
        private final boolean failStart;
        int stopCount;

        FakeServer(SourceImportRemoteSession.Listener listener, boolean failStart) {
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

        void deliver(String sourceUrl) {
            listener.onSourceUrl(sourceUrl);
        }
    }
}
