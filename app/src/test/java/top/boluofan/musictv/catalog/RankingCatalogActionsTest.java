package top.boluofan.musictv.catalog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.ResponseBody;
import okio.Timeout;
import org.junit.Test;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import top.boluofan.musictv.catalog.model.MusicInfo;
import top.boluofan.musictv.catalog.model.RankingBoard;

public class RankingCatalogActionsTest {
    @Test
    public void parsesBoardsInResponseOrder() {
        FakeService service = new FakeService();
        service.boards = FakeCall.success(body(
                "{\"list\":[{\"id\":\"tx__1\",\"name\":\"热歌榜\"},"
                        + "{\"id\":\"tx__2\",\"name\":\"新歌榜\"}]}"));
        Recorder<List<RankingBoard>> recorder = new Recorder<>();

        new RankingCatalogActions(service.api).loadBoards("tx", recorder);

        assertEquals("tx", service.source);
        assertEquals(2, recorder.value.size());
        assertEquals("tx__1", recorder.value.get(0).getId());
        assertEquals("新歌榜", recorder.value.get(1).getName());
    }

    @Test
    public void parsesSongsAndFillsOnlyMissingSource() {
        FakeService service = new FakeService();
        service.songs = FakeCall.success(body(
                "{\"list\":[{\"songmid\":\"1\",\"name\":\"first\"},"
                        + "{\"songmid\":\"2\",\"name\":\"second\",\"source\":\"kw\"}]}"));
        Recorder<List<MusicInfo>> recorder = new Recorder<>();

        new RankingCatalogActions(service.api).loadSongs("tx", "board", recorder);

        assertEquals("board", service.boardId);
        assertEquals(1, service.page);
        assertEquals("tx", recorder.value.get(0).getSource());
        assertEquals("kw", recorder.value.get(1).getSource());
    }

    @Test
    public void classifiesResponseParseAndTransportFailures() {
        FakeService responseService = new FakeService();
        responseService.boards = FakeCall.response(Response.error(503, body("{}")));
        Recorder<List<RankingBoard>> response = new Recorder<>();
        new RankingCatalogActions(responseService.api).loadBoards("tx", response);

        FakeService parseService = new FakeService();
        parseService.boards = FakeCall.success(body("{broken"));
        Recorder<List<RankingBoard>> parse = new Recorder<>();
        new RankingCatalogActions(parseService.api).loadBoards("tx", parse);

        FakeService transportService = new FakeService();
        transportService.boards = FakeCall.failure(new IOException("offline"));
        Recorder<List<RankingBoard>> transport = new Recorder<>();
        new RankingCatalogActions(transportService.api).loadBoards("tx", transport);

        assertEquals(RankingCatalogActions.Failure.Kind.RESPONSE, response.failure.getKind());
        assertEquals(RankingCatalogActions.Failure.Kind.PARSE, parse.failure.getKind());
        assertEquals(RankingCatalogActions.Failure.Kind.TRANSPORT, transport.failure.getKind());
    }

    @Test
    public void cancellationCancelsCallAndSuppressesLateCallback() {
        FakeService service = new FakeService();
        FakeCall<ResponseBody> call = FakeCall.manual();
        service.boards = call;
        Recorder<List<RankingBoard>> recorder = new Recorder<>();

        RankingCatalogActions.Operation operation =
                new RankingCatalogActions(service.api).loadBoards("tx", recorder);
        operation.cancel();
        call.dispatchResponse(Response.success(body("{\"list\":[]}")));

        assertTrue(call.isCanceled());
        assertNull(recorder.value);
        assertNull(recorder.failure);
    }

    private static ResponseBody body(String json) {
        return ResponseBody.create(MediaType.parse("application/json"), json);
    }

    private static final class Recorder<T> implements RankingCatalogActions.Callback<T> {
        T value;
        RankingCatalogActions.Failure failure;

        @Override public void onSuccess(T value) { this.value = value; }
        @Override public void onFailure(RankingCatalogActions.Failure failure) {
            this.failure = failure;
        }
    }

    private static final class FakeService {
        final LxApiService api;
        FakeCall<ResponseBody> boards;
        FakeCall<ResponseBody> songs;
        String source;
        String boardId;
        int page;

        FakeService() {
            api = (LxApiService) Proxy.newProxyInstance(
                    LxApiService.class.getClassLoader(), new Class<?>[]{LxApiService.class},
                    (proxy, method, args) -> {
                        if ("getLeaderboardBoards".equals(method.getName())) {
                            source = (String) args[0];
                            return boards;
                        }
                        if ("getLeaderboardList".equals(method.getName())) {
                            source = (String) args[0];
                            boardId = (String) args[1];
                            page = (Integer) args[2];
                            return songs;
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
        }
    }

    private static final class FakeCall<T> implements Call<T> {
        private final Response<T> response;
        private final Throwable error;
        private final boolean manual;
        private Callback<T> callback;
        private boolean canceled;

        private FakeCall(Response<T> response, Throwable error, boolean manual) {
            this.response = response;
            this.error = error;
            this.manual = manual;
        }

        static <T> FakeCall<T> success(T value) {
            return response(Response.success(value));
        }

        static <T> FakeCall<T> response(Response<T> response) {
            return new FakeCall<>(response, null, false);
        }

        static <T> FakeCall<T> failure(Throwable error) {
            return new FakeCall<>(null, error, false);
        }

        static <T> FakeCall<T> manual() {
            return new FakeCall<>(null, null, true);
        }

        @Override public Response<T> execute() { throw new UnsupportedOperationException(); }

        @Override
        public void enqueue(Callback<T> callback) {
            this.callback = callback;
            if (manual) return;
            if (error != null) callback.onFailure(this, error);
            else callback.onResponse(this, response);
        }

        void dispatchResponse(Response<T> response) { callback.onResponse(this, response); }
        @Override public boolean isExecuted() { return callback != null; }
        @Override public void cancel() { canceled = true; }
        @Override public boolean isCanceled() { return canceled; }
        @Override public Call<T> clone() { return new FakeCall<>(response, error, manual); }
        @Override public Request request() {
            return new Request.Builder().url("http://127.0.0.1/").build();
        }
        @Override public Timeout timeout() { return Timeout.NONE; }
    }
}
