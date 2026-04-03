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

public class SquarePlaylistAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_ITEM = 0;
    private static final int TYPE_FOOTER = 1;

    private List<Playlist> playlists = new ArrayList<>();
    private OnItemClickListener listener;
    private int selectedPosition = 0;
    private boolean showFooter = false;

    public interface OnItemClickListener {
        void onItemClick(Playlist playlist);
    }

    public void setData(List<Playlist> playlists) {
        this.playlists = playlists != null ? playlists : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setShowFooter(boolean show) {
        if (showFooter != show) {
            showFooter = show;
            if (show) {
                notifyItemInserted(getItemCount());
            } else {
                notifyItemRemoved(getItemCount());
            }
        }
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

    @Override
    public int getItemViewType(int position) {
        if (showFooter && position == getItemCount() - 1) {
            return TYPE_FOOTER;
        }
        return TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_FOOTER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_loading_footer, parent, false);
            return new FooterViewHolder(view);
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_playlist_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof FooterViewHolder) {
            return;
        }
        ViewHolder viewHolder = (ViewHolder) holder;
        Playlist playlist = playlists.get(position);
        
        viewHolder.tvName.setText(playlist.getName());
        
        String picUrl = playlist.getPicUrl();
        if (picUrl != null && !picUrl.isEmpty()) {
            Glide.with(viewHolder.itemView.getContext())
                    .load(picUrl)
                    .placeholder(R.drawable.ic_playlist_music)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .centerCrop()
                    .into(viewHolder.ivCover);
        } else {
            viewHolder.ivCover.setImageResource(R.drawable.ic_playlist_music);
        }

        String playCount = playlist.getFormattedPlayCount();
        if (playCount != null && !playCount.isEmpty()) {
            viewHolder.tvPlayCount.setVisibility(View.VISIBLE);
            viewHolder.tvPlayCount.setText(playCount);
        } else {
            viewHolder.tvPlayCount.setVisibility(View.GONE);
        }

        String creator = playlist.getCreator();
        if (creator != null && !creator.isEmpty()) {
            viewHolder.tvCreator.setVisibility(View.VISIBLE);
            viewHolder.tvCreator.setText(creator);
        } else {
            viewHolder.tvCreator.setVisibility(View.GONE);
        }

        viewHolder.itemView.setOnClickListener(v -> {
            int oldPos = selectedPosition;
            int newPos = viewHolder.getAdapterPosition();
            if (newPos == RecyclerView.NO_POSITION) return;

            selectedPosition = newPos;
            if (oldPos != selectedPosition) {
                notifyItemChanged(oldPos);
                notifyItemChanged(selectedPosition);
            }
            if (listener != null) listener.onItemClick(playlist);
        });

        viewHolder.itemView.setSelected(selectedPosition == position);
    }

    @Override
    public int getItemCount() {
        int count = playlists.size();
        if (showFooter) count++;
        return count;
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

    static class FooterViewHolder extends RecyclerView.ViewHolder {
        FooterViewHolder(View itemView) {
            super(itemView);
        }
    }
}
