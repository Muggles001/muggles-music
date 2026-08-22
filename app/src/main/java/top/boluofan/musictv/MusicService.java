package top.boluofan.musictv;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.ResolvingDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.session.DefaultMediaNotificationProvider;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import retrofit2.Response;
import top.boluofan.musictv.backend.BackendMode;
import top.boluofan.musictv.backend.BackendPreferences;
import top.boluofan.musictv.api.LxApiService;
import top.boluofan.musictv.api.LxRetrofitClient;
import top.boluofan.musictv.api.model.LoginResponse;
import top.boluofan.musictv.api.model.MusicInfo;
import top.boluofan.musictv.api.model.MusicUrlResponse;
import top.boluofan.musictv.source.SourceRuntimeManager;

public class MusicService extends MediaSessionService {
    private static final String TAG = "MusicService";
    private static final String RESOLVE_SCHEME = "lxmusic";
    private static final String RESOLVE_HOST = "resolve";
    private static final Gson GSON = new Gson();

    private MediaSession mediaSession;
    private ExoPlayer player;

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void onCreate() {
        super.onCreate();

        DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        ResolvingDataSource.Factory resolvingFactory = new ResolvingDataSource.Factory(
                httpDataSourceFactory,
                dataSpec -> {
                    Uri uri = dataSpec.uri;
                    if (RESOLVE_SCHEME.equals(uri.getScheme()) && RESOLVE_HOST.equals(uri.getHost())) {
                        String source = uri.getQueryParameter("source");
                        String songmid = uri.getQueryParameter("songmid");
                        String name = uri.getQueryParameter("name");
                        String payload = uri.getQueryParameter("payload");

                        if (source == null || songmid == null || songmid.isEmpty()) {
                            throw new IOException("Missing or empty source or songmid for URL resolution");
                        }

                        Log.d(TAG, "Resolving URL for: source=" + source + ", songmid=" + songmid + ", name=" + name);

                        String resolvedUrl = resolveMusicUrlSync(source, songmid, name, payload);
                        if (resolvedUrl == null || resolvedUrl.isEmpty()) {
                            throw new IOException("Failed to resolve music URL");
                        }

                        Log.d(TAG, "Resolved URL: " + resolvedUrl);

                        resolvedUrl = preparePlaybackUrl(resolvedUrl);

                        return dataSpec.withUri(Uri.parse(resolvedUrl));
                    }
                    return dataSpec;
                }
        );

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(this).setDataSourceFactory(resolvingFactory))
                .setAudioAttributes(audioAttributes, true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build();

        Intent intent = new Intent(this, top.boluofan.musictv.ui.MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        mediaSession = new MediaSession.Builder(this, player)
                .setSessionActivity(pendingIntent)
                .setId("io.github.muggles001.mugglesmusic.session")
                .build();

        DefaultMediaNotificationProvider notificationProvider = new DefaultMediaNotificationProvider.Builder(this).build();
        setMediaNotificationProvider(notificationProvider);
    }

    private String fixUrlFormat(String url) {
        if (url == null) return url;
        
        if (url.contains("&redirect=1") && !url.contains("?")) {
            url = url.replace("&redirect=1", "?redirect=1");
            Log.d(TAG, "Fixed URL format: " + url);
        }
        
        return url;
    }

    private String preparePlaybackUrl(String url) throws IOException {
        String resolvedUrl = fixUrlFormat(url);
        if (BackendPreferences.getMode(this) == BackendMode.DIRECT_SOURCE) {
            Uri directUri = Uri.parse(resolvedUrl);
            String directScheme = directUri.getScheme();
            if (!"http".equalsIgnoreCase(directScheme)
                    && !"https".equalsIgnoreCase(directScheme)) {
                throw new IOException("直连音源必须返回完整的 HTTP/HTTPS 播放地址");
            }
            return resolvedUrl;
        }
        String serverBaseUrl = LxRetrofitClient.getPureServerUrl(this);
        if (serverBaseUrl == null || serverBaseUrl.isEmpty()) {
            throw new IOException("未配置有效的 LXserver 地址，请先在设置中保存服务器地址");
        }

        Uri resolvedUri = Uri.parse(resolvedUrl);
        String scheme = resolvedUri.getScheme();
        if (scheme == null || scheme.isEmpty()) {
            String relativePath = resolvedUrl.startsWith("/") ? resolvedUrl : "/" + resolvedUrl;
            return serverBaseUrl + relativePath;
        }

        // A custom source may return a loopback URL that is reachable from the
        // LXserver host but not from the TV. The web player proxies such URLs
        // through LXserver; do the same here instead of connecting to the TV's
        // own localhost.
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
        String normalized = host.trim().toLowerCase(java.util.Locale.US);
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
        if (BackendPreferences.getMode(this) == BackendMode.DIRECT_SOURCE) {
            JsonObject musicInfo = new JsonObject();
            if (payload != null && !payload.isEmpty()) {
                try {
                    com.google.gson.JsonElement parsed = new JsonParser().parse(payload);
                    if (parsed.isJsonObject()) musicInfo = parsed.getAsJsonObject();
                } catch (Exception ignored) {
                }
            }
            musicInfo.addProperty("source", source);
            musicInfo.addProperty("songmid", songmid);
            if (name != null && !name.isEmpty()) musicInfo.addProperty("name", name);
            return SourceRuntimeManager.get(this).resolveMusicUrlBlocking(
                    source, musicInfo, LxRetrofitClient.getQuality(this));
        }

        String serverUrl = LxRetrofitClient.getServerUrl(this);
        if (LxRetrofitClient.normalizeServerUrl(serverUrl) == null) {
            throw new IOException("未配置有效的 LXserver 地址，请先在设置中保存服务器地址");
        }

        // MusicService can survive a configuration change. Always acquire the
        // client for the latest saved base URL instead of retaining the proxy
        // that existed when the service was first created.
        final LxApiService currentApiService;
        try {
            currentApiService = LxRetrofitClient.getApiService(this);
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new IOException("LXserver 地址无效: " + serverUrl, e);
        }

        Map<String, Object> body = new HashMap<>();
        Map<String, Object> songInfo = new HashMap<>();
        songInfo.put("source", source);
        songInfo.put("songmid", songmid);
        if (name != null) {
            songInfo.put("name", name);
        }
        body.put("songInfo", songInfo);
        body.put("quality", LxRetrofitClient.getQuality(this));

        String username = LxRetrofitClient.getUsername(this);
        String password = LxRetrofitClient.getPassword(this);
        String token = LxRetrofitClient.getToken(this);

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
                    String url = urlResponse.getUrl();
                    Log.d(TAG, "API returned URL: " + url);
                    return url;
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

    private String loginAndSaveToken(LxApiService currentApiService, String username, String password) {
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
                    LxRetrofitClient.saveToken(this, token);
                    return token;
                }
            }
            Log.e(TAG, "User login failed while refreshing playback token: " + response.code());
        } catch (IOException e) {
            Log.e(TAG, "Failed to refresh playback token: " + e.getMessage());
        }
        return null;
    }

    public static Uri buildResolveUri(String source, String songmid, String name) {
        Uri.Builder builder = new Uri.Builder()
                .scheme(RESOLVE_SCHEME)
                .authority(RESOLVE_HOST)
                .appendQueryParameter("source", source)
                .appendQueryParameter("songmid", songmid);
        if (name != null) {
            builder.appendQueryParameter("name", name);
        }
        return builder.build();
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

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (player != null && !player.getPlayWhenReady()) {
            stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }

    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }
}
