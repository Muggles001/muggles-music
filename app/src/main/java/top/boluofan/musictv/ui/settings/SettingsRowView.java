package top.boluofan.musictv.ui.settings;

import android.content.Context;
import android.util.AttributeSet;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Checkable;
import android.widget.LinearLayout;

public final class SettingsRowView extends LinearLayout implements Checkable {
    private boolean checkable;
    private boolean checked;

    public SettingsRowView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setCheckable(boolean checkable) {
        this.checkable = checkable;
    }

    @Override
    public void setChecked(boolean checked) {
        if (this.checked == checked) return;
        this.checked = checked;
        refreshDrawableState();
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    @Override
    public boolean isChecked() {
        return checked;
    }

    @Override
    public void toggle() {
        if (checkable) setChecked(!checked);
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return checkable ? android.widget.Switch.class.getName() : super.getAccessibilityClassName();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(getAccessibilityClassName());
        info.setCheckable(checkable);
        info.setChecked(checked);
    }
}
