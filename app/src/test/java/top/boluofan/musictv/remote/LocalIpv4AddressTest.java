package top.boluofan.musictv.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class LocalIpv4AddressTest {
    @Test
    public void firstUsableSkipsLoopbackAndIpv6InEnumerationOrder() throws Exception {
        String result = LocalIpv4Address.firstUsable(Arrays.asList(
                InetAddress.getByName("127.0.0.1"),
                InetAddress.getByName("2001:db8::1"),
                InetAddress.getByName("192.168.1.23"),
                InetAddress.getByName("10.0.0.8")));

        assertEquals("192.168.1.23", result);
    }

    @Test
    public void firstUsableReturnsNullWithoutNonLoopbackIpv4() throws Exception {
        assertNull(LocalIpv4Address.firstUsable(Arrays.asList(
                InetAddress.getByName("127.0.0.1"),
                InetAddress.getByName("::1"))));
        assertNull(LocalIpv4Address.firstUsable(Collections.emptyList()));
    }
}
