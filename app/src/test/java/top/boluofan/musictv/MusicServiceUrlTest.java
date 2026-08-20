package top.boluofan.musictv;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MusicServiceUrlTest {

    @Test
    public void loopbackHostsAreRecognized() {
        assertTrue(MusicService.isLoopbackHost("localhost"));
        assertTrue(MusicService.isLoopbackHost("music.localhost"));
        assertTrue(MusicService.isLoopbackHost("127.0.0.1"));
        assertTrue(MusicService.isLoopbackHost("127.42.3.9"));
        assertTrue(MusicService.isLoopbackHost("::1"));
        assertTrue(MusicService.isLoopbackHost("::ffff:127.0.0.1"));
        assertTrue(MusicService.isLoopbackHost("0.0.0.0"));
    }

    @Test
    public void lanAndInternetHostsAreNotTreatedAsLoopback() {
        assertFalse(MusicService.isLoopbackHost("192.168.1.20"));
        assertFalse(MusicService.isLoopbackHost("10.0.0.8"));
        assertFalse(MusicService.isLoopbackHost("music.example.com"));
        assertFalse(MusicService.isLoopbackHost(null));
    }

    @Test
    public void explicitLoopbackServerConfigurationIsKeptUsable() {
        assertFalse(MusicService.shouldProxyLoopback("127.0.0.1", "127.0.0.1"));
        assertFalse(MusicService.shouldProxyLoopback("localhost", "localhost"));
        assertTrue(MusicService.shouldProxyLoopback("127.0.0.1", "192.168.1.20"));
    }
}
