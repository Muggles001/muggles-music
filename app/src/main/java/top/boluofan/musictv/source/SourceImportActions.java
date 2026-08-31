package top.boluofan.musictv.source;

import android.content.Context;
import com.google.gson.JsonObject;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SourceImportActions implements AutoCloseable {
    interface Downloader {
        ImportedSource download(String url) throws Exception;
    }

    interface Runtime {
        void activate(ImportedSource source, SourceRuntimeEngine.LoadCallback callback);

        ImportedSource active();

        ImportedSource rollback();
    }

    interface Store {
        boolean hasPrevious();
    }

    private final Executor executor;
    private final ExecutorService ownedExecutor;
    private final Downloader downloader;
    private final Runtime runtime;
    private final Store store;
    private ActiveOperation activeOperation;
    private boolean closed;

    public SourceImportActions(Context context) {
        Context appContext = context.getApplicationContext();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        SourceRuntimeManager manager = SourceRuntimeManager.get(appContext);
        this.executor = executor;
        ownedExecutor = executor;
        downloader = new SourceScriptImporter()::download;
        runtime = new Runtime() {
            @Override
            public void activate(ImportedSource source,
                                 SourceRuntimeEngine.LoadCallback callback) {
                manager.activate(source, callback);
            }

            @Override
            public ImportedSource active() {
                return manager.getActiveSource();
            }

            @Override
            public ImportedSource rollback() {
                return manager.rollbackActive();
            }
        };
        SourceScriptStore sourceStore = new SourceScriptStore(appContext);
        store = sourceStore::hasPrevious;
    }

    SourceImportActions(Executor executor, Downloader downloader, Runtime runtime, Store store) {
        this.executor = executor;
        ownedExecutor = null;
        this.downloader = downloader;
        this.runtime = runtime;
        this.store = store;
    }

    public synchronized Operation importSource(String url, Callback callback) {
        cancelActive();
        if (closed) return ActiveOperation.canceled();
        ActiveOperation operation = new ActiveOperation();
        activeOperation = operation;
        executor.execute(() -> {
            if (!operation.start()) return;
            try {
                ImportedSource source = downloader.download(url);
                operation.stopRunning();
                if (operation.isCanceled()) return;
                runtime.activate(source, new SourceRuntimeEngine.LoadCallback() {
                    @Override
                    public void onLoaded(JsonObject capabilities) {
                        if (complete(operation)) callback.onSuccess(source, capabilities);
                    }

                    @Override
                    public void onError(String error) {
                        if (complete(operation)) callback.onFailure(error);
                    }
                });
            } catch (Exception error) {
                operation.stopRunning();
                if (complete(operation)) callback.onFailure(error.getMessage());
            }
        });
        return operation;
    }

    public ImportedSource getActiveSource() {
        return runtime.active();
    }

    public ImportedSource rollback() {
        return runtime.rollback();
    }

    public boolean hasPrevious() {
        return store.hasPrevious();
    }

    private synchronized boolean complete(ActiveOperation operation) {
        if (!operation.complete()) return false;
        if (activeOperation == operation) activeOperation = null;
        return true;
    }

    private synchronized void cancelActive() {
        if (activeOperation == null) return;
        activeOperation.cancel();
        activeOperation = null;
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        cancelActive();
        if (ownedExecutor != null) ownedExecutor.shutdownNow();
    }

    public interface Callback {
        void onSuccess(ImportedSource source, JsonObject capabilities);

        void onFailure(String error);
    }

    public interface Operation {
        void cancel();
    }

    private static final class ActiveOperation implements Operation {
        private boolean canceled;
        private boolean complete;
        private Thread runningThread;

        static ActiveOperation canceled() {
            ActiveOperation operation = new ActiveOperation();
            operation.canceled = true;
            return operation;
        }

        synchronized boolean start() {
            if (canceled || complete) return false;
            runningThread = Thread.currentThread();
            return true;
        }

        synchronized void stopRunning() {
            runningThread = null;
        }

        synchronized boolean complete() {
            if (canceled || complete) return false;
            complete = true;
            runningThread = null;
            return true;
        }

        synchronized boolean isCanceled() {
            return canceled;
        }

        @Override
        public synchronized void cancel() {
            if (canceled || complete) return;
            canceled = true;
            if (runningThread != null) runningThread.interrupt();
            runningThread = null;
        }
    }
}
