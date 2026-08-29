package top.boluofan.musictv.ui.adapter;

import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import top.boluofan.musictv.R;
import top.boluofan.musictv.settings.SettingsItem;
import top.boluofan.musictv.ui.settings.SettingsRowView;
import top.boluofan.musictv.util.FocusAnimationHelper;

public final class SettingsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public interface Listener {
        void onItemActivated(SettingsItem item);
        void onToggleChanged(SettingsItem item, boolean checked);
        void onSegmentChanged(SettingsItem item, int selectedIndex);
        boolean onMoveLeft(SettingsItem item);
    }

    private static final int VIEW_SECTION = 0;
    private static final int VIEW_ROW = 1;
    private static final int VIEW_INFO = 2;
    private final List<SettingsItem> items = new ArrayList<>();
    private final Listener listener;
    private String pendingFocusKey;
    private View topBoundaryFocusTarget;

    public SettingsAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submitList(List<SettingsItem> nextItems) {
        items.clear();
        items.addAll(nextItems == null ? Collections.emptyList() : nextItems);
        notifyDataSetChanged();
    }

    public List<SettingsItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public void setTopBoundaryFocusTarget(View target) {
        topBoundaryFocusTarget = target;
    }

    public void requestFocusByKey(RecyclerView recyclerView, String key) {
        int position = findPositionByKey(key);
        if (position == RecyclerView.NO_POSITION) return;
        pendingFocusKey = key;
        recyclerView.scrollToPosition(position);
        recyclerView.post(() -> focusPosition(recyclerView, position));
    }

    public void requestFirstInteractiveFocus(RecyclerView recyclerView) {
        int position = findInteractivePosition(0, 1);
        if (position != RecyclerView.NO_POSITION) {
            recyclerView.post(() -> focusPosition(recyclerView, position));
        }
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).key.hashCode();
    }

    @Override
    public int getItemViewType(int position) {
        SettingsItem.Type type = items.get(position).type;
        if (type == SettingsItem.Type.SECTION) return VIEW_SECTION;
        return type == SettingsItem.Type.INFO ? VIEW_INFO : VIEW_ROW;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_SECTION) {
            return new SectionHolder(inflater.inflate(R.layout.item_settings_section, parent, false));
        }
        if (viewType == VIEW_INFO) {
            SettingsRowView info = (SettingsRowView) inflater.inflate(
                    R.layout.item_settings_info, parent, false);
            return new InfoHolder(info);
        }
        SettingsRowView row = (SettingsRowView) inflater.inflate(
                R.layout.item_settings_row, parent, false);
        FocusAnimationHelper.applyFocusAnimation(row, FocusAnimationHelper.Profile.ROW);
        return new RowHolder(row);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        SettingsItem item = items.get(position);
        if (holder instanceof SectionHolder) {
            ((SectionHolder) holder).title.setText(item.title);
            return;
        }
        if (holder instanceof InfoHolder) {
            bindInfo((InfoHolder) holder, item);
            return;
        }
        bindRow((RowHolder) holder, item);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private void bindRow(RowHolder holder, SettingsItem item) {
        SettingsRowView row = holder.row;
        row.setEnabled(item.enabled);
        row.setFocusable(item.enabled);
        row.setClickable(item.enabled);
        row.setCheckable(item.type == SettingsItem.Type.TOGGLE);
        row.setChecked(item.checked);
        holder.title.setText(item.title);
        holder.title.setTextColor(ContextCompat.getColorStateList(row.getContext(),
                item.type == SettingsItem.Type.DANGER
                        ? R.color.selector_settings_danger_content
                        : R.color.selector_action_content));
        holder.summary.setText(item.summary);
        holder.summary.setVisibility(item.summary.isEmpty() ? View.GONE : View.VISIBLE);
        holder.value.setText(item.value);
        holder.value.setVisibility(item.value.isEmpty() ? View.GONE : View.VISIBLE);
        holder.chevron.setVisibility(item.type == SettingsItem.Type.ROUTE ? View.VISIBLE : View.GONE);
        holder.toggle.setVisibility(item.type == SettingsItem.Type.TOGGLE ? View.VISIBLE : View.GONE);
        if (item.type == SettingsItem.Type.TOGGLE) {
            holder.toggle.setImageResource(item.checked
                    ? R.drawable.toggle_on_new : R.drawable.toggle_off_new);
        }
        bindSegments(holder, item);
        updateContentDescription(row, item);
        row.setOnClickListener(view -> {
            if (item.type == SettingsItem.Type.TOGGLE) {
                listener.onToggleChanged(item, !item.checked);
            } else {
                listener.onItemActivated(item);
            }
        });
        bindNavigation(holder, row, item);
    }

    private void bindInfo(InfoHolder holder, SettingsItem item) {
        SettingsRowView row = holder.row;
        row.setEnabled(true);
        row.setFocusable(true);
        row.setClickable(false);
        row.setCheckable(false);
        row.setChecked(false);
        holder.title.setText(item.title);
        holder.body.setText(item.summary);
        holder.body.setVisibility(item.summary.isEmpty() ? View.GONE : View.VISIBLE);
        row.setContentDescription(item.summary.isEmpty()
                ? item.title : item.title + ", " + item.summary);
        row.setOnClickListener(null);
        bindNavigation(holder, row, item);
    }

    private void bindNavigation(RecyclerView.ViewHolder holder, SettingsRowView row,
                                SettingsItem item) {
        row.setOnKeyListener((view, keyCode, event) -> handleKey(holder, row, item, keyCode, event));
        row.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                FocusAnimationHelper.animateFocusIn(view, FocusAnimationHelper.Profile.ROW);
                ensureFullyVisible(view);
            } else {
                FocusAnimationHelper.animateFocusOut(view, FocusAnimationHelper.Profile.ROW);
            }
        });
        if (item.key.equals(pendingFocusKey)) {
            pendingFocusKey = null;
            row.post(row::requestFocus);
        }
    }

    private void bindSegments(RowHolder holder, SettingsItem item) {
        holder.segments.removeAllViews();
        if (item.type != SettingsItem.Type.SEGMENTED) {
            holder.segments.setVisibility(View.GONE);
            return;
        }
        holder.segments.setVisibility(View.VISIBLE);
        for (int i = 0; i < item.segments.size(); i++) {
            TextView segment = (TextView) LayoutInflater.from(holder.row.getContext())
                    .inflate(R.layout.item_settings_segment, holder.segments, false);
            segment.setText(item.segments.get(i));
            segment.setSelected(i == item.selectedSegment);
            segment.setFocusable(false);
            holder.segments.addView(segment);
        }
    }

    private boolean handleKey(RecyclerView.ViewHolder holder, SettingsRowView row,
                              SettingsItem item, int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        RecyclerView recyclerView = parentRecyclerView(row);
        if (recyclerView == null) return false;
        int position = holder.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION) return true;
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            int direction = keyCode == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1;
            int target = findInteractivePosition(position + direction, direction);
            if (target != RecyclerView.NO_POSITION) {
                focusPosition(recyclerView, target);
            } else if (direction < 0 && topBoundaryFocusTarget != null) {
                topBoundaryFocusTarget.requestFocus();
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (item.type == SettingsItem.Type.SEGMENTED && item.selectedSegment > 0) {
                listener.onSegmentChanged(item, item.selectedSegment - 1);
                return true;
            }
            return listener.onMoveLeft(item);
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                && item.type == SettingsItem.Type.SEGMENTED
                && item.selectedSegment < item.segments.size() - 1) {
            listener.onSegmentChanged(item, item.selectedSegment + 1);
            return true;
        }
        return false;
    }

    private int findPositionByKey(String key) {
        if (key == null) return RecyclerView.NO_POSITION;
        for (int i = 0; i < items.size(); i++) {
            if (key.equals(items.get(i).key) && items.get(i).isFocusable()) return i;
        }
        return RecyclerView.NO_POSITION;
    }

    private int findInteractivePosition(int start, int direction) {
        for (int position = start; position >= 0 && position < items.size(); position += direction) {
            if (items.get(position).isFocusable()) return position;
        }
        return RecyclerView.NO_POSITION;
    }

    private void focusPosition(RecyclerView recyclerView, int position) {
        RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(position);
        if (holder != null && holder.itemView.requestFocus()) return;
        pendingFocusKey = items.get(position).key;
        recyclerView.scrollToPosition(position);
        recyclerView.post(() -> {
            RecyclerView.ViewHolder next = recyclerView.findViewHolderForAdapterPosition(position);
            if (next != null) next.itemView.requestFocus();
        });
    }

    private void ensureFullyVisible(View row) {
        View parent = (View) row.getParent();
        if (!(parent instanceof RecyclerView)) return;
        Rect rect = new Rect(0, 0, row.getWidth(), row.getHeight());
        ((RecyclerView) parent).requestChildRectangleOnScreen(row, rect, false);
    }

    private RecyclerView parentRecyclerView(View row) {
        return row.getParent() instanceof RecyclerView ? (RecyclerView) row.getParent() : null;
    }

    private void updateContentDescription(SettingsRowView row, SettingsItem item) {
        StringBuilder description = new StringBuilder(item.title);
        if (!item.summary.isEmpty()) description.append(", ").append(item.summary);
        if (item.type == SettingsItem.Type.TOGGLE) {
            description.append(", ").append(item.checked
                    ? row.getContext().getString(R.string.settings_on)
                    : row.getContext().getString(R.string.settings_off));
        } else if (!item.value.isEmpty()) {
            description.append(", ").append(item.value);
        } else if (item.type == SettingsItem.Type.SEGMENTED
                && item.selectedSegment >= 0 && item.selectedSegment < item.segments.size()) {
            description.append(", ").append(item.segments.get(item.selectedSegment));
        }
        row.setContentDescription(description);
    }

    private static final class SectionHolder extends RecyclerView.ViewHolder {
        final TextView title;

        SectionHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tvSettingsSectionTitle);
        }
    }

    private static final class RowHolder extends RecyclerView.ViewHolder {
        final SettingsRowView row;
        final TextView title;
        final TextView summary;
        final TextView value;
        final ImageView chevron;
        final ImageView toggle;
        final LinearLayout segments;

        RowHolder(SettingsRowView itemView) {
            super(itemView);
            row = itemView;
            title = itemView.findViewById(R.id.tvSettingsRowTitle);
            summary = itemView.findViewById(R.id.tvSettingsRowSummary);
            value = itemView.findViewById(R.id.tvSettingsRowValue);
            chevron = itemView.findViewById(R.id.ivSettingsRowChevron);
            toggle = itemView.findViewById(R.id.ivSettingsRowToggle);
            segments = itemView.findViewById(R.id.layoutSettingsSegments);
        }
    }

    private static final class InfoHolder extends RecyclerView.ViewHolder {
        final SettingsRowView row;
        final TextView title;
        final TextView body;

        InfoHolder(SettingsRowView itemView) {
            super(itemView);
            row = itemView;
            title = itemView.findViewById(R.id.tvSettingsInfoTitle);
            body = itemView.findViewById(R.id.tvSettingsInfoBody);
        }
    }
}
