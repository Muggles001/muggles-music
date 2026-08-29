package top.boluofan.musictv.util;

import android.view.View;

public final class ViewHierarchy {
    private ViewHierarchy() {
    }

    public static boolean contains(View ancestor, View child) {
        View current = child;
        while (current != null) {
            if (current == ancestor) return true;
            if (!(current.getParent() instanceof View)) return false;
            current = (View) current.getParent();
        }
        return false;
    }
}
