package top.boluofan.musictv.ui.common;

import androidx.recyclerview.widget.RecyclerView;

public final class RecyclerFocus {
    private RecyclerFocus() {
    }

    public static boolean requestPosition(RecyclerView recyclerView, int position) {
        if (recyclerView == null || position < 0) return false;
        RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(position);
        if (holder != null) return holder.itemView.requestFocus();
        recyclerView.scrollToPosition(position);
        recyclerView.post(() -> {
            RecyclerView.ViewHolder target = recyclerView.findViewHolderForAdapterPosition(position);
            if (target != null) target.itemView.requestFocus();
        });
        return true;
    }
}
