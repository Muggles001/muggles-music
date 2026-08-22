package top.boluofan.musictv.source;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.junit.Test;

public class SourceScriptImporterTest {
    @Test
    public void downloadUsesOneDeadlineAcrossTheRequest() throws Exception {
        ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        Thread hangingServer = new Thread(() -> {
            try (Socket ignored = server.accept()) {
                Thread.sleep(5000);
            } catch (Exception ignored) {
            }
        });
        hangingServer.start();

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .followRedirects(false)
                .build();
        SourceScriptImporter importer = new SourceScriptImporter(client, 350);
        long startedAt = System.nanoTime();
        try {
            importer.download("http://127.0.0.1:" + server.getLocalPort() + "/source.js");
            fail("Expected the stalled download to time out");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("超时"));
        } finally {
            hangingServer.interrupt();
            server.close();
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        assertTrue("Download exceeded its overall deadline: " + elapsedMs, elapsedMs < 2000);
    }
}
