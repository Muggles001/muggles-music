package top.boluofan.musictv.source;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public final class SourceScriptStore {
    private static final String PREFS = "MugglesSourcePrefs";
    private static final String KEY_ACTIVE = "active_source";
    private static final String KEY_PREVIOUS = "previous_source";
    private static final Gson GSON = new Gson();

    private final Context context;

    public SourceScriptStore(Context context) {
        this.context = context.getApplicationContext();
    }

    public synchronized void saveActive(ImportedSource source) throws Exception {
        File directory = new File(context.getFilesDir(), "sources");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("无法创建音源目录");
        }
        File target = new File(directory, source.metadata.id + ".js");
        File pending = new File(directory, source.metadata.id + ".pending");
        try (FileOutputStream output = new FileOutputStream(pending)) {
            output.write(source.script.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
        if (target.exists() && !target.delete()) {
            throw new IllegalStateException("无法替换旧音源");
        }
        if (!pending.renameTo(target)) {
            throw new IllegalStateException("无法保存音源脚本");
        }

        StoredSource stored = new StoredSource();
        stored.id = source.metadata.id;
        stored.name = source.metadata.name;
        stored.description = source.metadata.description;
        stored.version = source.metadata.version;
        stored.author = source.metadata.author;
        stored.homepage = source.metadata.homepage;
        stored.sha256 = source.metadata.sha256;
        stored.importUrl = source.importUrl;
        stored.scriptPath = target.getAbsolutePath();
        stored.capabilitiesJson = source.capabilities.toString();
        String current = preferences().getString(KEY_ACTIVE, "");
        SharedPreferences.Editor editor = preferences().edit();
        if (current != null && !current.isEmpty()) {
            StoredSource currentSource = GSON.fromJson(current, StoredSource.class);
            if (currentSource != null && !source.metadata.sha256.equals(currentSource.sha256)) {
                editor.putString(KEY_PREVIOUS, current);
            }
        }
        boolean saved = editor.putString(KEY_ACTIVE, GSON.toJson(stored)).commit();
        if (!saved) throw new IllegalStateException("无法保存音源配置");
    }

    @Nullable
    public synchronized ImportedSource getActive() {
        String raw = preferences().getString(KEY_ACTIVE, "");
        if (raw == null || raw.isEmpty()) return null;
        try {
            StoredSource stored = GSON.fromJson(raw, StoredSource.class);
            File scriptFile = new File(stored.scriptPath);
            if (!scriptFile.isFile()) return null;
            byte[] bytes = new byte[(int) scriptFile.length()];
            try (FileInputStream input = new FileInputStream(scriptFile)) {
                int offset = 0;
                while (offset < bytes.length) {
                    int read = input.read(bytes, offset, bytes.length - offset);
                    if (read < 0) break;
                    offset += read;
                }
                if (offset != bytes.length) return null;
            }
            String script = new String(bytes, StandardCharsets.UTF_8);
            SourceScriptMetadata metadata = SourceScriptMetadata.parse(script);
            if (!metadata.sha256.equals(stored.sha256)) return null;
            JsonObject capabilities = GSON.fromJson(stored.capabilitiesJson, JsonObject.class);
            return new ImportedSource(metadata, stored.importUrl, script, capabilities);
        } catch (Exception ignored) {
            return null;
        }
    }

    public synchronized void clear() {
        ImportedSource active = getActive();
        if (active != null) {
            File script = new File(context.getFilesDir(), "sources/" + active.metadata.id + ".js");
            if (script.isFile()) script.delete();
        }
        preferences().edit().remove(KEY_ACTIVE).remove(KEY_PREVIOUS).apply();
    }

    public synchronized boolean hasPrevious() {
        String previous = preferences().getString(KEY_PREVIOUS, "");
        return previous != null && !previous.isEmpty();
    }

    public synchronized boolean rollback() {
        String active = preferences().getString(KEY_ACTIVE, "");
        String previous = preferences().getString(KEY_PREVIOUS, "");
        if (previous == null || previous.isEmpty()) return false;
        return preferences().edit()
                .putString(KEY_ACTIVE, previous)
                .putString(KEY_PREVIOUS, active == null ? "" : active)
                .commit();
    }

    private SharedPreferences preferences() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static final class StoredSource {
        String id;
        String name;
        String description;
        String version;
        String author;
        String homepage;
        String sha256;
        String importUrl;
        String scriptPath;
        String capabilitiesJson;
    }
}
