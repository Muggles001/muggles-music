package top.boluofan.musictv.catalog;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

public final class SearchSuggestionActions {
    private static final int MAX_SUGGESTIONS = 8;

    private final LxApiService apiService;

    public SearchSuggestionActions(LxApiService apiService) {
        this.apiService = apiService;
    }

    public Operation load(String keyword, String source, Callback callback) {
        ActiveOperation operation = new ActiveOperation();
        Call<ResponseBody> call = apiService.tipSearch(keyword, source);
        operation.setCall(call);
        call.enqueue(new retrofit2.Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (operation.isCanceled()) return;
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
                    succeed(operation, callback, parse(body.string()));
                } catch (IOException error) {
                    fail(operation, callback, Failure.Kind.BODY, error);
                } catch (RuntimeException error) {
                    fail(operation, callback, Failure.Kind.PARSE, error);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable error) {
                fail(operation, callback, Failure.Kind.TRANSPORT, error);
            }
        });
        return operation;
    }

    private static List<String> parse(String json) {
        JsonElement root = new Gson().fromJson(json, JsonElement.class);
        JsonArray values;
        if (root != null && root.isJsonArray()) {
            values = root.getAsJsonArray();
        } else if (root != null && root.isJsonObject()) {
            JsonObject object = root.getAsJsonObject();
            JsonElement list = object.get("list");
            if (list == null || !list.isJsonArray()) {
                throw new IllegalArgumentException("Suggestion response has no list array");
            }
            values = list.getAsJsonArray();
        } else {
            throw new IllegalArgumentException("Suggestion response is not an array or object");
        }

        List<String> suggestions = new ArrayList<>(Math.min(values.size(), MAX_SUGGESTIONS));
        for (JsonElement value : values) {
            if (suggestions.size() == MAX_SUGGESTIONS) break;
            if (value != null && value.isJsonPrimitive()
                    && value.getAsJsonPrimitive().isString()) {
                suggestions.add(value.getAsString());
            }
        }
        return suggestions;
    }

    private static void succeed(ActiveOperation operation, Callback callback,
                                List<String> suggestions) {
        if (operation.complete()) callback.onSuccess(suggestions);
    }

    private static void fail(ActiveOperation operation, Callback callback,
                             Failure.Kind kind, Throwable cause) {
        if (operation.complete()) callback.onFailure(new Failure(kind, cause));
    }

    public interface Callback {
        void onSuccess(List<String> suggestions);
        void onFailure(Failure failure);
    }

    public interface Operation {
        void cancel();
        boolean isCanceled();
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

        @Override
        public synchronized boolean isCanceled() {
            return canceled;
        }
    }
}
