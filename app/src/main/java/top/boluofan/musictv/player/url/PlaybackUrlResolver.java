package top.boluofan.musictv.player.url;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSpec;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import retrofit2.Response;
import top.boluofan.musictv.catalog.LxApiService;
import top.boluofan.musictv.catalog.MusicApiProvider;
import top.boluofan.musictv.catalog.direct.DirectSourcePlatforms;
import top.boluofan.musictv.catalog.lxserver.LxRetrofitClient;
import top.boluofan.musictv.catalog.model.LoginResponse;
import top.boluofan.musictv.catalog.model.MusicInfo;
import top.boluofan.musictv.catalog.model.MusicUrlResponse;
import top.boluofan.musictv.settings.BackendMode;
import top.boluofan.musictv.settings.BackendPreferences;
import top.boluofan.musictv.player.settings.AudioQualityPolicy;
import top.boluofan.musictv.settings.AppSettingsPreferences;
import top.boluofan.musictv.source.SourceRuntimeManager;

public final class PlaybackUrlResolver {
    private static final String TAG = "PlaybackUrlResolver";
    static final String RESOLVE_SCHEME = "lxmusic";
    static final String RESOLVE_HOST = "resolve";
    private static final Gson GSON = new Gson();

    private final Context context;
    private final AppSettingsPreferences settingsPreferences;
    private final Map<String, MusicInfo> directFallbackCache =
            new LinkedHashMap<String, MusicInfo>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, MusicInfo> eldest) {
                    return size() > 128;
                }
            };

    public PlaybackUrlResolver(Context context) {
        this.context = context.getApplicationContext();
        settingsPreferences = new AppSettingsPreferences(this.context);
    }

    @OptIn(markerClass = UnstableApi.class)
    public DataSpec resolve(DataSpec dataSpec) throws IOException {
        Uri uri = dataSpec.uri;
        if (!RESOLVE_SCHEME.equals(uri.getScheme()) || !RESOLVE_HOST.equals(uri.getHost())) {
            return dataSpec;
        }

        String source = uri.getQueryParameter("source");
        String songmid = uri.getQueryParameter("songmid");
        String name = uri.getQueryParameter("name");
        String payload = uri.getQueryParameter("payload");
        if (source == null || songmid == null || songmid.isEmpty()) {
            throw new IOException("Missing or empty source or songmid for URL resolution");
        }

        Log.d(TAG, "Resolving URL for: source=" + source + ", songmid=" + songmid
                + ", name=" + name);
        String resolvedUrl = resolveMusicUrlSync(source, songmid, name, payload);
        if (resolvedUrl == null || resolvedUrl.isEmpty()) {
            throw new IOException("Failed to resolve music URL");
        }
        Log.d(TAG, "Playback URL resolved");
        return dataSpec.withUri(Uri.parse(preparePlaybackUrl(resolvedUrl)));
    }

    private String fixUrlFormat(String url) {
        if (url == null) return null;
        if (url.contains("&redirect=1") && !url.contains("?")) {
            String fixed = url.replace("&redirect=1", "?redirect=1");
            Log.d(TAG, "Normalized playback URL query separator");
            return fixed;
        }
        return url;
    }

    private String preparePlaybackUrl(String url) throws IOException {
        String resolvedUrl = fixUrlFormat(url);
        if (BackendPreferences.getMode(context) == BackendMode.DIRECT_SOURCE) {
            Uri directUri = Uri.parse(resolvedUrl);
            String directScheme = directUri.getScheme();
            if (!"http".equalsIgnoreCase(directScheme)
                    && !"https".equalsIgnoreCase(directScheme)) {
                throw new IOException("直连音源必须返回完整的 HTTP/HTTPS 播放地址");
            }
            return resolvedUrl;
        }

        String serverBaseUrl = LxRetrofitClient.getPureServerUrl(context);
        if (serverBaseUrl == null || serverBaseUrl.isEmpty()) {
            throw new IOException("未配置有效的 LXserver 地址，请先在设置中保存服务器地址");
        }

        Uri resolvedUri = Uri.parse(resolvedUrl);
        String scheme = resolvedUri.getScheme();
        if (scheme == null || scheme.isEmpty()) {
            String relativePath = resolvedUrl.startsWith("/") ? resolvedUrl : "/" + resolvedUrl;
            return serverBaseUrl + relativePath;
        }

        Uri configuredServer = Uri.parse(serverBaseUrl);
        if (shouldProxyLoopback(resolvedUri.getHost(), configuredServer.getHost())) {
            return serverBaseUrl
                    + "/api/music/download?url=" + Uri.encode(resolvedUrl)
                    + "&inline=1";
        }
        return resolvedUrl;
    }

    static boolean shouldProxyLoopback(String resolvedHost, String configuredServerHost) {
        return isLoopbackHost(resolvedHost) && !isLoopbackHost(configuredServerHost);
    }

    static boolean isLoopbackHost(String host) {
        if (host == null) return false;
        String normalized = host.trim().toLowerCase(Locale.US);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        if (normalized.equals("localhost") || normalized.endsWith(".localhost")
                || normalized.equals("::1") || normalized.equals("0:0:0:0:0:0:0:1")
                || normalized.equals("::") || normalized.equals("0:0:0:0:0:0:0:0")
                || normalized.equals("0.0.0.0")) {
            return true;
        }
        if (normalized.startsWith("::ffff:")) {
            return isLoopbackHost(normalized.substring("::ffff:".length()));
        }

        String[] parts = normalized.split("\\.");
        if (parts.length != 4) return false;
        try {
            int first = Integer.parseInt(parts[0]);
            for (String part : parts) {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) return false;
            }
            return first == 127;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private String resolveMusicUrlSync(String source, String songmid, String name, String payload)
            throws IOException {
        if (BackendPreferences.getMode(context) == BackendMode.DIRECT_SOURCE) {
            JsonObject musicInfo = new JsonObject();
            if (payload != null && !payload.isEmpty()) {
                try {
                    JsonElement parsed = new JsonParser().parse(payload);
                    if (parsed.isJsonObject()) musicInfo = parsed.getAsJsonObject();
                } catch (Exception ignored) {
                }
            }
            musicInfo.addProperty("source", source);
            musicInfo.addProperty("songmid", songmid);
            if (name != null && !name.isEmpty()) musicInfo.addProperty("name", name);
            return resolveDirectMusicUrl(source, songmid, musicInfo);
        }

        String serverUrl = LxRetrofitClient.getServerUrl(context);
        if (LxRetrofitClient.normalizeServerUrl(serverUrl) == null) {
            throw new IOException("未配置有效的 LXserver 地址，请先在设置中保存服务器地址");
        }

        final LxApiService currentApiService;
        try {
            currentApiService = LxRetrofitClient.getApiService(context);
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new IOException("LXserver 地址无效: " + serverUrl, e);
        }

        Map<String, Object> body = new HashMap<>();
        Map<String, Object> songInfo = new HashMap<>();
        songInfo.put("source", source);
        songInfo.put("songmid", songmid);
        if (name != null) songInfo.put("name", name);
        body.put("songInfo", songInfo);
        body.put("quality", preferredQuality());

        String username = LxRetrofitClient.getUsername(context);
        String password = LxRetrofitClient.getPassword(context);
        String token = LxRetrofitClient.getToken(context);

        try {
            if ((token == null || token.isEmpty()) && !username.isEmpty() && !password.isEmpty()) {
                token = loginAndSaveToken(currentApiService, username, password);
            }

            Response<MusicUrlResponse> response = currentApiService
                    .getMusicUrl(username, password, token, body)
                    .execute();
            if (response.code() == 401 && !username.isEmpty() && !password.isEmpty()) {
                token = loginAndSaveToken(currentApiService, username, password);
                if (token != null && !token.isEmpty()) {
                    response = currentApiService
                            .getMusicUrl(username, password, token, body)
                            .execute();
                }
            }

            if (response.isSuccessful() && response.body() != null) {
                MusicUrlResponse urlResponse = response.body();
                if (urlResponse.isValid()) {
                    String resolvedUrl = urlResponse.getUrl();
                    Log.d(TAG, "LXserver returned a playback URL");
                    return resolvedUrl;
                }
                throw new IOException("LXserver returned an empty music URL");
            }

            String detail = "";
            if (response.errorBody() != null) {
                detail = response.errorBody().string();
                if (detail.length() > 8000) {
                    detail = detail.substring(0, 8000) + "\n…(内容超过 8000 字符)";
                }
            }
            throw new IOException("LXserver music URL request failed: HTTP "
                    + response.code() + (detail.isEmpty() ? "" : " - " + detail));
        } catch (IOException e) {
            Log.e(TAG, "Failed to resolve URL: " + e.getMessage());
            throw e;
        }
    }

    private String resolveDirectMusicUrl(String source, String songmid, JsonObject musicInfo)
            throws IOException {
        String[] supported = DirectSourcePlatforms.snapshot(context).codes();
        IOException originalFailure = null;
        if (contains(supported, source)) {
            try {
                return resolveWithSource(source, musicInfo);
            } catch (IOException error) {
                originalFailure = error;
                Log.w(TAG, "Original platform URL resolution failed; trying matched sources");
            }
        } else {
            originalFailure = new IOException("当前音源不支持歌曲原平台: " + source);
        }

        MusicInfo original = GSON.fromJson(musicInfo, MusicInfo.class);
        MusicInfo cached = cachedFallback(source + ':' + songmid);
        if (cached != null && contains(supported, cached.getSource())) {
            try {
                return resolveWithSource(cached.getSource(), GSON.toJsonTree(cached).getAsJsonObject());
            } catch (IOException ignored) {
                removeCachedFallback(source + ':' + songmid);
            }
        }

        LxApiService catalog = MusicApiProvider.get(context);
        String query = searchQuery(original);
        for (String fallbackSource : DirectPlaybackFallbackPolicy.fallbackSources(source, supported)) {
            try {
                Response<List<MusicInfo>> response = catalog
                        .searchMusic(query, fallbackSource, 1, 8).execute();
                if (!response.isSuccessful() || response.body() == null) continue;
                MusicInfo candidate = DirectPlaybackFallbackPolicy.bestMatch(
                        original, response.body());
                if (candidate == null) continue;
                String url = resolveWithSource(
                        candidate.getSource(), GSON.toJsonTree(candidate).getAsJsonObject());
                cacheFallback(source + ':' + songmid, candidate);
                return url;
            } catch (Exception error) {
                Log.w(TAG, "Fallback platform did not resolve a playable candidate: "
                        + fallbackSource);
            }
        }
        throw new IOException("原平台不可用，其他音源未找到可信匹配歌曲", originalFailure);
    }

    private String resolveWithSource(String source, JsonObject musicInfo) throws IOException {
        musicInfo.addProperty("source", source);
        return SourceRuntimeManager.get(context).resolveMusicUrlBlocking(
                source, musicInfo, chooseDirectSourceQuality(musicInfo));
    }

    private static String searchQuery(MusicInfo song) {
        String name = song == null ? "" : song.getName();
        String singer = song == null ? "" : song.getSinger();
        StringBuilder query = new StringBuilder();
        if (name != null) query.append(name.trim());
        if (singer != null && !singer.trim().isEmpty()) query.append(' ').append(singer.trim());
        return query.toString().trim();
    }

    private static boolean contains(String[] values, String target) {
        if (values == null || target == null) return false;
        for (String value : values) if (target.equals(value)) return true;
        return false;
    }

    private synchronized MusicInfo cachedFallback(String key) {
        return directFallbackCache.get(key);
    }

    private synchronized void cacheFallback(String key, MusicInfo song) {
        directFallbackCache.put(key, song);
    }

    private synchronized void removeCachedFallback(String key) {
        directFallbackCache.remove(key);
    }

    private String loginAndSaveToken(
            LxApiService currentApiService, String username, String password) {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("username", username);
        credentials.put("password", password);

        try {
            Response<LoginResponse> response = currentApiService
                    .loginUser(credentials)
                    .execute();
            if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                String token = response.body().getToken();
                if (token != null && !token.isEmpty()) {
                    LxRetrofitClient.saveToken(context, token);
                    return token;
                }
            }
            Log.e(TAG, "User login failed while refreshing playback token: " + response.code());
        } catch (IOException e) {
            Log.e(TAG, "Failed to refresh playback token: " + e.getMessage());
        }
        return null;
    }

    private String chooseDirectSourceQuality(JsonObject musicInfo) {
        Set<String> declared = new LinkedHashSet<>();
        try {
            MusicInfo song = GSON.fromJson(musicInfo, MusicInfo.class);
            List<MusicInfo.QualityInfo> types = song.getTypes();
            if (types != null) {
                for (MusicInfo.QualityInfo type : types) {
                    if (type != null && type.getType() != null) declared.add(type.getType());
                }
            }
            if (song.get_types() != null) declared.addAll(song.get_types().keySet());
        } catch (RuntimeException error) {
            Log.w(TAG, "Unable to inspect declared track qualities", error);
        }
        return AudioQualityPolicy.choose(preferredQuality(), declared);
    }

    private String preferredQuality() {
        return settingsPreferences.getPreferredQuality().getStorageValue();
    }

    public static Uri buildResolveUri(MusicInfo song) {
        Uri.Builder builder = new Uri.Builder()
                .scheme(RESOLVE_SCHEME)
                .authority(RESOLVE_HOST)
                .appendQueryParameter("source", song.getSource())
                .appendQueryParameter("songmid", song.getSongmid())
                .appendQueryParameter("payload", GSON.toJson(song));
        if (song.getName() != null) builder.appendQueryParameter("name", song.getName());
        return builder.build();
    }
}
