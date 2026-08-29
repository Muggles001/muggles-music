package top.boluofan.musictv.settings;

import androidx.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class SettingsItem {
    public enum Type {
        SECTION,
        INFO,
        ROUTE,
        TOGGLE,
        SEGMENTED,
        ACTION,
        DANGER
    }

    public final String key;
    public final Type type;
    public final String title;
    public final String summary;
    public final String value;
    public final boolean checked;
    public final boolean enabled;
    public final List<String> segments;
    public final int selectedSegment;

    private SettingsItem(String key, Type type, String title, String summary, String value,
                         boolean checked, boolean enabled, List<String> segments,
                         int selectedSegment) {
        this.key = key;
        this.type = type;
        this.title = title;
        this.summary = summary == null ? "" : summary;
        this.value = value == null ? "" : value;
        this.checked = checked;
        this.enabled = enabled;
        this.segments = segments;
        this.selectedSegment = selectedSegment;
    }

    public static SettingsItem section(String key, String title) {
        return new SettingsItem(key, Type.SECTION, title, "", "", false, true,
                Collections.emptyList(), -1);
    }

    public static SettingsItem route(String key, String title, @Nullable String summary,
                                     @Nullable String value) {
        return interactive(key, Type.ROUTE, title, summary, value, false, true);
    }

    public static SettingsItem info(String key, String title, @Nullable String body) {
        return interactive(key, Type.INFO, title, body, "", false, true);
    }

    public static SettingsItem toggle(String key, String title, @Nullable String summary,
                                      boolean checked) {
        return interactive(key, Type.TOGGLE, title, summary, "", checked, true);
    }

    public static SettingsItem segmented(String key, String title, @Nullable String summary,
                                         int selectedSegment, String... segments) {
        return new SettingsItem(key, Type.SEGMENTED, title, summary, "", false, true,
                Collections.unmodifiableList(Arrays.asList(segments)), selectedSegment);
    }

    public static SettingsItem action(String key, String title, @Nullable String summary,
                                      boolean enabled) {
        return interactive(key, Type.ACTION, title, summary, "", false, enabled);
    }

    public static SettingsItem danger(String key, String title, @Nullable String summary,
                                      boolean enabled) {
        return interactive(key, Type.DANGER, title, summary, "", false, enabled);
    }

    private static SettingsItem interactive(String key, Type type, String title,
                                            @Nullable String summary, @Nullable String value,
                                            boolean checked, boolean enabled) {
        return new SettingsItem(key, type, title, summary, value, checked, enabled,
                Collections.emptyList(), -1);
    }

    public boolean isFocusable() {
        return type != Type.SECTION && enabled;
    }
}
