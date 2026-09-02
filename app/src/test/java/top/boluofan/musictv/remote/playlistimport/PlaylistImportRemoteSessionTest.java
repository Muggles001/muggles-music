package top.boluofan.musictv.remote.playlistimport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class PlaylistImportRemoteSessionTest {
    @Test
    public void firstDeliveryWinsAndStopsServer() throws Exception {
        FakeFactory factory = new FakeFactory();
        PlaylistImportRemoteSession session = new PlaylistImportRemoteSession(factory);
        List<String> delivered = new ArrayList<>();
        session.start(delivered::add);

        FakeServer server = factory.servers.get(0);
        server.deliver("first");
        server.deliver("second");

        assertEquals(java.util.Collections.singletonList("first"), delivered);
        assertEquals(1, server.stopCount);
    }

    @Test
    public void replacementAndCloseSuppressLateDelivery() throws Exception {
        FakeFactory factory = new FakeFactory();
        PlaylistImportRemoteSession session = new PlaylistImportRemoteSession(factory);
        List<String> delivered = new ArrayList<>();
        session.start(delivered::add);
        FakeServer first = factory.servers.get(0);
        session.start(delivered::add);
        FakeServer second = factory.servers.get(1);
        session.close();

        first.deliver("old");
        second.deliver("closed");

        assertTrue(delivered.isEmpty());
        assertEquals(1, first.stopCount);
        assertEquals(1, second.stopCount);
    }

    private static final class FakeFactory implements PlaylistImportRemoteSession.Factory {
        private final List<FakeServer> servers = new ArrayList<>();

        @Override
        public PlaylistImportRemoteSession.Server create(
                PlaylistImportRemoteSession.Listener listener) {
            FakeServer server = new FakeServer(listener);
            servers.add(server);
            return server;
        }
    }

    private static final class FakeServer implements PlaylistImportRemoteSession.Server {
        private final PlaylistImportRemoteSession.Listener listener;
        private int stopCount;

        private FakeServer(PlaylistImportRemoteSession.Listener listener) {
            this.listener = listener;
        }

        @Override
        public void start() throws IOException {
        }

        @Override
        public void stop() {
            stopCount++;
        }

        private void deliver(String link) {
            listener.onPlaylistLink(link);
        }
    }
}
