package top.boluofan.musictv.remote;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

public final class LocalIpv4Address {
    private LocalIpv4Address() {
    }

    public static String find() {
        try {
            List<NetworkInterface> interfaces =
                    Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface networkInterface : interfaces) {
                String address = firstUsable(
                        Collections.list(networkInterface.getInetAddresses()));
                if (address != null) return address;
            }
        } catch (Exception error) {
            error.printStackTrace();
        }
        return null;
    }

    static String firstUsable(List<InetAddress> addresses) {
        for (InetAddress address : addresses) {
            if (!address.isLoopbackAddress() && address.getHostAddress().indexOf(':') < 0) {
                return address.getHostAddress();
            }
        }
        return null;
    }
}
