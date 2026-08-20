package top.boluofan.musictv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import java.util.ArrayList;
import java.util.List;
import top.boluofan.musictv.R;
import top.boluofan.musictv.api.model.HotListResponse;

public class HotPlaylistAdapter extends RecyclerView.Adapter<HotPlaylistAdapter.ViewHolder> {
    private List<HotListResponse.HotListItem> playlists = new ArrayList<>();
    private OnItemClickListener listener;
    private int selectedPosition = 0;

    public interface OnItemClickListener {
        void onItemClick(HotListResponse.HotListItem playlist, int position);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<HotListResponse.HotListItem> playlists) {
        this.playlists = playlists != null ? playlists : new ArrayList<>();
        selectedPosition = this.playlists.isEmpty()
                ? RecyclerView.NO_POSITION
                : Math.min(Math.max(0, selectedPosition), this.playlists.size() - 1);
        notifyDataSetChanged();
    }

    public void setSelection(int position) {
        int oldPosition = selectedPosition;
        selectedPosition = position >= 0 && position < playlists.size()
                ? position : RecyclerView.NO_POSITION;
        if (oldPosition >= 0 && oldPosition < playlists.size()) {
            notifyItemChanged(oldPosition);
        }
        if (selectedPosition >= 0 && selectedPosition != oldPosition) {
            notifyItemChanged(selectedPosition);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_playlist, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HotListResponse.HotListItem playlist = playlists.get(position);
        holder.bind(playlist);
        holder.itemView.setSelected(position == selectedPosition);
        
        holder.itemView.setOnClickListener(v -> {
            int newPosition = holder.getAdapterPosition();
            if (newPosition == RecyclerView.NO_POSITION) return;
            int oldPosition = selectedPosition;
            selectedPosition = newPosition;
            if (oldPosition >= 0 && oldPosition < playlists.size()) {
                notifyItemChanged(oldPosition);
            }
            if (selectedPosition != oldPosition) notifyItemChanged(selectedPosition);
            if (listener != null) {
                listener.onItemClick(playlist, selectedPosition);
            }
        });
    }

    @Override
    public int getItemCount() {
        return playlists.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivIcon;
        private final TextView tvName;
        private final TextView tvCount;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivIcon);
            tvName = itemView.findViewById(R.id.tvName);
            tvCount = itemView.findViewById(R.id.tvCount);
        }

        void bind(HotListResponse.HotListItem playlist) {
            tvName.setText(playlist.getName());
            tvCount.setText(playlist.getSongCount() + "首");
        }
    }
}
