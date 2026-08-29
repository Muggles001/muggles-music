package top.boluofan.musictv.test;

import android.content.SharedPreferences;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class InMemorySharedPreferences implements SharedPreferences {
    private final Map<String, Object> values = new HashMap<>();
    private int commitsUntilFailure = -1;

    public InMemorySharedPreferences putInitial(String key, Object value) {
        values.put(key, value);
        return this;
    }

    public void failNextCommit() {
        failCommitNumber(1);
    }

    public void failCommitNumber(int commitNumber) {
        commitsUntilFailure = commitNumber;
    }

    @Override
    public Map<String, ?> getAll() {
        return Collections.unmodifiableMap(new HashMap<>(values));
    }

    @Override
    public String getString(String key, String defaultValue) {
        Object value = values.get(key);
        if (value == null) return defaultValue;
        if (!(value instanceof String)) throw new ClassCastException();
        return (String) value;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Set<String> getStringSet(String key, Set<String> defaultValues) {
        Object value = values.get(key);
        if (value == null) return defaultValues;
        if (!(value instanceof Set)) throw new ClassCastException();
        return new HashSet<>((Set<String>) value);
    }

    @Override
    public int getInt(String key, int defaultValue) {
        Object value = values.get(key);
        if (value == null) return defaultValue;
        if (!(value instanceof Integer)) throw new ClassCastException();
        return (Integer) value;
    }

    @Override
    public long getLong(String key, long defaultValue) {
        Object value = values.get(key);
        if (value == null) return defaultValue;
        if (!(value instanceof Long)) throw new ClassCastException();
        return (Long) value;
    }

    @Override
    public float getFloat(String key, float defaultValue) {
        Object value = values.get(key);
        if (value == null) return defaultValue;
        if (!(value instanceof Float)) throw new ClassCastException();
        return (Float) value;
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = values.get(key);
        if (value == null) return defaultValue;
        if (!(value instanceof Boolean)) throw new ClassCastException();
        return (Boolean) value;
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
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
    }

    private final class MemoryEditor implements Editor {
        private final Map<String, Object> updates = new HashMap<>();
        private final Set<String> removals = new HashSet<>();
        private boolean clear;

        @Override
        public Editor putString(String key, String value) {
            updates.put(key, value);
            return this;
        }

        @Override
        public Editor putStringSet(String key, Set<String> value) {
            updates.put(key, value == null ? null : new HashSet<>(value));
            return this;
        }

        @Override
        public Editor putInt(String key, int value) {
            updates.put(key, value);
            return this;
        }

        @Override
        public Editor putLong(String key, long value) {
            updates.put(key, value);
            return this;
        }

        @Override
        public Editor putFloat(String key, float value) {
            updates.put(key, value);
            return this;
        }

        @Override
        public Editor putBoolean(String key, boolean value) {
            updates.put(key, value);
            return this;
        }

        @Override
        public Editor remove(String key) {
            removals.add(key);
            return this;
        }

        @Override
        public Editor clear() {
            clear = true;
            return this;
        }

        @Override
        public boolean commit() {
            if (commitsUntilFailure > 0) commitsUntilFailure--;
            if (commitsUntilFailure == 0) {
                commitsUntilFailure = -1;
                return false;
            }
            if (clear) values.clear();
            for (String key : removals) values.remove(key);
            values.putAll(updates);
            return true;
        }

        @Override
        public void apply() {
            commit();
        }
    }
}
