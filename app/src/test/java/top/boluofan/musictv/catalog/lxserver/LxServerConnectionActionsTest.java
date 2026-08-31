package top.boluofan.musictv.catalog.lxserver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.InterruptedIOException;
import java.lang.reflect.Proxy;
import java.util.Map;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.ResponseBody;
import okio.Timeout;
import org.junit.Test;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import top.boluofan.musictv.catalog.LxApiService;
import top.boluofan.musictv.catalog.model.LoginResponse;

public class LxServerConnectionActionsTest {
    @Test
    public void publicConnectionChecksTagsAndKeepsProvidedToken() {
        FakeService service = new FakeService();
        service.tags = FakeCall.success(body("{}"));
        Recorder recorder = new Recorder();

        new LxServerConnectionActions(url -> service.api)
                .connect("http://server/", "", "", "existing-token", recorder);

        assertEquals("wy", service.tagsSource);
        assertEquals("existing-token", recorder.token);
        assertEquals("服务器连接成功，将使用公共功能", recorder.successMessage);
        assertNull(recorder.failure);
    }

    @Test
    public void authenticatedConnectionReturnsLoginTokenAndCredentials() {
        FakeService service = new FakeService();
        LoginResponse response = new LoginResponse();
        response.setSuccess(true);
        response.setToken("new-token");
        service.login = FakeCall.success(response);
        Recorder recorder = new Recorder();

        new LxServerConnectionActions(url -> service.api)
                .connect("http://server/", "user", "password", "old-token", recorder);

        assertEquals("user", service.credentials.get("username"));
        assertEquals("password", service.credentials.get("password"));
        assertEquals("new-token", recorder.token);
        assertEquals("登录成功", recorder.successMessage);
    }

    @Test
    public void reportsHttpLoginAndTransportFailures() {
        FakeService httpService = new FakeService();
        httpService.tags = FakeCall.response(Response.error(503, body("{}")));
        Recorder http = new Recorder();
        new LxServerConnectionActions(url -> httpService.api)
                .connect("http://server/", "", "", "", http);

        FakeService loginService = new FakeService();
        LoginResponse rejected = new LoginResponse();
        rejected.setMessage("");
        loginService.login = FakeCall.success(rejected);
        Recorder login = new Recorder();
        new LxServerConnectionActions(url -> loginService.api)
                .connect("http://server/", "user", "password", "", login);

        assertEquals("服务器返回 HTTP 503", http.failure);
        assertEquals("用户名或密码错误", login.failure);
        assertEquals("连接超时，请检查地址和网络",
                LxServerConnectionActions.failureMessage(new InterruptedIOException()));
    }

    @Test
    public void cancellationCancelsCallAndSuppressesLateResult() {
        FakeService service = new FakeService();
        FakeCall<ResponseBody> call = FakeCall.manual();
        service.tags = call;
        Recorder recorder = new Recorder();

        LxServerConnectionActions.Operation operation =
                new LxServerConnectionActions(url -> service.api)
                        .connect("http://server/", "", "", "", recorder);
        operation.cancel();
        call.dispatchResponse(Response.success(body("{}")));

        assertTrue(call.isCanceled());
        assertNull(recorder.successMessage);
        assertNull(recorder.failure);
    }

    private static ResponseBody body(String json) {
        return ResponseBody.create(MediaType.parse("application/json"), json);
    }

    private static final class Recorder implements LxServerConnectionActions.Callback {
        String token;
        String successMessage;
        String failure;

        @Override
        public void onSuccess(String token, String message) {
            this.token = token;
            successMessage = message;
        }

        @Override
        public void onFailure(String message) {
            failure = message;
        }
    }

    private static final class FakeService {
        final LxApiService api;
        FakeCall<ResponseBody> tags;
        FakeCall<LoginResponse> login;
        String tagsSource;
        Map<String, String> credentials;

        @SuppressWarnings("unchecked")
        FakeService() {
            api = (LxApiService) Proxy.newProxyInstance(
                    LxApiService.class.getClassLoader(), new Class<?>[]{LxApiService.class},
                    (proxy, method, args) -> {
                        if ("getSongListTags".equals(method.getName())) {
                            tagsSource = (String) args[0];
                            return tags;
                        }
                        if ("loginUser".equals(method.getName())) {
                            credentials = (Map<String, String>) args[0];
                            return login;
                        }
                        throw new UnsupportedOperationException(method.getName());
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
            return response(Response.success(value));
        }

        static <T> FakeCall<T> response(Response<T> response) {
            return new FakeCall<>(response, false);
        }

        static <T> FakeCall<T> manual() {
            return new FakeCall<>(null, true);
        }

        @Override
        public Response<T> execute() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void enqueue(Callback<T> callback) {
            this.callback = callback;
            if (!manual) callback.onResponse(this, response);
        }

        void dispatchResponse(Response<T> response) {
            callback.onResponse(this, response);
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
