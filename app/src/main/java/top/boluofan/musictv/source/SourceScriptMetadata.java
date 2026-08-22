package top.boluofan.musictv.source;

import androidx.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SourceScriptMetadata {
    private static final Pattern HEADER = Pattern.compile("/\\*[*!]([\\s\\S]*?)\\*/");
    private static final Pattern FIELD = Pattern.compile("(?m)^\\s*\\*?\\s*@([a-zA-Z]+)\\s+(.+?)\\s*$");

    public final String id;
    public final String name;
    public final String description;
    public final String version;
    public final String author;
    public final String homepage;
    public final String sha256;

    private SourceScriptMetadata(String id, String name, String description, String version,
                                 String author, String homepage, String sha256) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.version = version;
        this.author = author;
        this.homepage = homepage;
        this.sha256 = sha256;
    }

    public static SourceScriptMetadata parse(String script) {
        if (script == null || script.trim().isEmpty()) {
            throw new IllegalArgumentException("音源脚本为空");
        }
        Matcher headerMatcher = HEADER.matcher(script);
        if (!headerMatcher.find()) {
            throw new IllegalArgumentException("音源脚本缺少头部信息");
        }

        String name = "";
        String description = "";
        String version = "";
        String author = "";
        String homepage = "";
        Matcher fieldMatcher = FIELD.matcher(headerMatcher.group(1));
        while (fieldMatcher.find()) {
            String key = fieldMatcher.group(1).toLowerCase(Locale.US);
            String value = fieldMatcher.group(2).trim();
            switch (key) {
                case "name": name = value; break;
                case "description": description = value; break;
                case "version": version = value; break;
                case "author": author = value; break;
                case "homepage":
                case "repository": homepage = value; break;
                default: break;
            }
        }
        if (name.isEmpty()) {
            throw new IllegalArgumentException("音源脚本缺少 @name");
        }
        if (name.length() > 80) {
            throw new IllegalArgumentException("音源名称过长");
        }

        String digest = sha256(script);
        return new SourceScriptMetadata("source_" + digest.substring(0, 16), name,
                description, version, author, homepage, digest);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) result.append(String.format(Locale.US, "%02x", b));
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("无法计算音源摘要", e);
        }
    }

    @Nullable
    public static String normalizeImportUrl(String rawUrl) {
        if (rawUrl == null) return null;
        String value = rawUrl.trim();
        if (value.isEmpty()) return null;
        try {
            java.net.URI uri = new java.net.URI(value);
            String scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null) return null;
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return null;
            return uri.toString();
        } catch (Exception ignored) {
            return null;
        }
    }
}
