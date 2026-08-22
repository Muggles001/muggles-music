package top.boluofan.musictv.source;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Base64;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.whl.quickjs.android.QuickJSLoader;
import com.whl.quickjs.wrapper.JSFunction;
import com.whl.quickjs.wrapper.QuickJSContext;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SourceRuntimeEngine implements AutoCloseable {
    private static final String TAG = "SourceRuntime";
    private static final Gson GSON = new Gson();
    private static final long INIT_TIMEOUT_MS = 5000;
    private static final long RESOLVE_TIMEOUT_MS = 22000;

    public interface LoadCallback {
        void onLoaded(JsonObject capabilities);
        void onError(String error);
    }

    public interface ResolveCallback {
        void onSuccess(JsonElement result);
        void onError(String error);
    }

    private final Context context;
    private final SourceHttpBridge httpBridge = new SourceHttpBridge();
    private final HandlerThread runtimeThread = new HandlerThread("MugglesSourceRuntime");
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentHashMap<String, PendingResolve> pendingResolves = new ConcurrentHashMap<>();
    private final Handler runtimeHandler;
    private volatile QuickJSContext jsContext;
    private volatile LoadCallback pendingLoad;
    private volatile boolean loaded;

    public SourceRuntimeEngine(Context context) {
        this.context = context.getApplicationContext();
        runtimeThread.start();
        runtimeHandler = new Handler(runtimeThread.getLooper());
    }

    public void load(ImportedSource source, LoadCallback callback) {
        AtomicBoolean completed = new AtomicBoolean();
        LoadCallback guarded = new LoadCallback() {
            @Override
            public void onLoaded(JsonObject capabilities) {
                if (completed.compareAndSet(false, true)) callback.onLoaded(capabilities);
            }

            @Override
            public void onError(String error) {
                if (completed.compareAndSet(false, true)) callback.onError(error);
            }
        };
        pendingLoad = guarded;
        loaded = false;
        watchdog.schedule(() -> {
            LoadCallback target = pendingLoad;
            if (target != null) {
                pendingLoad = null;
                target.onError("音源初始化超时");
            }
        }, INIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        runtimeHandler.post(() -> {
            try {
                createContext(source);
                jsContext.evaluate(source.script, source.metadata.name + ".js");
            } catch (Throwable error) {
                failLoad(message(error));
            }
        });
    }

    public void resolve(String source, String action, JsonObject info, ResolveCallback callback) {
        if (!loaded || jsContext == null) {
            callback.onError("音源尚未初始化");
            return;
        }
        String requestId = UUID.randomUUID().toString();
        PendingResolve pending = new PendingResolve(callback);
        pendingResolves.put(requestId, pending);
        watchdog.schedule(() -> {
            PendingResolve target = pendingResolves.remove(requestId);
            if (target != null && target.completed.compareAndSet(false, true)) {
                target.callback.onError("音源解析超时");
            }
        }, RESOLVE_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        runtimeHandler.post(() -> {
            try {
                QuickJSContext runtime = jsContext;
                if (runtime == null) throw new IllegalStateException("音源运行时已关闭");
                JSFunction call = runtime.getGlobalObject().getJSFunction("__muggles_call");
                call.callVoid(requestId, source, action, GSON.toJson(info));
                call.release();
            } catch (Throwable error) {
                completeResolve(requestId, null, message(error));
            }
        });
    }

    private void createContext(ImportedSource source) throws Exception {
        destroyContext();
        QuickJSLoader.init();
        QuickJSContext runtime = QuickJSContext.create();
        runtime.setMemoryLimit(24 * 1024 * 1024);
        runtime.setMaxStackSize(1024 * 1024);
        runtime.setEnableStackTrace(true);
        runtime.setConsole(new QuickJSContext.Console() {
            @Override public void log(String message) { Log.d(TAG, message); }
            @Override public void info(String message) { Log.i(TAG, message); }
            @Override public void warn(String message) { Log.w(TAG, message); }
            @Override public void error(String message) { Log.e(TAG, message); }
        });
        runtime.getGlobalObject().setProperty("__muggles_http__", args ->
                httpBridge.request(stringArg(args, 0), stringArg(args, 1)));
        runtime.getGlobalObject().setProperty("__muggles_emit__", args -> {
            handleEmit(stringArg(args, 0), stringArg(args, 1));
            return null;
        });
        runtime.getGlobalObject().setProperty("__muggles_set_timeout__", args -> {
            int id = ((Number) args[0]).intValue();
            long delay = ((Number) args[1]).longValue();
            runtimeHandler.postDelayed(() -> fireTimeout(id), Math.max(0, Math.min(delay, 60000)));
            return null;
        });
        runtime.getGlobalObject().setProperty("__muggles_md5__", args ->
                SourceCryptoBridge.md5(stringArg(args, 0)));
        runtime.getGlobalObject().setProperty("__muggles_random__", args ->
                SourceCryptoBridge.randomBytes(((Number) args[0]).intValue()));
        runtime.getGlobalObject().setProperty("__muggles_aes__", args ->
                SourceCryptoBridge.aes(stringArg(args, 0), stringArg(args, 1),
                        stringArg(args, 2), stringArg(args, 3)));
        runtime.getGlobalObject().setProperty("__muggles_rsa__", args ->
                SourceCryptoBridge.rsa(stringArg(args, 0), stringArg(args, 1)));
        runtime.getGlobalObject().setProperty("__muggles_hex_to_base64__", args ->
                hexToBase64(stringArg(args, 0)));
        runtime.getGlobalObject().setProperty("__muggles_base64_to_hex__", args ->
                base64ToHex(stringArg(args, 0)));
        jsContext = runtime;

        runtime.evaluate(readAsset("source-preload.js"), "source-preload.js");
        JsonObject info = new JsonObject();
        info.addProperty("id", source.metadata.id);
        info.addProperty("name", source.metadata.name);
        info.addProperty("description", source.metadata.description);
        info.addProperty("version", source.metadata.version);
        info.addProperty("author", source.metadata.author);
        info.addProperty("homepage", source.metadata.homepage);
        info.addProperty("rawScript", source.script);
        JSFunction setup = runtime.getGlobalObject().getJSFunction("__muggles_setup");
        setup.callVoid(GSON.toJson(info));
        setup.release();
    }

    private void handleEmit(String event, String payload) {
        try {
            JsonObject data = new JsonParser().parse(payload).getAsJsonObject();
            if ("inited".equals(event)) {
                JsonObject capabilities = data.has("sources") && data.get("sources").isJsonObject()
                        ? data.getAsJsonObject("sources") : new JsonObject();
                loaded = true;
                LoadCallback callback = pendingLoad;
                pendingLoad = null;
                if (callback != null) callback.onLoaded(capabilities.deepCopy());
                return;
            }
            if ("result".equals(event)) {
                String requestId = data.has("requestId") ? data.get("requestId").getAsString() : "";
                boolean ok = data.has("ok") && data.get("ok").getAsBoolean();
                if (ok) {
                    completeResolve(requestId, data.get("data"), null);
                } else {
                    String error = data.has("error") ? data.get("error").getAsString() : "音源解析失败";
                    completeResolve(requestId, null, error);
                }
            }
        } catch (Exception error) {
            Log.e(TAG, "Invalid source runtime event", error);
        }
    }

    private void completeResolve(String requestId, JsonElement result, String error) {
        PendingResolve pending = pendingResolves.remove(requestId);
        if (pending == null || !pending.completed.compareAndSet(false, true)) return;
        if (error == null) pending.callback.onSuccess(result == null ? com.google.gson.JsonNull.INSTANCE : result);
        else pending.callback.onError(error);
    }

    private void fireTimeout(int id) {
        try {
            QuickJSContext runtime = jsContext;
            if (runtime == null) return;
            JSFunction function = runtime.getGlobalObject().getJSFunction("__muggles_fire_timeout");
            function.callVoid(id);
            function.release();
        } catch (Throwable error) {
            Log.e(TAG, "Source timer failed", error);
        }
    }

    private void failLoad(String error) {
        loaded = false;
        LoadCallback callback = pendingLoad;
        pendingLoad = null;
        if (callback != null) callback.onError(error);
    }

    private String readAsset(String name) throws Exception {
        try (InputStream input = context.getAssets().open(name)) {
            byte[] buffer = new byte[input.available()];
            int offset = 0;
            while (offset < buffer.length) {
                int read = input.read(buffer, offset, buffer.length - offset);
                if (read < 0) break;
                offset += read;
            }
            if (offset != buffer.length) throw new IllegalStateException("无法读取音源运行时");
            return new String(buffer, StandardCharsets.UTF_8);
        }
    }

    private static String stringArg(Object[] args, int index) {
        if (args == null || index >= args.length || args[index] == null) return "";
        return String.valueOf(args[index]);
    }

    private static String hexToBase64(String hex) {
        if (hex.length() % 2 != 0) throw new IllegalArgumentException("Invalid hex input");
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    private static String base64ToHex(String value) {
        byte[] bytes = Base64.decode(value, Base64.NO_WRAP);
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) result.append(String.format(Locale.US, "%02x", b));
        return result.toString();
    }

    private static String message(Throwable error) {
        String value = error.getMessage();
        return value == null || value.trim().isEmpty() ? error.getClass().getSimpleName() : value;
    }

    private void destroyContext() {
        QuickJSContext runtime = jsContext;
        jsContext = null;
        loaded = false;
        if (runtime != null) runtime.destroy();
    }

    @Override
    public void close() {
        for (PendingResolve pending : pendingResolves.values()) {
            if (pending.completed.compareAndSet(false, true)) pending.callback.onError("音源运行时已关闭");
        }
        pendingResolves.clear();
        watchdog.shutdownNow();
        runtimeHandler.post(this::destroyContext);
        runtimeThread.quitSafely();
    }

    private static final class PendingResolve {
        final ResolveCallback callback;
        final AtomicBoolean completed = new AtomicBoolean();

        PendingResolve(ResolveCallback callback) {
            this.callback = callback;
        }
    }
}
