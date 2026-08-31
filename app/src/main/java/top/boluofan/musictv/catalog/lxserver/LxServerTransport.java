package top.boluofan.musictv.catalog.lxserver;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import top.boluofan.musictv.catalog.LxApiService;

public final class LxServerTransport {
    private static Retrofit retrofit;
    private static String currentBaseUrl;

    private LxServerTransport() {
    }

    public static LxApiService getApiService(Context context, String rawUrl) {
        String baseUrl = normalizeServerUrl(rawUrl);
        if (baseUrl == null) {
            throw new IllegalStateException("LXserver address is missing or invalid");
        }
        if (retrofit == null || !baseUrl.equals(currentBaseUrl)) {
            currentBaseUrl = baseUrl;
            retrofit = createRetrofit(context, baseUrl);
        }
        return retrofit.create(LxApiService.class);
    }

    public static LxApiService createApiService(Context context, String rawUrl) {
        String baseUrl = normalizeServerUrl(rawUrl);
        if (baseUrl == null) throw new IllegalArgumentException("LXserver address is invalid");
        return createRetrofit(context, baseUrl).create(LxApiService.class);
    }

    public static String normalizeServerUrl(String rawUrl) {
        if (rawUrl == null) return null;
        String candidate = rawUrl.trim();
        if (candidate.isEmpty()) return null;
        boolean hasHttpScheme = candidate.regionMatches(true, 0, "http://", 0, 7)
                || candidate.regionMatches(true, 0, "https://", 0, 8);
        if (!hasHttpScheme) candidate = "https://" + candidate;
        HttpUrl parsed = HttpUrl.parse(candidate);
        if (parsed == null || parsed.host().isEmpty()) return null;
        HttpUrl.Builder builder = parsed.newBuilder().query(null).fragment(null);
        if (!parsed.encodedPath().endsWith("/")) builder.addPathSegment("");
        return builder.build().toString();
    }

    public static void resetClient() {
        retrofit = null;
        currentBaseUrl = null;
    }

    private static Retrofit createRetrofit(Context context, String baseUrl) {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        // BODY logging exposes account data and buffers large responses on low-memory TVs.
        boolean isDebuggable = (context.getApplicationInfo().flags
                & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        logging.setLevel(isDebuggable
                ? HttpLoggingInterceptor.Level.BODY
                : HttpLoggingInterceptor.Level.NONE);
        OkHttpClient client = new OkHttpClient.Builder()
                .callTimeout(25, TimeUnit.SECONDS)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build();
        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build();
    }
}
