package top.boluofan.musictv.catalog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.ResponseBody;
import okio.Timeout;
import org.junit.Test;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PlaylistCatalogActionsTest {
    @Test
    public void parsesPageAndUsesServerPaginationMetadata() {
        FakeService service = new FakeService(FakeCall.success(body(
                "{\"list\":[{\"id\":\"one\",\"name\":\"First\"}],"
                        + "\"limit\":20,\"total\":41}")));
        Recorder recorder = new Recorder();

        new PlaylistCatalogActions(service.api).loadPage("kw", 2, recorder);

        assertEquals("kw", service.source);
        assertEquals("", service.tagId);
        assertEquals("hot", service.sortId);
        assertEquals(2, service.page);
        assertEquals("one", recorder.page.getPlaylists().get(0).getId());
        assertTrue(recorder.page.hasMore(2));
        assertFalse(recorder.page.hasMore(3));
    }

    @Test
    public void missingPaginationMetadataKeepsTwentyItemFallback() {
        StringBuilder json = new StringBuilder("{\"list\":[");
        for (int i = 0; i < 20; i++) {
            if (i > 0) json.append(',');
            json.append("{\"id\":\"").append(i).append("\"}");
        }
        json.append("]}");
        Recorder recorder = new Recorder();

        new PlaylistCatalogActions(
                new FakeService(FakeCall.success(body(json.toString()))).api)
                .loadPage("tx", 1, recorder);

        assertTrue(recorder.page.hasMore(1));
    }

    @Test
    public void cancellationCancelsCallAndSuppressesLatePage() {
        FakeCall<ResponseBody> call = FakeCall.manual();
        FakeService service = new FakeService(call);
        Recorder recorder = new Recorder();

        PlaylistCatalogActions.Operation operation =
                new PlaylistCatalogActions(service.api).loadPage("tx", 1, recorder);
        operation.cancel();
        call.dispatchResponse(Response.success(body("{\"list\":[]}")));

        assertTrue(call.isCanceled());
        assertNull(recorder.page);
        assertNull(recorder.failure);
    }

    private static ResponseBody body(String json) {
        return ResponseBody.create(MediaType.parse("application/json"), json);
    }

    private static final class Recorder implements PlaylistCatalogActions.Callback {
        PlaylistCatalogActions.Page page;
        PlaylistCatalogActions.Failure failure;

        @Override public void onSuccess(PlaylistCatalogActions.Page page) { this.page = page; }
        @Override public void onFailure(PlaylistCatalogActions.Failure failure) {
            this.failure = failure;
        }
    }

    private static final class FakeService {
        final LxApiService api;
        String source;
        String tagId;
        String sortId;
        int page;

        FakeService(FakeCall<ResponseBody> call) {
            api = (LxApiService) Proxy.newProxyInstance(
                    LxApiService.class.getClassLoader(), new Class<?>[]{LxApiService.class},
                    (proxy, method, args) -> {
                        if (!"getSongListList".equals(method.getName())) {
                            throw new UnsupportedOperationException(method.getName());
                        }
                        source = (String) args[0];
                        tagId = (String) args[1];
                        sortId = (String) args[2];
                        page = (Integer) args[3];
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
