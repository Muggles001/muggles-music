package top.boluofan.musictv.catalog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.reflect.Proxy;
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

public class SearchSuggestionActionsTest {
    @Test
    public void readsRootArrayInOrderAndLimitsToEightStrings() {
        FakeService service = new FakeService(FakeCall.success(body(
                "[\"one\",null,2,\"two\",\"three\",\"four\",\"five\","
                        + "\"six\",\"seven\",\"eight\",\"nine\"]")));
        Recorder recorder = new Recorder();

        new SearchSuggestionActions(service.api).load("key", "kw", recorder);

        assertEquals(Arrays.asList("one", "two", "three", "four", "five", "six", "seven", "eight"),
                recorder.suggestions);
        assertNull(recorder.failure);
        assertEquals("key", service.keyword);
        assertEquals("kw", service.source);
    }

    @Test
    public void readsObjectListArray() {
        FakeService service = new FakeService(FakeCall.success(body(
                "{\"list\":[\"first\",\"second\"]}")));
        Recorder recorder = new Recorder();

        new SearchSuggestionActions(service.api).load("key", "tx", recorder);

        assertEquals(Arrays.asList("first", "second"), recorder.suggestions);
        assertNull(recorder.failure);
    }

    @Test
    public void malformedPayloadIsParseFailure() {
        FakeService service = new FakeService(FakeCall.success(body("{\"items\":[]}")));
        Recorder recorder = new Recorder();

        new SearchSuggestionActions(service.api).load("key", "wy", recorder);

        assertFailure(SearchSuggestionActions.Failure.Kind.PARSE, recorder);
    }

    @Test
    public void unsuccessfulAndEmptyResponsesAreDistinctFailures() {
        Recorder unsuccessful = new Recorder();
        Recorder empty = new Recorder();

        new SearchSuggestionActions(new FakeService(FakeCall.response(
                Response.error(503, body("{}")))).api).load("key", "kw", unsuccessful);
        new SearchSuggestionActions(new FakeService(FakeCall.success(null)).api)
                .load("key", "kw", empty);

        assertFailure(SearchSuggestionActions.Failure.Kind.RESPONSE, unsuccessful);
        assertFailure(SearchSuggestionActions.Failure.Kind.BODY, empty);
    }

    @Test
    public void transportFailurePreservesCause() {
        IOException cause = new IOException("offline");
        FakeService service = new FakeService(FakeCall.failure(cause));
        Recorder recorder = new Recorder();

        new SearchSuggestionActions(service.api).load("key", "kw", recorder);

        assertFailure(SearchSuggestionActions.Failure.Kind.TRANSPORT, recorder);
        assertSame(cause, recorder.failure.getCause());
    }

    @Test
    public void cancellationCancelsCallAndSuppressesLateCallbacks() {
        FakeCall<ResponseBody> call = FakeCall.manual();
        FakeService service = new FakeService(call);
        Recorder recorder = new Recorder();
        SearchSuggestionActions.Operation operation =
                new SearchSuggestionActions(service.api).load("key", "kw", recorder);

        operation.cancel();
        call.dispatchResponse(Response.success(body("[\"late\"]")));
        call.dispatchFailure(new IOException("late failure"));

        assertTrue(operation.isCanceled());
        assertTrue(call.isCanceled());
        assertNull(recorder.suggestions);
        assertNull(recorder.failure);
    }

    private static void assertFailure(SearchSuggestionActions.Failure.Kind kind,
                                      Recorder recorder) {
        assertNull(recorder.suggestions);
        assertEquals(kind, recorder.failure.getKind());
    }

    private static ResponseBody body(String json) {
        return ResponseBody.create(MediaType.parse("application/json"), json);
    }

    private static final class Recorder implements SearchSuggestionActions.Callback {
        List<String> suggestions;
        SearchSuggestionActions.Failure failure;

        @Override
        public void onSuccess(List<String> suggestions) {
            this.suggestions = suggestions;
        }

        @Override
        public void onFailure(SearchSuggestionActions.Failure failure) {
            this.failure = failure;
        }
    }

    private static final class FakeService {
        final LxApiService api;
        String keyword;
        String source;

        FakeService(FakeCall<ResponseBody> call) {
            api = (LxApiService) Proxy.newProxyInstance(
                    LxApiService.class.getClassLoader(), new Class<?>[]{LxApiService.class},
                    (proxy, method, args) -> {
                        if (!"tipSearch".equals(method.getName())) {
                            throw new UnsupportedOperationException(method.getName());
                        }
                        keyword = (String) args[0];
                        source = (String) args[1];
                        return call;
                    });
        }
    }

    private static final class FakeCall<T> implements Call<T> {
        private final Response<T> response;
        private final Throwable error;
        private final boolean manual;
        private Callback<T> callback;
        private boolean executed;
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

        @Override
        public Response<T> execute() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void enqueue(Callback<T> callback) {
            executed = true;
            this.callback = callback;
            if (manual) return;
            if (error != null) callback.onFailure(this, error);
            else callback.onResponse(this, response);
        }

        void dispatchResponse(Response<T> response) {
            callback.onResponse(this, response);
        }

        void dispatchFailure(Throwable error) {
            callback.onFailure(this, error);
        }

        @Override
        public boolean isExecuted() {
            return executed;
        }

        @Override
        public void cancel() {
            canceled = true;
        }

        @Override
        public boolean isCanceled() {
            return canceled;
        }

        @Override
        public Call<T> clone() {
            return new FakeCall<>(response, error, manual);
        }

        @Override
        public Request request() {
            return new Request.Builder().url("http://127.0.0.1/").build();
        }

        @Override
        public Timeout timeout() {
            return Timeout.NONE;
        }
    }
}
