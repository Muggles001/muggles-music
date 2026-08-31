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
import top.boluofan.musictv.catalog.model.RankingBoard;

public final class RankingCatalogActions {
    private final LxApiService apiService;
    private final Gson gson = new Gson();

    public RankingCatalogActions(LxApiService apiService) {
        this.apiService = apiService;
    }

    public Operation loadBoards(String source, Callback<List<RankingBoard>> callback) {
        return enqueue(apiService.getLeaderboardBoards(source), callback,
                json -> parseBoards(json));
    }

    public Operation loadSongs(String source, String boardId,
                               Callback<List<MusicInfo>> callback) {
        return enqueue(apiService.getLeaderboardList(source, boardId, 1), callback,
                json -> parseSongs(json, source));
    }

    private List<RankingBoard> parseBoards(String json) {
        JsonArray values = list(json);
        List<RankingBoard> boards = new ArrayList<>(values.size());
        for (JsonElement value : values) {
            JsonObject item = value.getAsJsonObject();
            boards.add(new RankingBoard(
                    item.get("id").getAsString(), item.get("name").getAsString()));
        }
        return boards;
    }

    private List<MusicInfo> parseSongs(String json, String source) {
        JsonArray values = list(json);
        List<MusicInfo> songs = new ArrayList<>(values.size());
        for (JsonElement value : values) {
            MusicInfo song = gson.fromJson(value, MusicInfo.class);
            if (song.getSource() == null || song.getSource().isEmpty()) {
                song.setSource(source);
            }
            songs.add(song);
        }
        return songs;
    }

    private JsonArray list(String json) {
        JsonObject root = gson.fromJson(json, JsonObject.class);
        JsonArray values = root == null ? null : root.getAsJsonArray("list");
        return values == null ? new JsonArray() : values;
    }

    private <T> Operation enqueue(Call<ResponseBody> call, Callback<T> callback,
                                  Parser<T> parser) {
        ActiveOperation operation = new ActiveOperation();
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
                    succeed(operation, callback, parser.parse(body.string()));
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

    private static <T> void succeed(ActiveOperation operation, Callback<T> callback, T value) {
        if (operation.complete()) callback.onSuccess(value);
    }

    private static void fail(ActiveOperation operation, Callback<?> callback,
                             Failure.Kind kind, Throwable cause) {
        if (operation.complete()) callback.onFailure(new Failure(kind, cause));
    }

    private interface Parser<T> {
        T parse(String json);
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
