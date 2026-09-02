package top.boluofan.musictv.catalog;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlaylistLinkParser {
    public static final String SOURCE_AUTO = "auto";
    private static final int MAX_INPUT_LENGTH = 4096;
    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://[^\\s<>\\[\\](){}\\\"',;!，。；！【】《》]+",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MARKDOWN_LINK_PATTERN = Pattern.compile(
            "\\[[^\\]]*\\]\\(\\s*([^\\s<>\\[\\](){}\\\"']+)[^)]*\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern AMP_ENTITY_PATTERN = Pattern.compile(
            "&(?:amp|#0*38|#x0*26);", Pattern.CASE_INSENSITIVE);
    private static final Pattern PREFIXED_ID = Pattern.compile("^(wy|tx|kw|kg|mg)[:_]{1,2}([A-Za-z0-9_-]+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PATH_NUMBER = Pattern.compile("(?:^|/)([0-9]{3,})(?:\\.html)?(?:/|$)");
    private static final Map<String, String> HOST_SOURCES = hostSources();
    private static final Map<String, String> SHORT_HOST_SOURCES = shortHostSources();

    private PlaylistLinkParser() {
    }

    public static Result parse(String input, String sourceHint) throws Failure {
        String rawInput = input == null ? "" : input.trim();
        if (rawInput.isEmpty()) throw new Failure(Kind.EMPTY, null);
        if (rawInput.length() > MAX_INPUT_LENGTH) {
            throw new Failure(Kind.TOO_LONG, null);
        }

        String normalizedInput = normalizeShareInput(rawInput).trim();
        if (normalizedInput.isEmpty()) throw new Failure(Kind.EMPTY, null);
        String hint = normalizeSource(sourceHint);
        Failure firstFailure = null;
        boolean foundMarkdown = false;

        Matcher markdownMatcher = MARKDOWN_LINK_PATTERN.matcher(normalizedInput);
        while (markdownMatcher.find()) {
            foundMarkdown = true;
            try {
                return parseUrl(markdownMatcher.group(1));
            } catch (Failure failure) {
                if (firstFailure == null) firstFailure = failure;
            }
        }
        if (foundMarkdown) throw firstFailure;

        Matcher urlMatcher = URL_PATTERN.matcher(normalizedInput);
        boolean foundUrl = false;
        while (urlMatcher.find()) {
            foundUrl = true;
            try {
                return parseUrl(urlMatcher.group());
            } catch (Failure failure) {
                if (firstFailure == null) firstFailure = failure;
            }
        }
        if (foundUrl) throw firstFailure;
        return parseIdentifier(normalizedInput, hint);
    }

    private static Result parseUrl(String candidate) throws Failure {
        String url = trimTrailingPunctuation(candidate);
        URI uri = parseUri(url);
        String scheme = lower(uri.getScheme());
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new Failure(Kind.UNSUPPORTED_SCHEME, url);
        }
        if (uri.getUserInfo() != null || (uri.getPort() != -1
                && uri.getPort() != 80 && uri.getPort() != 443)) {
            throw new Failure(Kind.UNSAFE_URL, url);
        }

        String host = lower(uri.getHost());
        String shortSource = SHORT_HOST_SOURCES.get(host);
        if (shortSource != null) {
            return Result.expansion(url, shortSource);
        }

        String source = HOST_SOURCES.get(host);
        if (source == null) throw new Failure(Kind.UNSUPPORTED_HOST, url);

        String id = extractId(source, uri, url);
        if (!hasText(id)) throw new Failure(Kind.MISSING_ID, url);
        String normalizedId = normalizeId(source, id);
        String detailRequestId = "kg".equals(source) && normalizedId.startsWith("gcid_")
                ? url : normalizedId;
        return Result.reference(new PlaylistReference(
                source, normalizedId, url, detailRequestId));
    }

    private static Result parseIdentifier(String input, String hint) throws Failure {
        Matcher prefixed = PREFIXED_ID.matcher(input);
        if (prefixed.matches()) {
            String source = lower(prefixed.group(1));
            return Result.reference(new PlaylistReference(
                    source, normalizeId(source, prefixed.group(2)), null));
        }
        if (!isSource(hint)) throw new Failure(Kind.PLATFORM_REQUIRED, null);
        if (!input.matches("[A-Za-z0-9_-]{1,256}")) {
            throw new Failure(Kind.MALFORMED_ID, null);
        }
        return Result.reference(new PlaylistReference(hint, normalizeId(hint, input), null));
    }

    private static String extractId(String source, URI uri, String rawUrl) {
        String queryId = firstQueryValue(uri.getRawQuery(), "id", "playlistId", "specialid");
        if (!hasText(queryId)) {
            int fragmentQuery = rawUrl.indexOf('?');
            if (fragmentQuery >= 0) {
                queryId = firstQueryValue(rawUrl.substring(fragmentQuery + 1),
                        "id", "playlistId", "specialid");
            }
        }
        if (hasText(queryId)) return queryId;

        String path = uri.getPath() == null ? "" : uri.getPath();
        if ("kw".equals(source)) {
            String value = group(path, "/(?:playlist_detail|playlist)/(\\d+)");
            if (value != null) return value;
        } else if ("kg".equals(source)) {
            String value = group(path, "/songlist/(gcid_[A-Za-z0-9_]+)");
            if (value != null) return value;
            value = group(path, "/(?:yy/)?special/single/(\\d+)(?:\\.html)?");
            if (value != null) return value;
        } else if ("mg".equals(source)) {
            String value = group(path, "/playlist/(\\d+)");
            if (value != null) return value;
        } else if ("tx".equals(source)) {
            String value = group(path, "/(?:playlist|taoge)/(?:[^/]+/)?([A-Za-z0-9_-]+)");
            if (value != null) return value;
        } else if ("wy".equals(source)) {
            String value = group(path, "/playlist/(\\d+)");
            if (value != null) return value;
        }

        Matcher number = PATH_NUMBER.matcher(path);
        String last = null;
        while (number.find()) last = number.group(1);
        return last;
    }

    private static String firstQueryValue(String rawQuery, String... keys) {
        if (!hasText(rawQuery)) return null;
        String query = rawQuery;
        int hash = query.indexOf('#');
        if (hash >= 0) query = query.substring(0, hash);
        for (String pair : query.split("&")) {
            int split = pair.indexOf('=');
            if (split <= 0) continue;
            String key = decode(pair.substring(0, split));
            for (String expected : keys) {
                if (expected.equalsIgnoreCase(key)) {
                    return decode(pair.substring(split + 1));
                }
            }
        }
        return null;
    }

    private static String normalizeId(String source, String raw) throws Failure {
        String value = raw == null ? "" : raw.trim();
        String lower = lower(value);
        String[] prefixes = {source + "__", source + "_", source + ":"};
        for (String prefix : prefixes) {
            if (lower.startsWith(prefix)) {
                value = value.substring(prefix.length());
                break;
            }
        }
        if (!value.matches("[A-Za-z0-9_-]{1,256}")) {
            throw new Failure(Kind.MALFORMED_ID, null);
        }
        return value;
    }

    private static URI parseUri(String url) throws Failure {
        try {
            URI uri = new URI(url);
            if (!hasText(uri.getHost())) throw new Failure(Kind.MALFORMED_URL, url);
            return uri;
        } catch (URISyntaxException e) {
            throw new Failure(Kind.MALFORMED_URL, url);
        }
    }

    private static String normalizeSource(String source) {
        String normalized = lower(source == null ? "" : source.trim());
        return isSource(normalized) ? normalized : SOURCE_AUTO;
    }

    private static boolean isSource(String source) {
        return "wy".equals(source) || "tx".equals(source) || "kw".equals(source)
                || "kg".equals(source) || "mg".equals(source);
    }

    private static String group(String value, String expression) {
        Matcher matcher = Pattern.compile(expression, Pattern.CASE_INSENSITIVE).matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String trimTrailingPunctuation(String url) {
        String value = url;
        while (!value.isEmpty() && ").,;，。；！!】]》>\"'".indexOf(
                value.charAt(value.length() - 1)) >= 0) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String normalizeShareInput(String input) {
        StringBuilder normalized = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char value = input.charAt(i);
            if (Character.getType(value) == Character.FORMAT) continue;
            if (value == '\\' && i + 1 < input.length()
                    && isAsciiPunctuation(input.charAt(i + 1))) {
                normalized.append(toAsciiPunctuation(input.charAt(++i)));
                continue;
            }
            normalized.append(toAsciiPunctuation(value));
        }
        return AMP_ENTITY_PATTERN.matcher(normalized).replaceAll("&");
    }

    private static boolean isAsciiPunctuation(char value) {
        return (value >= '!' && value <= '/') || (value >= ':' && value <= '@')
                || (value >= '[' && value <= '`') || (value >= '{' && value <= '~');
    }

    private static char toAsciiPunctuation(char value) {
        if (value == '\uFF06') return '&';
        if (value == '\uFF03') return '#';
        if (value == '\uFF0E') return '.';
        if (value == '\uFF0F') return '/';
        if (value == '\uFF1A') return ':';
        if (value == '\uFF1D') return '=';
        if (value == '\uFF1F') return '?';
        return value;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            return value;
        }
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static Map<String, String> hostSources() {
        Map<String, String> hosts = new HashMap<>();
        add(hosts, "wy", "music.163.com", "y.music.163.com");
        add(hosts, "tx", "y.qq.com", "i.y.qq.com", "i2.y.qq.com", "c.y.qq.com", "music.qq.com");
        add(hosts, "kw", "kuwo.cn", "www.kuwo.cn", "m.kuwo.cn", "h5app.kuwo.cn");
        add(hosts, "kg", "kugou.com", "www.kugou.com", "m.kugou.com");
        add(hosts, "mg", "music.migu.cn", "m.music.migu.cn", "h5.nf.migu.cn");
        return Collections.unmodifiableMap(hosts);
    }

    private static Map<String, String> shortHostSources() {
        Map<String, String> hosts = new HashMap<>();
        add(hosts, "wy", "163cn.tv");
        add(hosts, "tx", "c6.y.qq.com");
        add(hosts, "kg", "t.kugou.com");
        add(hosts, "mg", "c.migu.cn");
        return Collections.unmodifiableMap(hosts);
    }

    private static void add(Map<String, String> hosts, String source, String... names) {
        for (String name : names) hosts.put(name, source);
    }

    public enum Kind {
        EMPTY,
        TOO_LONG,
        UNSUPPORTED_SCHEME,
        UNSAFE_URL,
        UNSUPPORTED_HOST,
        MALFORMED_URL,
        MISSING_ID,
        PLATFORM_REQUIRED,
        MALFORMED_ID
    }

    public static final class Failure extends Exception {
        private final Kind kind;
        private final String inputUrl;

        Failure(Kind kind, String inputUrl) {
            super(kind.name());
            this.kind = kind;
            this.inputUrl = inputUrl;
        }

        public Kind getKind() {
            return kind;
        }

        public String getInputUrl() {
            return inputUrl;
        }
    }

    public static final class Result {
        private final PlaylistReference reference;
        private final String expansionUrl;
        private final String source;

        private Result(PlaylistReference reference, String expansionUrl, String source) {
            this.reference = reference;
            this.expansionUrl = expansionUrl;
            this.source = source;
        }

        static Result reference(PlaylistReference reference) {
            return new Result(reference, null, reference.getSource());
        }

        static Result expansion(String url, String source) {
            return new Result(null, url, source);
        }

        public boolean requiresExpansion() {
            return expansionUrl != null;
        }

        public PlaylistReference getReference() {
            return reference;
        }

        public String getExpansionUrl() {
            return expansionUrl;
        }

        public String getSource() {
            return source;
        }
    }
}
