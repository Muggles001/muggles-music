package top.boluofan.musictv.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;
import com.google.gson.JsonObject;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SourceScriptStoreTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void failedSaveRestoresPreferencesAndRetryConverges() throws Exception {
        File directory = temporaryFolder.newFolder("save-retry");
        MemoryPreferences preferences = new MemoryPreferences();
        SourceScriptStore store = new SourceScriptStore(directory, preferences);
        ImportedSource first = source("A");
        ImportedSource second = source("B");
        store.saveActive(first);

        preferences.failNextCommit();
        assertThrows(IllegalStateException.class, () -> store.saveActive(second));

        assertEquals(first.metadata.sha256, store.getActive().metadata.sha256);
        assertEquals(1, directory.list().length);

        store.saveActive(second);

        assertEquals(second.metadata.sha256, store.getActive().metadata.sha256);
        assertEquals(2, directory.list().length);
    }

    @Test
    public void failedClearRestoresPreferencesAndKeepsFiles() throws Exception {
        File directory = temporaryFolder.newFolder("clear-retry");
        MemoryPreferences preferences = new MemoryPreferences();
        SourceScriptStore store = new SourceScriptStore(directory, preferences);
        ImportedSource source = source("active");
        store.saveActive(source);

        preferences.failNextCommit();
        assertFalse(store.clear());

        ImportedSource restored = store.getActive();
        assertNotNull(restored);
        assertEquals(source.metadata.sha256, restored.metadata.sha256);
        assertEquals(1, directory.list().length);

        assertTrue(store.clear());
        assertNull(store.getActive());
        assertEquals(0, directory.list().length);
    }

    @Test
    public void invalidPreviousVersionRestoresCurrentVersion() throws Exception {
        File directory = temporaryFolder.newFolder("invalid-previous");
        SourceScriptStore store = new SourceScriptStore(directory, new MemoryPreferences());
        ImportedSource first = source("A");
        ImportedSource second = source("B");
        store.saveActive(first);
        store.saveActive(second);
        File firstArtifact = new File(directory,
                SourceFileLifecycle.sha256(first.script) + ".js");
        assertTrue(firstArtifact.delete());

        assertNull(store.rollbackToValidPrevious());
        assertEquals(second.metadata.sha256, store.getActive().metadata.sha256);
    }

    private ImportedSource source(String version) {
        String script = "/*!\n * @name Test Source\n * @version " + version + "\n */\n"
                + "const version = '" + version + "';";
        return new ImportedSource(SourceScriptMetadata.parse(script), null, script, new JsonObject());
    }

    private static final class MemoryPreferences implements SharedPreferences {
        private final Map<String, Object> values = new HashMap<>();
        private boolean failNextCommit;

        void failNextCommit() {
            failNextCommit = true;
        }

        @Override
        public Map<String, ?> getAll() {
            return new HashMap<>(values);
        }

        @Override
        public String getString(String key, String defaultValue) {
            Object value = values.get(key);
            return value instanceof String ? (String) value : defaultValue;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Set<String> getStringSet(String key, Set<String> defaultValues) {
            Object value = values.get(key);
            return value instanceof Set ? new HashSet<>((Set<String>) value) : defaultValues;
        }

        @Override
        public int getInt(String key, int defaultValue) {
            Object value = values.get(key);
            return value instanceof Integer ? (Integer) value : defaultValue;
        }

        @Override
        public long getLong(String key, long defaultValue) {
            Object value = values.get(key);
            return value instanceof Long ? (Long) value : defaultValue;
        }

        @Override
        public float getFloat(String key, float defaultValue) {
            Object value = values.get(key);
            return value instanceof Float ? (Float) value : defaultValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            Object value = values.get(key);
            return value instanceof Boolean ? (Boolean) value : defaultValue;
        }

        @Override
        public boolean contains(String key) {
            return values.containsKey(key);
        }

        @Override
        public Editor edit() {
            return new MemoryEditor();
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {
        }

        @Override
        public void unregisterOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {
        }

        private final class MemoryEditor implements Editor {
            private final Map<String, Object> updates = new HashMap<>();
            private final Set<String> removals = new HashSet<>();
            private boolean clear;

            @Override
            public Editor putString(String key, String value) {
                updates.put(key, value);
                removals.remove(key);
                return this;
            }

            @Override
            public Editor putStringSet(String key, Set<String> value) {
                updates.put(key, value == null ? null : new HashSet<>(value));
                removals.remove(key);
                return this;
            }

            @Override
            public Editor putInt(String key, int value) {
                updates.put(key, value);
                removals.remove(key);
                return this;
            }

            @Override
            public Editor putLong(String key, long value) {
                updates.put(key, value);
                removals.remove(key);
                return this;
            }

            @Override
            public Editor putFloat(String key, float value) {
                updates.put(key, value);
                removals.remove(key);
                return this;
            }

            @Override
            public Editor putBoolean(String key, boolean value) {
                updates.put(key, value);
                removals.remove(key);
                return this;
            }

            @Override
            public Editor remove(String key) {
                updates.remove(key);
                removals.add(key);
                return this;
            }

            @Override
            public Editor clear() {
                clear = true;
                updates.clear();
                removals.clear();
                return this;
            }

            @Override
            public boolean commit() {
                if (clear) values.clear();
                for (String key : removals) values.remove(key);
                values.putAll(updates);
                boolean succeeds = !failNextCommit;
                failNextCommit = false;
                return succeeds;
            }

            @Override
            public void apply() {
                commit();
            }
        }
    }
}
