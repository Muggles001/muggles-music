package top.boluofan.musictv.source;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.File;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

final class RemoteSourceRuntimeClient implements AutoCloseable {
    private final Context context;
    private final HandlerThread replyThread = new HandlerThread("MugglesSourceReplies");
    private final Messenger incoming;
    private final ConcurrentHashMap<String, Pending> pending = new ConcurrentHashMap<>();
    private final CountDownLatch connected = new CountDownLatch(1);
    private volatile Messenger remote;
    private volatile boolean bound;

    RemoteSourceRuntimeClient(Context context) {
        this.context = context.getApplicationContext();
        replyThread.start();
        incoming = new Messenger(new Handler(replyThread.getLooper(), this::handleReply));
    }

    JsonObject load(File script, String importUrl) throws IOException {
        Bundle data = new Bundle();
        data.putString(SourceRuntimeProtocol.SCRIPT_PATH, script.getAbsolutePath());
        data.putString(SourceRuntimeProtocol.IMPORT_URL, importUrl);
        JsonElement result = request(SourceRuntimeProtocol.LOAD, data, 8, TimeUnit.SECONDS);
        if (result == null || !result.isJsonObject()) throw new IOException("音源能力信息无效");
        return result.getAsJsonObject();
    }

    JsonElement resolve(String source, String action, JsonObject info) throws IOException {
        Bundle data = new Bundle();
        data.putString(SourceRuntimeProtocol.SOURCE, source);
        data.putString(SourceRuntimeProtocol.ACTION, action);
        data.putString(SourceRuntimeProtocol.INFO, info.toString());
        return request(SourceRuntimeProtocol.RESOLVE, data, 24, TimeUnit.SECONDS);
    }

    private JsonElement request(int what, Bundle data, long timeout, TimeUnit unit) throws IOException {
        ensureBound();
        String requestId = UUID.randomUUID().toString();
        data.putString(SourceRuntimeProtocol.REQUEST_ID, requestId);
        Pending target = new Pending();
        pending.put(requestId, target);
        Message message = Message.obtain(null, what);
        message.setData(data);
        message.replyTo = incoming;
        try {
            remote.send(message);
        } catch (RemoteException error) {
            pending.remove(requestId);
            throw new IOException("无法连接音源运行进程", error);
        }
        try {
            if (!target.latch.await(timeout, unit)) {
                pending.remove(requestId);
                throw new IOException("音源运行进程响应超时");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("音源请求被中断", error);
        }
        if (target.error.get() != null) throw new IOException(target.error.get());
        return target.result.get();
    }

    private void ensureBound() throws IOException {
        if (remote != null) return;
        synchronized (this) {
            if (!bound) {
                bound = context.bindService(new Intent(context, SourceRuntimeService.class),
                        connection, Context.BIND_AUTO_CREATE);
            }
        }
        if (!bound) throw new IOException("无法启动音源运行进程");
        try {
            if (!connected.await(5, TimeUnit.SECONDS) || remote == null) {
                throw new IOException("音源运行进程连接超时");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("音源运行进程连接被中断", error);
        }
    }

    private boolean handleReply(Message message) {
        if (message.what != SourceRuntimeProtocol.RESULT) return false;
        Bundle data = message.getData();
        Pending target = pending.remove(data.getString(SourceRuntimeProtocol.REQUEST_ID, ""));
        if (target == null) return true;
        if (data.getBoolean(SourceRuntimeProtocol.OK, false)) {
            try {
                target.result.set(new JsonParser().parse(
                        data.getString(SourceRuntimeProtocol.DATA, "null")));
            } catch (Exception error) {
                target.error.set("音源运行结果无效");
            }
        } else {
            target.error.set(data.getString(SourceRuntimeProtocol.ERROR, "音源运行失败"));
        }
        target.latch.countDown();
        return true;
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            remote = new Messenger(service);
            connected.countDown();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            remote = null;
            for (Pending target : pending.values()) {
                target.error.compareAndSet(null, "音源运行进程已重启");
                target.latch.countDown();
            }
            pending.clear();
        }
    };

    @Override
    public void close() {
        if (bound) {
            context.unbindService(connection);
            bound = false;
        }
        replyThread.quitSafely();
    }

    private static final class Pending {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<JsonElement> result = new AtomicReference<>();
        final AtomicReference<String> error = new AtomicReference<>();
    }
}
