package top.boluofan.musictv.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import fi.iki.elonen.NanoHTTPD;
import top.boluofan.musictv.ui.settings.connection.ConfigActivity;
import top.boluofan.musictv.remote.login.LoginWebServer;
import top.boluofan.musictv.remote.search.SearchWebServer;
import top.boluofan.musictv.remote.sourceimport.SourceImportWebServer;
import top.boluofan.musictv.ui.catalog.search.SearchFragment;

@RunWith(AndroidJUnit4.class)
public class RemoteWebServerProtocolInstrumentedTest {
    @Test
    public void protocolServersReleaseSocketForImmediateRebind() throws Exception {
        assertRebind(port -> new LoginWebServer(port, null));
        assertRebind(port -> new SearchWebServer(port, null));
        assertRebind(port -> new SourceImportWebServer(port, ignored -> { }));
    }

    @Test
    public void qrCallersUseDistinctCurrentPorts() throws Exception {
        assertEquals(8088, intConstant(ConfigActivity.class, "SERVER_PORT"));
        assertEquals(8089, intConstant(SearchFragment.class, "SEARCH_SERVER_PORT"));
    }

    @Test
    public void loginProtocolServesRootAndAcceptsFourFields() throws Exception {
        AtomicReference<List<String>> callback = new AtomicReference<>();
        LoginWebServer server = new LoginWebServer(8088,
                (url, username, password, token) -> callback.set(
                        java.util.Arrays.asList(url, username, password, token)),
                "https://example.test/?a=1&b=2", "user<name", "p\"ass", "tok'en");

        FakeSession get = new FakeSession(NanoHTTPD.Method.GET, "/", Collections.emptyMap());
        NanoHTTPD.Response page = server.serve(get);
        String html = body(page);

        assertResponse(page, NanoHTTPD.Response.Status.OK, "text/html; charset=UTF-8");
        assertTrue(html.contains("fetch('/login'"));
        assertTrue(html.contains("formData.append('url', url)"));
        assertTrue(html.contains("formData.append('username', username)"));
        assertTrue(html.contains("formData.append('password', password)"));
        assertTrue(html.contains("formData.append('token'"));
        assertTrue(html.contains("https://example.test/?a=1&amp;b=2"));
        assertTrue(html.contains("user&lt;name"));
        assertTrue(html.contains("p&quot;ass"));
        assertTrue(html.contains("tok&#39;en"));

        Map<String, String> fields = fields(
                "url", "http://192.168.1.8:58090",
                "username", "alice",
                "password", "secret",
                "token", "token-value");
        FakeSession post = new FakeSession(NanoHTTPD.Method.POST, "/login", fields);
        NanoHTTPD.Response response = server.serve(post);

        assertTrue(post.bodyParsed);
        assertResponse(response, NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT);
        assertEquals("SUCCESS", body(response));
        assertEquals(java.util.Arrays.asList(
                "http://192.168.1.8:58090", "alice", "secret", "token-value"), callback.get());
    }

    @Test
    public void loginProtocolRejectsOtherRoutesAndMethods() throws Exception {
        LoginWebServer server = new LoginWebServer(8088, null);

        NanoHTTPD.Response wrongMethod = server.serve(
                new FakeSession(NanoHTTPD.Method.GET, "/login", Collections.emptyMap()));
        NanoHTTPD.Response wrongRoute = server.serve(
                new FakeSession(NanoHTTPD.Method.POST, "/", Collections.emptyMap()));

        assertResponse(wrongMethod, NanoHTTPD.Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT);
        assertEquals("Not Found", body(wrongMethod));
        assertResponse(wrongRoute, NanoHTTPD.Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT);
        assertEquals("Not Found", body(wrongRoute));
    }

    @Test
    public void searchProtocolServesRootAndAcceptsKeywordAndSource() throws Exception {
        AtomicReference<List<String>> callback = new AtomicReference<>();
        SearchWebServer server = new SearchWebServer(8089,
                (keyword, source) -> callback.set(java.util.Arrays.asList(keyword, source)));

        NanoHTTPD.Response page = server.serve(
                new FakeSession(NanoHTTPD.Method.GET, "/", Collections.emptyMap()));
        String html = body(page);

        assertResponse(page, NanoHTTPD.Response.Status.OK, "text/html; charset=UTF-8");
        assertTrue(html.contains("fetch('/search'"));
        assertTrue(html.contains("formData.append('keyword', keyword)"));
        assertTrue(html.contains("formData.append('source', source)"));
        assertTrue(html.contains("<option value=\"all\">"));
        assertTrue(html.contains("<option value=\"kw\">"));
        assertTrue(html.contains("<option value=\"kg\">"));
        assertTrue(html.contains("<option value=\"tx\">"));
        assertTrue(html.contains("<option value=\"wy\">"));
        assertTrue(html.contains("<option value=\"mg\">"));

        FakeSession post = new FakeSession(NanoHTTPD.Method.POST, "/search",
                fields("keyword", "night drive", "source", "tx"));
        NanoHTTPD.Response response = server.serve(post);

        assertTrue(post.bodyParsed);
        assertResponse(response, NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT);
        assertEquals("SUCCESS", body(response));
        assertEquals(java.util.Arrays.asList("night drive", "tx"), callback.get());
    }

    @Test
    public void searchProtocolRejectsOtherRoutesAndMethods() throws Exception {
        SearchWebServer server = new SearchWebServer(8089, null);

        NanoHTTPD.Response wrongMethod = server.serve(
                new FakeSession(NanoHTTPD.Method.GET, "/search", Collections.emptyMap()));
        NanoHTTPD.Response wrongRoute = server.serve(
                new FakeSession(NanoHTTPD.Method.POST, "/", Collections.emptyMap()));

        assertResponse(wrongMethod, NanoHTTPD.Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT);
        assertEquals("Not Found", body(wrongMethod));
        assertResponse(wrongRoute, NanoHTTPD.Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT);
        assertEquals("Not Found", body(wrongRoute));
    }

    @Test
    public void sourceImportProtocolUsesRootFormAndTrimmedSourceUrlCallback() throws Exception {
        AtomicReference<String> callback = new AtomicReference<>();
        SourceImportWebServer server = new SourceImportWebServer(8088, callback::set);

        NanoHTTPD.Response page = server.serve(
                new FakeSession(NanoHTTPD.Method.GET, "/", Collections.emptyMap()));
        String html = body(page);

        assertResponse(page, NanoHTTPD.Response.Status.OK, "text/html; charset=utf-8");
        assertTrue(html.contains("<form method='post'>"));
        assertTrue(html.contains("name='sourceUrl'"));

        FakeSession post = new FakeSession(NanoHTTPD.Method.POST, "/",
                fields("sourceUrl", "  https://example.test/source.js  "));
        NanoHTTPD.Response response = server.serve(post);

        assertTrue(post.bodyParsed);
        assertResponse(response, NanoHTTPD.Response.Status.OK, "text/html; charset=utf-8");
        assertTrue(body(response).contains("已发送"));
        assertEquals("https://example.test/source.js", callback.get());
    }

    @Test
    public void sourceImportProtocolRejectsBlankFieldWithoutCallback() throws Exception {
        AtomicReference<String> callback = new AtomicReference<>();
        SourceImportWebServer server = new SourceImportWebServer(8088, callback::set);
        FakeSession post = new FakeSession(NanoHTTPD.Method.POST, "/",
                fields("sourceUrl", "   "));

        NanoHTTPD.Response response = server.serve(post);

        assertResponse(response, NanoHTTPD.Response.Status.BAD_REQUEST, "text/html; charset=utf-8");
        assertTrue(body(response).contains("请输入音源地址"));
        assertEquals(null, callback.get());
    }

    @Test
    public void parseFailuresKeepEachProtocolCurrentErrorContract() throws Exception {
        FakeSession login = FakeSession.failingPost("/login");
        NanoHTTPD.Response loginResponse = new LoginWebServer(8088, null).serve(login);
        assertResponse(loginResponse, NanoHTTPD.Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT);
        assertEquals("ERROR: parse failed", body(loginResponse));

        FakeSession search = FakeSession.failingPost("/search");
        NanoHTTPD.Response searchResponse = new SearchWebServer(8089, null).serve(search);
        assertResponse(searchResponse, NanoHTTPD.Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT);
        assertEquals("ERROR: parse failed", body(searchResponse));

        FakeSession source = FakeSession.failingPost("/");
        NanoHTTPD.Response sourceResponse = new SourceImportWebServer(8088, ignored -> { }).serve(source);
        assertResponse(sourceResponse, NanoHTTPD.Response.Status.INTERNAL_ERROR, "text/plain; charset=utf-8");
        assertEquals("parse failed", body(sourceResponse));
    }

    private static int intConstant(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(null);
    }

    private static Map<String, String> fields(String... entries) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            result.put(entries[i], entries[i + 1]);
        }
        return result;
    }

    private static void assertResponse(NanoHTTPD.Response response,
                                       NanoHTTPD.Response.Status status,
                                       String mimeType) {
        assertEquals(status, response.getStatus());
        assertEquals(mimeType, response.getMimeType());
    }

    private static void assertRebind(ServerFactory factory) throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        NanoHTTPD first = factory.create(port);
        first.start();
        first.stop();

        NanoHTTPD second = factory.create(port);
        try {
            second.start();
            assertTrue(second.isAlive());
        } finally {
            second.stop();
        }
    }

    private interface ServerFactory {
        NanoHTTPD create(int port);
    }

    private static String body(NanoHTTPD.Response response) throws IOException {
        try (InputStream input = response.getData();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static final class FakeSession implements NanoHTTPD.IHTTPSession {
        private final NanoHTTPD.Method method;
        private final String uri;
        private final Map<String, String> parameters;
        private final boolean failParsing;
        private boolean bodyParsed;

        private FakeSession(NanoHTTPD.Method method, String uri, Map<String, String> parameters) {
            this(method, uri, parameters, false);
        }

        private FakeSession(NanoHTTPD.Method method, String uri, Map<String, String> parameters,
                            boolean failParsing) {
            this.method = method;
            this.uri = uri;
            this.parameters = parameters;
            this.failParsing = failParsing;
        }

        private static FakeSession failingPost(String uri) {
            return new FakeSession(NanoHTTPD.Method.POST, uri, Collections.emptyMap(), true);
        }

        @Override
        public void execute() {
        }

        @Override
        public NanoHTTPD.CookieHandler getCookies() {
            return null;
        }

        @Override
        public Map<String, String> getHeaders() {
            return Collections.emptyMap();
        }

        @Override
        public InputStream getInputStream() {
            return null;
        }

        @Override
        public NanoHTTPD.Method getMethod() {
            return method;
        }

        @Override
        public Map<String, String> getParms() {
            return parameters;
        }

        @Override
        public Map<String, List<String>> getParameters() {
            return Collections.emptyMap();
        }

        @Override
        public String getQueryParameterString() {
            return null;
        }

        @Override
        public String getUri() {
            return uri;
        }

        @Override
        public void parseBody(Map<String, String> files) throws IOException {
            bodyParsed = true;
            if (failParsing) throw new IOException("parse failed");
        }

        @Override
        public String getRemoteIpAddress() {
            return "127.0.0.1";
        }

        @Override
        public String getRemoteHostName() {
            return "localhost";
        }
    }
}
