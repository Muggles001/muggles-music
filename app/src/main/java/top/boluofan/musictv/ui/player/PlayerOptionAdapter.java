package top.boluofan.musictv.ui.player;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import top.boluofan.musictv.R;


public class PlayerOptionAdapter extends RecyclerView.Adapter<PlayerOptionAdapter.ViewHolder> {

    private final List<String> options;
    private final java.util.Set<String> disabledOptions = new java.util.HashSet<>();
    private final OnOptionClickListener listener;

    public interface OnOptionClickListener {
        void onOptionClick(String option, int position);
    }

    public PlayerOptionAdapter(List<String> options, OnOptionClickListener listener) {
        this.options = options;
        this.listener = listener;
    }

    public void setOptionDisabled(String option, boolean disabled) {
        if (disabled) {
            disabledOptions.add(option);
        } else {
            disabledOptions.remove(option);
        }
        notifyDataSetChanged();
    }

    public boolean isOptionDisabled(String option) {
        return disabledOptions.contains(option);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_player_option, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String option = options.get(position);
        boolean isDisabled = disabledOptions.contains(option);
        
        holder.tvOptionName.setText(option);
        holder.itemView.setEnabled(!isDisabled);
        holder.itemView.setAlpha(isDisabled ? 0.3f : 1.0f);
        holder.itemView.setFocusable(!isDisabled);
        holder.itemView.setNextFocusUpId(
                position == 0 ? R.id.btnOptionMenuBack : View.NO_ID
        );
        holder.itemView.setOnKeyListener((view, keyCode, event) -> {
            if (position == 0
                    && keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP
                    && event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                View back = view.getRootView().findViewById(R.id.btnOptionMenuBack);
                return back != null && back.requestFocus();
            }
            return false;
        });

        holder.itemView.setOnClickListener(v -> {
            if (listener != null && !isDisabled) {
                listener.onOptionClick(option, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return options.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvOptionName;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvOptionName = itemView.findViewById(R.id.tvOptionName);
        }
    }
}
