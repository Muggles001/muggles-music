package top.boluofan.musictv.source;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class SourceScriptStore {
    private static final String PREFS = "MugglesSourcePrefs";
    private static final String KEY_ACTIVE = "active_source";
    private static final String KEY_PREVIOUS = "previous_source";
    private static final Gson GSON = new Gson();

    private final File sourceDirectory;
    private final SharedPreferences preferences;

    public SourceScriptStore(Context context) {
        Context applicationContext = context.getApplicationContext();
        sourceDirectory = new File(applicationContext.getFilesDir(), "sources");
        preferences = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    SourceScriptStore(File sourceDirectory, SharedPreferences preferences) {
        this.sourceDirectory = sourceDirectory;
        this.preferences = preferences;
    }

    public synchronized void saveActive(ImportedSource source) throws Exception {
        SharedPreferences preferences = preferences();
        String current = preferences.getString(KEY_ACTIVE, "");
        String previous = preferences.getString(KEY_PREVIOUS, "");
        String originalPrevious = previous;
        reconcile(current, previous);

        File target = SourceFileLifecycle.persist(sourceDirectory(), source.script);
        StoredSource stored = storedSource(source, target);
        SharedPreferences.Editor editor = preferences.edit();
        if (hasDifferentArtifact(current, stored.sha256)) {
            editor.putString(KEY_PREVIOUS, current);
            previous = current;
        }
        String active = GSON.toJson(stored);
        boolean saved = editor.putString(KEY_ACTIVE, active).commit();
        if (!saved) {
            restorePreferences(preferences, current, originalPrevious);
            reconcile(current, originalPrevious);
            throw new IllegalStateException("无法保存音源配置");
        }
        reconcile(active, previous);
    }

    @Nullable
    public synchronized ImportedSource getActive() {
        SharedPreferences preferences = preferences();
        String active = preferences.getString(KEY_ACTIVE, "");
        String previous = preferences.getString(KEY_PREVIOUS, "");
        reconcile(active, previous);
        if (active == null || active.isEmpty()) return null;

        try {
            StoredSource stored = GSON.fromJson(active, StoredSource.class);
            if (stored == null || stored.scriptPath == null) return null;
            File scriptFile = new File(stored.scriptPath);
            if (!scriptFile.isFile()) return null;
            String script = SourceFileLifecycle.read(scriptFile);
            SourceScriptMetadata metadata = SourceScriptMetadata.parse(script);
            if (!metadata.sha256.equals(stored.sha256)) return null;
            JsonObject capabilities = GSON.fromJson(stored.capabilitiesJson, JsonObject.class);
            ImportedSource source = new ImportedSource(metadata, stored.importUrl, script, capabilities);
            active = migrateActive(preferences, active, stored, script);
            reconcile(active, previous);
            return source;
        } catch (Exception ignored) {
            reconcile(preferences.getString(KEY_ACTIVE, active), previous);
            return null;
        }
    }

    public synchronized boolean clear() {
        SharedPreferences preferences = preferences();
        String active = preferences.getString(KEY_ACTIVE, "");
        String previous = preferences.getString(KEY_PREVIOUS, "");
        if (!preferences.edit().remove(KEY_ACTIVE).remove(KEY_PREVIOUS).commit()) {
            restorePreferences(preferences, active, previous);
            return false;
        }
        deleteStoredScript(active);
        deleteStoredScript(previous);
        SourceFileLifecycle.clear(sourceDirectory());
        return true;
    }

    public synchronized boolean hasPrevious() {
        String previous = preferences().getString(KEY_PREVIOUS, "");
        return previous != null && !previous.isEmpty();
    }

    public synchronized boolean rollback() {
        SharedPreferences preferences = preferences();
        String active = preferences.getString(KEY_ACTIVE, "");
        String previous = preferences.getString(KEY_PREVIOUS, "");
        if (previous == null || previous.isEmpty()) return false;
        boolean saved = preferences.edit()
                .putString(KEY_ACTIVE, previous)
                .putString(KEY_PREVIOUS, active == null ? "" : active)
                .commit();
        if (saved) reconcile(previous, active);
        else {
            restorePreferences(preferences, active, previous);
            reconcile(active, previous);
        }
        return saved;
    }

    @Nullable
    public synchronized ImportedSource rollbackToValidPrevious() {
        if (!rollback()) return null;
        ImportedSource restored = getActive();
        if (restored != null) return restored;
        rollback();
        return null;
    }

    private String migrateActive(SharedPreferences preferences, String active, StoredSource stored,
                                 String script) throws Exception {
        File target = SourceFileLifecycle.persist(sourceDirectory(), script);
        if (SourceFileLifecycle.sameFile(target, new File(stored.scriptPath))) return active;
        stored.scriptPath = target.getAbsolutePath();
        String migrated = GSON.toJson(stored);
        if (preferences.edit().putString(KEY_ACTIVE, migrated).commit()) return migrated;
        preferences.edit().putString(KEY_ACTIVE, active).commit();
        return active;
    }

    private void restorePreferences(SharedPreferences preferences, String active, String previous) {
        preferences.edit()
                .putString(KEY_ACTIVE, active == null ? "" : active)
                .putString(KEY_PREVIOUS, previous == null ? "" : previous)
                .commit();
    }

    private void reconcile(String active, String previous) {
        Set<File> retained = new HashSet<>();
        addStoredScript(retained, active);
        addStoredScript(retained, previous);
        SourceFileLifecycle.reconcile(sourceDirectory(), retained);
    }

    private void addStoredScript(Set<File> retained, String raw) {
        if (raw == null || raw.isEmpty()) return;
        try {
            StoredSource source = GSON.fromJson(raw, StoredSource.class);
            if (source != null && source.scriptPath != null) retained.add(new File(source.scriptPath));
        } catch (Exception ignored) {
        }
    }

    private boolean hasDifferentArtifact(String raw, String sha256) {
        if (raw == null || raw.isEmpty()) return false;
        try {
            StoredSource source = GSON.fromJson(raw, StoredSource.class);
            return source == null || !sha256.equals(source.sha256);
        } catch (Exception ignored) {
            return true;
        }
    }

    private StoredSource storedSource(ImportedSource source, File target) {
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
        return stored;
    }

    private void deleteStoredScript(String raw) {
        if (raw == null || raw.isEmpty()) return;
        try {
            StoredSource source = GSON.fromJson(raw, StoredSource.class);
            if (source != null && source.scriptPath != null) {
                SourceFileLifecycle.delete(new File(source.scriptPath));
            }
        } catch (Exception ignored) {
        }
    }

    private File sourceDirectory() {
        return sourceDirectory;
    }

    private SharedPreferences preferences() {
        return preferences;
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

final class SourceFileLifecycle {
    private static final String SCRIPT_SUFFIX = ".js";
    private static final String PENDING_SUFFIX = ".pending";

    private SourceFileLifecycle() {
    }

    static synchronized File persist(File directory, String script) throws Exception {
        ensureDirectory(directory);
        String sha256 = sha256(script);
        File target = new File(directory, sha256 + SCRIPT_SUFFIX);
        if (target.isFile() && sha256.equals(sha256(read(target)))) return target;

        File pending = new File(directory, sha256 + PENDING_SUFFIX);
        delete(pending);
        try (FileOutputStream output = new FileOutputStream(pending)) {
            output.write(script.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
        if (target.exists() && !target.delete()) {
            throw new IllegalStateException("无法替换损坏的音源脚本");
        }
        if (!pending.renameTo(target)) {
            throw new IllegalStateException("无法保存音源脚本");
        }
        return target;
    }

    static void reconcile(File directory, Set<File> retained) {
        File[] files = directory.listFiles();
        if (files == null) return;
        Set<String> retainedPaths = new HashSet<>();
        for (File file : retained) retainedPaths.add(canonicalPath(file));
        for (File file : files) {
            if (file.getName().endsWith(PENDING_SUFFIX)
                    || !retainedPaths.contains(canonicalPath(file))) {
                delete(file);
            }
        }
    }

    static void clear(File directory) {
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) delete(file);
    }

    static void delete(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) delete(child);
            }
        }
        if (file.exists()) file.delete();
    }

    static String read(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    static boolean sameFile(File first, File second) {
        return canonicalPath(first).equals(canonicalPath(second));
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) result.append(String.format(Locale.US, "%02x", b));
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("无法计算音源摘要", e);
        }
    }

    private static void ensureDirectory(File directory) {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("无法创建音源目录");
        }
    }

    private static String canonicalPath(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException ignored) {
            return file.getAbsolutePath();
        }
    }
}
