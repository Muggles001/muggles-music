package top.boluofan.musictv.catalog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;

import org.junit.Test;

public class PlaylistLinkResolverTest {
    @Test
    public void expandsAllowlistedShortLinkIntoStableReference() throws Exception {
        FakeTransport transport = new FakeTransport(
                "https://www.kugou.com/yy/special/single/334455.html");

        PlaylistReference reference = new PlaylistLinkResolver(transport).resolve(
                "https://t.kugou.com/AbCdEf", "auto");

        assertEquals("kg", reference.getSource());
        assertEquals("334455", reference.getSourceListId());
        assertEquals(1, transport.calls);
    }

    @Test
    public void rejectsRedirectToUnapprovedHost() {
        FakeTransport transport = new FakeTransport(
                "https://attacker.test/playlist?id=334455");

        assertFailure(PlaylistLinkResolver.Kind.INVALID_INPUT,
                new PlaylistLinkResolver(transport));
    }

    @Test
    public void capsRedirectChain() {
        String[] redirects = new String[6];
        Arrays.fill(redirects, "https://t.kugou.com/Another");
        FakeTransport transport = new FakeTransport(redirects);

        assertFailure(PlaylistLinkResolver.Kind.TOO_MANY_REDIRECTS,
                new PlaylistLinkResolver(transport));
    }

    private static void assertFailure(PlaylistLinkResolver.Kind kind,
                                      PlaylistLinkResolver resolver) {
        try {
            resolver.resolve("https://t.kugou.com/Start", "auto");
            fail("Expected " + kind);
        } catch (PlaylistLinkResolver.Failure failure) {
            assertEquals(kind, failure.getKind());
        }
    }

    private static final class FakeTransport implements PlaylistLinkResolver.RedirectTransport {
        private final Queue<String> redirects = new ArrayDeque<>();
        private int calls;

        private FakeTransport(String... redirects) {
            this.redirects.addAll(Arrays.asList(redirects));
        }

        @Override
        public String redirect(String url) throws IOException {
            calls++;
            return redirects.poll();
        }
    }
}
