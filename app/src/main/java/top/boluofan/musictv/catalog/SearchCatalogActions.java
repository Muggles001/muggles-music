package top.boluofan.musictv.catalog;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;
import top.boluofan.musictv.catalog.model.MusicInfo;

public final class SearchCatalogActions {
    private static final int MAX_HOT_SEARCHES = 10;
    private final LxApiService apiService;

    public SearchCatalogActions(LxApiService apiService) {
        this.apiService = apiService;
    }

    public Operation loadHotSearch(String source, Callback<List<String>> callback) {
        ActiveOperation operation = new ActiveOperation();
        Call<ResponseBody> call = apiService.getHotSearch(source);
        operation.setCall(call);
        call.enqueue(new retrofit2.Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> request, Response<ResponseBody> response) {
                if (!response.isSuccessful()) {
                    fail(operation, callback, Failure.Kind.RESPONSE, null);
                    return;
                }
                ResponseBody body = response.body();
                if (body == null) {
                    fail(operation, callback, Failure.Kind.BODY, null);
                    return;
                }
                try {
                    succeed(operation, callback, parseHotSearch(body.string()));
                } catch (Exception error) {
                    fail(operation, callback, Failure.Kind.PARSE, error);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> request, Throwable error) {
                fail(operation, callback, Failure.Kind.TRANSPORT, error);
            }
        });
        return operation;
    }

    public Operation search(String keyword, String source, int page, int limit,
                            Callback<List<MusicInfo>> callback) {
        ActiveOperation operation = new ActiveOperation();
        Call<List<MusicInfo>> call = apiService.searchMusic(keyword, source, page, limit);
        operation.setCall(call);
        call.enqueue(new retrofit2.Callback<List<MusicInfo>>() {
            @Override
            public void onResponse(Call<List<MusicInfo>> request,
                                   Response<List<MusicInfo>> response) {
                if (!response.isSuccessful()) {
                    fail(operation, callback, Failure.Kind.RESPONSE, null);
                } else if (response.body() == null) {
                    fail(operation, callback, Failure.Kind.BODY, null);
                } else {
                    succeed(operation, callback, response.body());
                }
            }

            @Override
            public void onFailure(Call<List<MusicInfo>> request, Throwable error) {
                fail(operation, callback, Failure.Kind.TRANSPORT, error);
            }
        });
        return operation;
    }

    private static List<String> parseHotSearch(String json) {
        JsonObject root = new Gson().fromJson(json, JsonObject.class);
        JsonArray values = root == null ? null : root.getAsJsonArray("list");
        List<String> words = new ArrayList<>();
        if (values == null) return words;
        for (JsonElement value : values) {
            if (words.size() == MAX_HOT_SEARCHES) break;
            if (value != null && value.isJsonPrimitive()
                    && value.getAsJsonPrimitive().isString()) {
                words.add(value.getAsString());
            }
        }
        return words;
    }

    private static <T> void succeed(ActiveOperation operation, Callback<T> callback, T value) {
        if (operation.complete()) callback.onSuccess(value);
    }

    private static void fail(ActiveOperation operation, Callback<?> callback,
                             Failure.Kind kind, Throwable cause) {
        if (operation.complete()) callback.onFailure(new Failure(kind, cause));
    }

    public interface Callback<T> {
        void onSuccess(T value);

        void onFailure(Failure failure);
    }

    public interface Operation {
        void cancel();
    }

    public static final class Failure {
        public enum Kind {
            RESPONSE,
            BODY,
            PARSE,
            TRANSPORT
        }

        private final Kind kind;
        private final Throwable cause;

        Failure(Kind kind, Throwable cause) {
            this.kind = kind;
            this.cause = cause;
        }

        public Kind getKind() {
            return kind;
        }

        public Throwable getCause() {
            return cause;
        }
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
