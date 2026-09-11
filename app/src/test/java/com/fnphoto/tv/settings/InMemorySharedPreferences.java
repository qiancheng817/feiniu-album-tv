package com.fnphoto.tv.settings;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class InMemorySharedPreferences implements SharedPreferences {
    private final Map<String, Object> values = new LinkedHashMap<>();
    private final List<ChangeSet> pendingApplies = new ArrayList<>();

    @Override
    public Map<String, ?> getAll() {
        return new LinkedHashMap<>(values);
    }

    @Override
    public String getString(String key, String defValue) {
        Object value = values.get(key);
        return value instanceof String ? (String) value : defValue;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<String> getStringSet(String key, Set<String> defValues) {
        Object value = values.get(key);
        return value instanceof Set ? new HashSet<>((Set<String>) value) : defValues;
    }

    @Override
    public int getInt(String key, int defValue) {
        Object value = values.get(key);
        return value instanceof Integer ? (Integer) value : defValue;
    }

    @Override
    public long getLong(String key, long defValue) {
        Object value = values.get(key);
        return value instanceof Long ? (Long) value : defValue;
    }

    @Override
    public float getFloat(String key, float defValue) {
        Object value = values.get(key);
        return value instanceof Float ? (Float) value : defValue;
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        Object value = values.get(key);
        return value instanceof Boolean ? (Boolean) value : defValue;
    }

    @Override
    public boolean contains(String key) {
        return values.containsKey(key);
    }

    @Override
    public Editor edit() {
        return new EditorImpl();
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
    }

    void flushApplied() {
        List<ChangeSet> changes = new ArrayList<>(pendingApplies);
        pendingApplies.clear();
        for (ChangeSet changeSet : changes) {
            applyChangeSet(changeSet);
        }
    }

    private void applyChangeSet(ChangeSet changeSet) {
        if (changeSet.clear) {
            values.clear();
        }
        for (String key : changeSet.removals) {
            values.remove(key);
        }
        values.putAll(changeSet.updates);
    }

    private final class EditorImpl implements Editor {
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
        public Editor putStringSet(String key, Set<String> values) {
            updates.put(key, new HashSet<>(values));
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
            applyChangeSet(snapshot());
            return true;
        }

        @Override
        public void apply() {
            pendingApplies.add(snapshot());
        }

        private ChangeSet snapshot() {
            return new ChangeSet(clear, new HashMap<>(updates), new HashSet<>(removals));
        }
    }

    private static final class ChangeSet {
        final boolean clear;
        final Map<String, Object> updates;
        final Set<String> removals;

        ChangeSet(boolean clear, Map<String, Object> updates, Set<String> removals) {
            this.clear = clear;
            this.updates = updates;
            this.removals = removals;
        }
    }
}
