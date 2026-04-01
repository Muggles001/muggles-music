package top.boluofan.musictv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import top.boluofan.musictv.R;
import top.boluofan.musictv.api.model.Playlist;

import java.util.ArrayList;
import java.util.List;

public class SquarePlaylistAdapter extends RecyclerView.Adapter<SquarePlaylistAdapter.ViewHolder> {

    private List<Playlist> playlists = new ArrayList<>();
    private OnItemClickListener listener;
    private int selectedPosition = 0;

    public interface OnItemClickListener {
        void onItemClick(Playlist playlist);
    }

    public void setData(List<Playlist> playlists) {
        this.playlists = playlists != null ? playlists : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setSelection(int position) {
        int oldPos = selectedPosition;
        selectedPosition = position;
        if (oldPos != selectedPosition) {
            notifyItemChanged(oldPos);
            notifyItemChanged(selectedPosition);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_playlist_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Playlist playlist = playlists.get(position);
        
        holder.tvName.setText(playlist.getName());
        
        String picUrl = playlist.getPicUrl();
        if (picUrl != null && !picUrl.isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(picUrl)
                    .placeholder(R.drawable.ic_playlist_music)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .centerCrop()
                    .into(holder.ivCover);
        } else {
            holder.ivCover.setImageResource(R.drawable.ic_playlist_music);
        }

        String playCount = playlist.getFormattedPlayCount();
        if (playCount != null && !playCount.isEmpty()) {
            holder.tvPlayCount.setVisibility(View.VISIBLE);
            holder.tvPlayCount.setText(playCount);
        } else {
            holder.tvPlayCount.setVisibility(View.GONE);
        }

        String creator = playlist.getCreator();
        if (creator != null && !creator.isEmpty()) {
            holder.tvCreator.setVisibility(View.VISIBLE);
            holder.tvCreator.setText(creator);
        } else {
            holder.tvCreator.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            int oldPos = selectedPosition;
            int newPos = holder.getAdapterPosition();
            if (newPos == RecyclerView.NO_POSITION) return;

            selectedPosition = newPos;
            if (oldPos != selectedPosition) {
                notifyItemChanged(oldPos);
                notifyItemChanged(selectedPosition);
            }
            if (listener != null) listener.onItemClick(playlist);
        });

        holder.itemView.setSelected(selectedPosition == position);
    }

    @Override
    public int getItemCount() {
        return playlists.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivCover;
        TextView tvPlayCount;
        TextView tvName;
        TextView tvCreator;

        ViewHolder(View itemView) {
            super(itemView);
            ivCover = itemView.findViewById(R.id.ivCover);
            tvPlayCount = itemView.findViewById(R.id.tvPlayCount);
            tvName = itemView.findViewById(R.id.tvName);
            tvCreator = itemView.findViewById(R.id.tvCreator);
        }
    }
}
