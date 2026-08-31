package top.boluofan.musictv.catalog.lxserver;

import java.io.InterruptedIOException;
import java.util.HashMap;
import java.util.Map;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;
import top.boluofan.musictv.catalog.LxApiService;
import top.boluofan.musictv.catalog.model.LoginResponse;

public final class LxServerConnectionActions {
    interface ServiceFactory {
        LxApiService create(String url);
    }

    private final ServiceFactory serviceFactory;

    public LxServerConnectionActions(android.content.Context context) {
        this(url -> LxRetrofitClient.createApiService(context, url));
    }

    LxServerConnectionActions(ServiceFactory serviceFactory) {
        this.serviceFactory = serviceFactory;
    }

    public Operation connect(String url, String username, String password, String token,
                             Callback callback) {
        ActiveOperation operation = new ActiveOperation();
        LxApiService service;
        try {
            service = serviceFactory.create(url);
        } catch (RuntimeException error) {
            fail(operation, callback, error.getMessage());
            return operation;
        }

        if (username.isEmpty() || password.isEmpty()) {
            Call<ResponseBody> call = service.getSongListTags("wy");
            operation.setCall(call);
            call.enqueue(new retrofit2.Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> request, Response<ResponseBody> response) {
                    if (!response.isSuccessful()) {
                        fail(operation, callback, "服务器返回 HTTP " + response.code());
                        return;
                    }
                    succeed(operation, callback, token, "服务器连接成功，将使用公共功能");
                }

                @Override
                public void onFailure(Call<ResponseBody> request, Throwable error) {
                    fail(operation, callback, failureMessage(error));
                }
            });
            return operation;
        }

        Map<String, String> body = new HashMap<>();
        body.put("username", username);
        body.put("password", password);
        Call<LoginResponse> call = service.loginUser(body);
        operation.setCall(call);
        call.enqueue(new retrofit2.Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> request, Response<LoginResponse> response) {
                if (!response.isSuccessful()) {
                    fail(operation, callback, "服务器返回 HTTP " + response.code());
                    return;
                }
                LoginResponse result = response.body();
                if (result == null || !result.isSuccess()) {
                    String message = result == null ? null : result.getMessage();
                    fail(operation, callback, message == null || message.trim().isEmpty()
                            ? "用户名或密码错误" : message);
                    return;
                }
                succeed(operation, callback, result.getToken(), "登录成功");
            }

            @Override
            public void onFailure(Call<LoginResponse> request, Throwable error) {
                fail(operation, callback, failureMessage(error));
            }
        });
        return operation;
    }

    static String failureMessage(Throwable error) {
        if (error instanceof InterruptedIOException) return "连接超时，请检查地址和网络";
        String message = error == null ? null : error.getMessage();
        return message == null || message.trim().isEmpty() ? "无法访问服务器" : message;
    }

    private static void succeed(ActiveOperation operation, Callback callback,
                                String token, String message) {
        if (operation.complete()) callback.onSuccess(token, message);
    }

    private static void fail(ActiveOperation operation, Callback callback, String message) {
        if (operation.complete()) callback.onFailure(message);
    }

    public interface Callback {
        void onSuccess(String token, String message);

        void onFailure(String message);
    }

    public interface Operation {
        void cancel();
    }

    private static final class ActiveOperation implements Operation {
        private Call<?> call;
        private boolean canceled;
        private boolean complete;

        synchronized void setCall(Call<?> call) {
            if (canceled || complete) {
                call.cancel();
                return;
            }
            this.call = call;
        }

        synchronized boolean complete() {
            if (canceled || complete) return false;
            complete = true;
            call = null;
            return true;
        }

        @Override
        public synchronized void cancel() {
            if (canceled || complete) return;
            canceled = true;
            if (call != null) call.cancel();
            call = null;
        }
    }
}
