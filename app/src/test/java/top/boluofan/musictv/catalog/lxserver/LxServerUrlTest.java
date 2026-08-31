package top.boluofan.musictv.catalog.lxserver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class LxServerUrlTest {
    @Test
    public void normalizesSchemePathQueryAndFragment() {
        assertEquals("https://example.com/", LxRetrofitClient.normalizeServerUrl("example.com"));
        assertEquals("http://example.com/base/",
                LxRetrofitClient.normalizeServerUrl(" http://example.com/base?token=x#part "));
        assertEquals("https://example.com/base/",
                LxRetrofitClient.normalizeServerUrl("HTTPS://example.com/base/"));
    }

    @Test
    public void rejectsMissingOrInvalidAddresses() {
        assertNull(LxRetrofitClient.normalizeServerUrl(null));
        assertNull(LxRetrofitClient.normalizeServerUrl("  "));
        assertNull(LxRetrofitClient.normalizeServerUrl("http://"));
    }
}
