package top.boluofan.musictv.catalog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
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

public class SearchCatalogActionsTest {
    @Test
    public void hotSearchKeepsStringsInOrderAndLimitsToTen() {
        FakeService service = new FakeService();
        service.hot = FakeCall.success(body(
                "{\"list\":[\"1\",null,2,\"2\",\"3\",\"4\",\"5\","
                        + "\"6\",\"7\",\"8\",\"9\",\"10\",\"11\"]}"));
        Recorder<List<String>> recorder = new Recorder<>();

        new SearchCatalogActions(service.api).loadHotSearch("kw", recorder);

        assertEquals(Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"),
                recorder.value);
        assertEquals("kw", service.source);
    }

    @Test
    public void typedSearchPreservesParametersAndResponseList() {
        FakeService service = new FakeService();
        List<MusicInfo> songs = new ArrayList<>();
        songs.add(new MusicInfo());
        service.search = FakeCall.success(songs);
        Recorder<List<MusicInfo>> recorder = new Recorder<>();

        new SearchCatalogActions(service.api).search("key", "tx", 2, 30, recorder);

        assertEquals("key", service.keyword);
        assertEquals("tx", service.source);
        assertEquals(2, service.page);
        assertEquals(30, service.limit);
        assertEquals(songs, recorder.value);
    }

    @Test
    public void cancellationCancelsSearchAndSuppressesLateResult() {
        FakeService service = new FakeService();
        FakeCall<List<MusicInfo>> call = FakeCall.manual();
        service.search = call;
        Recorder<List<MusicInfo>> recorder = new Recorder<>();

        SearchCatalogActions.Operation operation =
                new SearchCatalogActions(service.api).search("key", "tx", 1, 30, recorder);
        operation.cancel();
        call.dispatchResponse(Response.success(new ArrayList<>()));

        assertTrue(call.isCanceled());
        assertNull(recorder.value);
        assertNull(recorder.failure);
    }

    private static ResponseBody body(String json) {
        return ResponseBody.create(MediaType.parse("application/json"), json);
    }

    private static final class Recorder<T> implements SearchCatalogActions.Callback<T> {
        T value;
        SearchCatalogActions.Failure failure;

        @Override public void onSuccess(T value) { this.value = value; }
        @Override public void onFailure(SearchCatalogActions.Failure failure) {
            this.failure = failure;
        }
    }

    private static final class FakeService {
        final LxApiService api;
        FakeCall<ResponseBody> hot;
        FakeCall<List<MusicInfo>> search;
        String keyword;
        String source;
        int page;
        int limit;

        FakeService() {
            api = (LxApiService) Proxy.newProxyInstance(
                    LxApiService.class.getClassLoader(), new Class<?>[]{LxApiService.class},
                    (proxy, method, args) -> {
                        if ("getHotSearch".equals(method.getName())) {
                            source = (String) args[0];
                            return hot;
                        }
                        if ("searchMusic".equals(method.getName())) {
                            keyword = (String) args[0];
                            source = (String) args[1];
                            page = (Integer) args[2];
                            limit = (Integer) args[3];
                            return search;
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
            return new FakeCall<>(Response.success(value), false);
        }

        static <T> FakeCall<T> manual() {
            return new FakeCall<>(null, true);
        }

        @Override public Response<T> execute() { throw new UnsupportedOperationException(); }
        @Override public void enqueue(Callback<T> callback) {
            this.callback = callback;
            if (!manual) callback.onResponse(this, response);
        }
        void dispatchResponse(Response<T> response) { callback.onResponse(this, response); }
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
