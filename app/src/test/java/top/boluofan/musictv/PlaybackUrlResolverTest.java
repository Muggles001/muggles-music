package top.boluofan.musictv;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlaybackUrlResolverTest {
    @Test
    public void loopbackHostsAreRecognized() {
        assertTrue(PlaybackUrlResolver.isLoopbackHost("localhost"));
        assertTrue(PlaybackUrlResolver.isLoopbackHost("music.localhost"));
        assertTrue(PlaybackUrlResolver.isLoopbackHost("127.0.0.1"));
        assertTrue(PlaybackUrlResolver.isLoopbackHost("127.42.3.9"));
        assertTrue(PlaybackUrlResolver.isLoopbackHost("::1"));
        assertTrue(PlaybackUrlResolver.isLoopbackHost("::ffff:127.0.0.1"));
        assertTrue(PlaybackUrlResolver.isLoopbackHost("0.0.0.0"));
    }

    @Test
    public void lanAndInternetHostsAreNotTreatedAsLoopback() {
        assertFalse(PlaybackUrlResolver.isLoopbackHost("192.168.1.20"));
        assertFalse(PlaybackUrlResolver.isLoopbackHost("10.0.0.8"));
        assertFalse(PlaybackUrlResolver.isLoopbackHost("music.example.com"));
        assertFalse(PlaybackUrlResolver.isLoopbackHost(null));
    }

    @Test
    public void explicitLoopbackServerConfigurationIsKeptUsable() {
        assertFalse(PlaybackUrlResolver.shouldProxyLoopback("127.0.0.1", "127.0.0.1"));
        assertFalse(PlaybackUrlResolver.shouldProxyLoopback("localhost", "localhost"));
        assertTrue(PlaybackUrlResolver.shouldProxyLoopback("127.0.0.1", "192.168.1.20"));
    }
}
