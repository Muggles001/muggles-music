package top.boluofan.musictv.source;

import android.content.Context;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SourceRuntimeManager {
    private static volatile SourceRuntimeManager instance;

    public static SourceRuntimeManager get(Context context) {
        if (instance == null) {
            synchronized (SourceRuntimeManager.class) {
                if (instance == null) instance = new SourceRuntimeManager(context.getApplicationContext());
            }
        }
        return instance;
    }

    private final Context context;
    private final SourceScriptStore store;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private RemoteSourceRuntimeClient client;
    private ImportedSource loadedSource;

    private SourceRuntimeManager(Context context) {
        this.context = context;
        store = new SourceScriptStore(context);
        client = new RemoteSourceRuntimeClient(context);
    }

    public void activate(ImportedSource source, SourceRuntimeEngine.LoadCallback callback) {
        executor.execute(() -> {
            File candidate = null;
            try {
                candidate = writeCandidate(source);
                JsonObject capabilities = runtime().load(candidate, source.importUrl);
                boolean supportsCatalog = false;
                for (String code : new String[]{"mg", "kw", "kg", "tx", "wy"}) {
                    if (capabilities.has(code)) {
                        supportsCatalog = true;
                        break;
                    }
                }
                if (!supportsCatalog) {
                    throw new IOException("音源未声明咪咕、酷我、酷狗、QQ 或网易云播放能力");
                }
                ImportedSource validated = new ImportedSource(source.metadata, source.importUrl,
                        source.script, capabilities);
                store.saveActive(validated);
                synchronized (this) {
                    loadedSource = validated;
                }
                callback.onLoaded(capabilities);
            } catch (Exception error) {
                callback.onError(error.getMessage() == null ? "音源启用失败" : error.getMessage());
            } finally {
                if (candidate != null && candidate.isFile()) candidate.delete();
            }
        });
    }

    public synchronized ImportedSource getActiveSource() {
        ImportedSource source = loadedSource;
        return source != null ? source : store.getActive();
    }

    public synchronized void invalidate() {
        loadedSource = null;
        resetClient();
    }

    public String resolveMusicUrlBlocking(String source, JsonObject musicInfo, String quality)
            throws IOException {
        ensureLoadedBlocking();
        JsonObject info = new JsonObject();
        info.addProperty("type", quality);
        info.add("musicInfo", musicInfo);
        JsonElement result;
        try {
            result = runtime().resolve(source, "musicUrl", info);
        } catch (IOException error) {
            if (!shouldReload(error)) throw error;
            synchronized (this) {
                loadedSource = null;
                resetClient();
            }
            ensureLoadedBlocking();
            result = runtime().resolve(source, "musicUrl", info);
        }
        String url = extractUrl(result);
        if (url == null || url.trim().isEmpty()) throw new IOException("音源返回空播放地址");
        return url.trim();
    }

    private void ensureLoadedBlocking() throws IOException {
        ImportedSource active = store.getActive();
        if (active == null) throw new IOException("未导入可用音源");
        synchronized (this) {
            if (loadedSource != null
                    && loadedSource.metadata.sha256.equals(active.metadata.sha256)) return;
        }
        File candidate = null;
        try {
            candidate = writeCandidate(active);
            JsonObject capabilities = runtime().load(candidate, active.importUrl);
            synchronized (this) {
                loadedSource = new ImportedSource(active.metadata, active.importUrl,
                        active.script, capabilities);
            }
        } finally {
            if (candidate != null && candidate.isFile()) candidate.delete();
        }
    }

    private File writeCandidate(ImportedSource source) throws IOException {
        File file = File.createTempFile("lx-source-", ".js", context.getCacheDir());
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(source.script.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
        return file;
    }

    private synchronized RemoteSourceRuntimeClient runtime() {
        return client;
    }

    private synchronized void resetClient() {
        client.close();
        client = new RemoteSourceRuntimeClient(context);
    }

    private static String extractUrl(JsonElement result) {
        if (result == null || result.isJsonNull()) return null;
        if (result.isJsonPrimitive()) return result.getAsString();
        if (result.isJsonObject() && result.getAsJsonObject().has("url")) {
            return result.getAsJsonObject().get("url").getAsString();
        }
        return null;
    }

    private static boolean shouldReload(IOException error) {
        String message = error.getMessage();
        return message != null && (message.contains("尚未初始化")
                || message.contains("运行进程") || message.contains("连接"));
    }
}
