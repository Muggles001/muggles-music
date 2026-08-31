package top.boluofan.musictv.player.lyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;
import okhttp3.Request;
import okio.Timeout;
import org.junit.Test;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import top.boluofan.musictv.catalog.LxApiService;
import top.boluofan.musictv.catalog.model.LyricInfo;

public class LyricCatalogActionsTest {
    @Test
    public void returnsAllLyricVariantsAndFreshCredentials() {
        LyricInfo info = new LyricInfo();
        info.setLyric("primary");
        info.setLxlyric("lx");
        info.setTlyric("translated");
        FakeService service = new FakeService(FakeCall.success(info));
        Recorder recorder = new Recorder();

        new LyricCatalogActions(service.api, new FixedCredentials())
                .request(Collections.singletonMap("songmid", "id"), recorder);

        assertEquals("user", service.username);
        assertEquals("password", service.password);
        assertEquals("token", service.token);
        assertEquals("id", service.params.get("songmid"));
        assertEquals("primary", recorder.primary);
        assertEquals("lx", recorder.lxlyric);
        assertEquals("translated", recorder.translated);
    }

    @Test
    public void cancelableCancelsUnderlyingCall() {
        FakeCall<LyricInfo> call = FakeCall.manual();
        LyricRequestCoordinator.Cancelable cancelable = new LyricCatalogActions(
                new FakeService(call).api, new FixedCredentials())
                .request(Collections.emptyMap(), new Recorder());

        cancelable.cancel();

        assertTrue(call.isCanceled());
    }

    private static final class FixedCredentials implements LyricCatalogActions.Credentials {
        @Override public String username() { return "user"; }
        @Override public String password() { return "password"; }
        @Override public String token() { return "token"; }
    }

    private static final class Recorder implements LyricCatalogActions.Callback {
        String primary;
        String lxlyric;
        String translated;

        @Override
        public void onSuccess(String primary, String lxlyric, String translated) {
            this.primary = primary;
            this.lxlyric = lxlyric;
            this.translated = translated;
        }

        @Override public void onFailure(String message) { }
    }

    private static final class FakeService {
        final LxApiService api;
        String username;
        String password;
        String token;
        Map<String, String> params;

        @SuppressWarnings("unchecked")
        FakeService(FakeCall<LyricInfo> call) {
            api = (LxApiService) Proxy.newProxyInstance(
                    LxApiService.class.getClassLoader(), new Class<?>[]{LxApiService.class},
                    (proxy, method, args) -> {
                        if (!"getLyric".equals(method.getName())) {
                            throw new UnsupportedOperationException(method.getName());
                        }
                        username = (String) args[0];
                        password = (String) args[1];
                        token = (String) args[2];
                        params = (Map<String, String>) args[3];
                        return call;
                    });
        }
    }

    private static final class FakeCall<T> implements Call<T> {
        private final Response<T> response;
        private final boolean manual;
        private Callback<T> callback;
        private boolean canceled;

        private FakeCall(Response<T> response, boolean manual) {
            this.response = response;
            this.manual = manual;
        }

        static <T> FakeCall<T> success(T value) {
            return new FakeCall<>(Response.success(value), false);
        }

        static <T> FakeCall<T> manual() { return new FakeCall<>(null, true); }
        @Override public Response<T> execute() { throw new UnsupportedOperationException(); }
        @Override public void enqueue(Callback<T> callback) {
            this.callback = callback;
            if (!manual) callback.onResponse(this, response);
        }
        @Override public boolean isExecuted() { return callback != null; }
        @Override public void cancel() { canceled = true; }
        @Override public boolean isCanceled() { return canceled; }
        @Override public Call<T> clone() { return new FakeCall<>(response, manual); }
        @Override public Request request() {
            return new Request.Builder().url("http://127.0.0.1/").build();
        }
        @Override public Timeout timeout() { return Timeout.NONE; }
    }
}
