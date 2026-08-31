package top.boluofan.musictv.testutil;

import android.content.Context;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;

public final class RecyclerViewLayoutFixture {
    private RecyclerViewLayoutFixture() {
    }

    public static void layout(RecyclerView recyclerView, Context context) {
        recyclerView.measure(
                View.MeasureSpec.makeMeasureSpec(dp(context, 960), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(dp(context, 540), View.MeasureSpec.EXACTLY));
        recyclerView.layout(0, 0, recyclerView.getMeasuredWidth(), recyclerView.getMeasuredHeight());
    }

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
