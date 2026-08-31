package top.boluofan.musictv.remote.login;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class LoginRemoteSessionTest {
    @Test
    public void startReplacesPreviousAndIgnoresItsLateCallback() throws Exception {
        FakeFactory factory = new FakeFactory();
        LoginRemoteSession session = new LoginRemoteSession(factory);
        List<String> received = new ArrayList<>();

        session.start((url, username, password, token) -> received.add(url));
        FakeServer first = factory.servers.get(0);
        session.start((url, username, password, token) -> received.add(url));
        FakeServer second = factory.servers.get(1);
        first.deliver("https://old.test", "old-user", "old-password", "old-token");
        second.deliver("https://new.test", "new-user", "new-password", "new-token");

        assertEquals(1, first.stopCount);
        assertEquals(1, second.stopCount);
        assertEquals(java.util.Collections.singletonList("https://new.test"), received);
        assertFalse(session.isActive());
    }

    @Test
    public void callbackIsConsumedOnlyOnceWithAllFields() throws Exception {
        FakeFactory factory = new FakeFactory();
        LoginRemoteSession session = new LoginRemoteSession(factory);
        List<String> received = new ArrayList<>();
        session.start((url, username, password, token) -> received.add(
                url + "|" + username + "|" + password + "|" + token));

        FakeServer server = factory.servers.get(0);
        server.deliver("https://first.test", "alice", "secret", "first-token");
        server.deliver("https://second.test", "bob", "other", "second-token");

        assertEquals(java.util.Collections.singletonList(
                "https://first.test|alice|secret|first-token"), received);
        assertEquals(1, server.stopCount);
    }

    @Test
    public void closeIsIdempotentAndSuppressesLateCallback() throws Exception {
        FakeFactory factory = new FakeFactory();
        LoginRemoteSession session = new LoginRemoteSession(factory);
        List<String> received = new ArrayList<>();
        session.start((url, username, password, token) -> received.add(url));
        FakeServer server = factory.servers.get(0);

        session.close();
        session.close();
        server.deliver("https://late.test", "late", "late", "late");

        assertEquals(1, server.stopCount);
        assertTrue(received.isEmpty());
        assertFalse(session.isActive());
    }

    @Test
    public void failedStartStopsAndClearsServer() {
        FakeFactory factory = new FakeFactory();
        factory.failNext = true;
        LoginRemoteSession session = new LoginRemoteSession(factory);

        try {
            session.start((url, username, password, token) -> { });
            fail("Expected bind failure");
        } catch (IOException expected) {
            assertEquals("bind", expected.getMessage());
        }

        assertEquals(1, factory.servers.get(0).stopCount);
        assertFalse(session.isActive());
    }

    private static final class FakeFactory implements LoginRemoteSession.Factory {
        final List<FakeServer> servers = new ArrayList<>();
        boolean failNext;

        @Override
        public LoginRemoteSession.Server create(LoginRemoteSession.Listener listener) {
            FakeServer server = new FakeServer(listener, failNext);
            failNext = false;
            servers.add(server);
            return server;
        }
    }

    private static final class FakeServer implements LoginRemoteSession.Server {
        private final LoginRemoteSession.Listener listener;
        private final boolean failStart;
        int stopCount;

        FakeServer(LoginRemoteSession.Listener listener, boolean failStart) {
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

        void deliver(String url, String username, String password, String token) {
            listener.onLogin(url, username, password, token);
        }
    }
}
