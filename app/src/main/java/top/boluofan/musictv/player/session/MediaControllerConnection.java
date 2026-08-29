package top.boluofan.musictv.player.session;

import android.content.ComponentName;
import android.content.Context;
import androidx.core.content.ContextCompat;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import top.boluofan.musictv.MusicService;

public final class MediaControllerConnection {
    public interface HostState {
        boolean isActive();
    }

    public interface ConnectedListener {
        void onConnected(MediaController controller);
    }

    public interface FailureListener {
        void onConnectionFailed(Throwable error);
    }

    private final AsyncConnection<MediaController> connection;

    public MediaControllerConnection(
            Context context,
            HostState hostState,
            ConnectedListener connectedListener,
            FailureListener failureListener) {
        Context applicationContext = context.getApplicationContext();
        connection = new AsyncConnection<>(
                () -> {
                    SessionToken token = new SessionToken(applicationContext,
                            new ComponentName(applicationContext, MusicService.class));
                    return new MediaController.Builder(applicationContext, token).buildAsync();
                },
                MediaController::releaseFuture,
                ContextCompat.getMainExecutor(applicationContext),
                hostState::isActive,
                connectedListener::onConnected,
                failureListener::onConnectionFailed);
    }

    public void connect() {
        connection.connect();
    }

    public void disconnect() {
        connection.disconnect();
    }
}

final class AsyncConnection<T> {
    interface FutureFactory<T> {
        ListenableFuture<T> create();
    }

    interface FutureReleaser<T> {
        void release(ListenableFuture<T> future);
    }

    interface HostState {
        boolean isActive();
    }

    interface ConnectedListener<T> {
        void onConnected(T value);
    }

    interface FailureListener {
        void onConnectionFailed(Throwable error);
    }

    private final FutureFactory<T> futureFactory;
    private final FutureReleaser<T> futureReleaser;
    private final Executor callbackExecutor;
    private final HostState hostState;
    private final ConnectedListener<T> connectedListener;
    private final FailureListener failureListener;
    private ListenableFuture<T> currentFuture;

    AsyncConnection(
            FutureFactory<T> futureFactory,
            FutureReleaser<T> futureReleaser,
            Executor callbackExecutor,
            HostState hostState,
            ConnectedListener<T> connectedListener,
            FailureListener failureListener) {
        this.futureFactory = futureFactory;
        this.futureReleaser = futureReleaser;
        this.callbackExecutor = callbackExecutor;
        this.hostState = hostState;
        this.connectedListener = connectedListener;
        this.failureListener = failureListener;
    }

    void connect() {
        disconnect();

        ListenableFuture<T> pending;
        try {
            pending = futureFactory.create();
        } catch (Throwable error) {
            notifyFailureIfActive(error);
            return;
        }

        synchronized (this) {
            currentFuture = pending;
        }
        pending.addListener(() -> complete(pending), callbackExecutor);
    }

    void disconnect() {
        ListenableFuture<T> pending;
        synchronized (this) {
            pending = currentFuture;
            currentFuture = null;
        }
        if (pending != null) {
            futureReleaser.release(pending);
        }
    }

    private void complete(ListenableFuture<T> pending) {
        T value;
        try {
            value = pending.get();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            completeFailure(pending, error);
            return;
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            completeFailure(pending, cause != null ? cause : error);
            return;
        } catch (Throwable error) {
            completeFailure(pending, error);
            return;
        }

        boolean release;
        synchronized (this) {
            if (currentFuture != pending) return;
            release = !hostState.isActive();
            if (release) currentFuture = null;
        }
        if (release) {
            futureReleaser.release(pending);
            return;
        }
        connectedListener.onConnected(value);
    }

    private void completeFailure(ListenableFuture<T> pending, Throwable error) {
        synchronized (this) {
            if (currentFuture != pending) return;
            currentFuture = null;
        }
        futureReleaser.release(pending);
        notifyFailureIfActive(error);
    }

    private void notifyFailureIfActive(Throwable error) {
        if (hostState.isActive()) {
            failureListener.onConnectionFailed(error);
        }
    }
}
