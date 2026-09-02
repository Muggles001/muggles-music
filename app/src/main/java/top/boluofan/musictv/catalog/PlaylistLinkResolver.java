package top.boluofan.musictv.catalog;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class PlaylistLinkResolver {
    private static final int MAX_REDIRECTS = 5;
    private final RedirectTransport transport;

    public PlaylistLinkResolver() {
        this(new OkHttpRedirectTransport());
    }

    PlaylistLinkResolver(RedirectTransport transport) {
        this.transport = transport;
    }

    public PlaylistReference resolve(String input, String sourceHint) throws Failure {
        String currentInput = input;
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            PlaylistLinkParser.Result parsed;
            try {
                parsed = PlaylistLinkParser.parse(currentInput, sourceHint);
            } catch (PlaylistLinkParser.Failure failure) {
                throw new Failure(Kind.INVALID_INPUT, failure);
            }
            if (!parsed.requiresExpansion()) return parsed.getReference();
            if (redirect == MAX_REDIRECTS) throw new Failure(Kind.TOO_MANY_REDIRECTS, null);

            String next;
            try {
                next = transport.redirect(parsed.getExpansionUrl());
            } catch (IOException error) {
                throw new Failure(Kind.TRANSPORT, error);
            }
            if (next == null || next.trim().isEmpty()) {
                throw new Failure(Kind.MISSING_REDIRECT, null);
            }
            currentInput = next;
        }
        throw new Failure(Kind.TOO_MANY_REDIRECTS, null);
    }

    interface RedirectTransport {
        String redirect(String url) throws IOException;
    }

    private static final class OkHttpRedirectTransport implements RedirectTransport {
        private final OkHttpClient client = new OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .callTimeout(12, TimeUnit.SECONDS)
                .build();

        @Override
        public String redirect(String url) throws IOException {
            HttpUrl parsed = HttpUrl.parse(url);
            if (parsed == null) throw new IOException("短链地址无效");
            rejectLocalAddresses(parsed.host());
            Request request = new Request.Builder()
                    .url(parsed)
                    .header("User-Agent", "MugglesMusicTV/2")
                    .get()
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (response.code() < 300 || response.code() >= 400) return null;
                String location = response.header("Location");
                if (location == null) return null;
                HttpUrl next = parsed.resolve(location);
                return next == null ? null : next.toString();
            }
        }

        private static void rejectLocalAddresses(String host) throws IOException {
            try {
                for (InetAddress address : InetAddress.getAllByName(host)) {
                    if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                            || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                            || address.isMulticastAddress()) {
                        throw new IOException("短链目标不是公网地址");
                    }
                }
            } catch (UnknownHostException error) {
                throw new IOException("短链域名无法解析", error);
            }
        }
    }

    public enum Kind {
        INVALID_INPUT,
        TOO_MANY_REDIRECTS,
        MISSING_REDIRECT,
        TRANSPORT
    }

    public static final class Failure extends Exception {
        private final Kind kind;

        Failure(Kind kind, Throwable cause) {
            super(kind.name(), cause);
            this.kind = kind;
        }

        public Kind getKind() {
            return kind;
        }
    }
}
