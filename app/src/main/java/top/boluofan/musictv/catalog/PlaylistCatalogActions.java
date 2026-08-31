package top.boluofan.musictv.catalog;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.List;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;
import top.boluofan.musictv.catalog.model.Playlist;

public final class PlaylistCatalogActions {
    private final LxApiService apiService;

    public PlaylistCatalogActions(LxApiService apiService) {
        this.apiService = apiService;
    }

    public Operation loadPage(String source, int page, Callback callback) {
        ActiveOperation operation = new ActiveOperation();
        Call<ResponseBody> call = apiService.getSongListList(source, "", "hot", page);
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
                    WirePage wire = new Gson().fromJson(body.string(), WirePage.class);
                    if (wire == null || wire.list == null) {
                        fail(operation, callback, Failure.Kind.PARSE, null);
                        return;
                    }
                    if (operation.complete()) {
                        callback.onSuccess(new Page(wire.list, wire.limit, wire.total));
                    }
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

    private static void fail(ActiveOperation operation, Callback callback,
                             Failure.Kind kind, Throwable cause) {
        if (operation.complete()) callback.onFailure(new Failure(kind, cause));
    }

    public interface Callback {
        void onSuccess(Page page);

        void onFailure(Failure failure);
    }

    public interface Operation {
        void cancel();
    }

    public static final class Page {
        private final List<Playlist> playlists;
        private final int limit;
        private final int total;

        Page(List<Playlist> playlists, int limit, int total) {
            this.playlists = Collections.unmodifiableList(playlists);
            this.limit = limit;
            this.total = total;
        }

        public List<Playlist> getPlaylists() {
            return playlists;
        }

        public boolean hasMore(int page) {
            if (limit > 0 && total > 0) return page * limit < total;
            if (limit > 0) return playlists.size() >= limit;
            return playlists.size() >= 20;
        }
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

    private static final class WirePage {
        List<Playlist> list;
        int limit;
        int total;
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
