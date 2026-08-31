package top.boluofan.musictv.player.lyrics;

import android.content.Context;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Response;
import top.boluofan.musictv.catalog.LxApiService;
import top.boluofan.musictv.catalog.MusicApiProvider;
import top.boluofan.musictv.catalog.lxserver.LxRetrofitClient;
import top.boluofan.musictv.catalog.model.LyricInfo;

public final class LyricCatalogActions {
    interface Credentials {
        String username();
        String password();
        String token();
    }

    private final LxApiService apiService;
    private final Credentials credentials;

    public LyricCatalogActions(Context context) {
        Context appContext = context.getApplicationContext();
        apiService = MusicApiProvider.get(appContext);
        credentials = new Credentials() {
            @Override public String username() {
                return LxRetrofitClient.getUsername(appContext);
            }

            @Override public String password() {
                return LxRetrofitClient.getPassword(appContext);
            }

            @Override public String token() {
                return LxRetrofitClient.getToken(appContext);
            }
        };
    }

    LyricCatalogActions(LxApiService apiService, Credentials credentials) {
        this.apiService = apiService;
        this.credentials = credentials;
    }

    public LyricRequestCoordinator.Cancelable request(
            Map<String, String> params, Callback callback) {
        Call<LyricInfo> call = apiService.getLyric(
                credentials.username(), credentials.password(), credentials.token(), params);
        call.enqueue(new retrofit2.Callback<LyricInfo>() {
            @Override
            public void onResponse(Call<LyricInfo> request, Response<LyricInfo> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onFailure("HTTP " + response.code());
                    return;
                }
                LyricInfo lyric = response.body();
                callback.onSuccess(
                        lyric.getPrimaryLyric(), lyric.getLxlyric(), lyric.getTlyric());
            }

            @Override
            public void onFailure(Call<LyricInfo> request, Throwable error) {
                callback.onFailure(error == null ? null : error.getMessage());
            }
        });
        return call::cancel;
    }

    public interface Callback {
        void onSuccess(String primary, String lxlyric, String translated);

        void onFailure(String message);
    }
}
