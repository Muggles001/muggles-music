package top.boluofan.musictv.backend;

import android.os.Handler;
import android.os.Looper;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.Request;
import okio.Timeout;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

final class ValueCall<T> implements Call<T> {
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final Callable<T> task;
    private volatile boolean executed;
    private volatile boolean canceled;

    ValueCall(Callable<T> task) {
        this.task = task;
    }

    @Override
    public Response<T> execute() throws IOException {
        markExecuted();
        if (canceled) throw new IOException("Canceled");
        try {
            return Response.success(task.call());
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    @Override
    public void enqueue(Callback<T> callback) {
        markExecuted();
        EXECUTOR.execute(() -> {
            try {
                T value = task.call();
                if (!canceled) MAIN.post(() -> callback.onResponse(this, Response.success(value)));
            } catch (Throwable error) {
                if (!canceled) MAIN.post(() -> callback.onFailure(this, error));
            }
        });
    }

    private synchronized void markExecuted() {
        if (executed) throw new IllegalStateException("Already executed");
        executed = true;
    }

    @Override public boolean isExecuted() { return executed; }
    @Override public void cancel() { canceled = true; }
    @Override public boolean isCanceled() { return canceled; }
    @Override public Call<T> clone() { return new ValueCall<>(task); }
    @Override public Request request() { return new Request.Builder().url("http://127.0.0.1/").build(); }
    @Override public Timeout timeout() { return Timeout.NONE; }
}
