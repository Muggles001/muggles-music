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
        selectedPosition = this.playlists.isEmpty()
                ? RecyclerView.NO_POSITION
                : Math.min(Math.max(0, selectedPosition), this.playlists.size() - 1);
        notifyDataSetChanged();
    }
    
    public void addData(List<Playlist> newPlaylists) {
        if (newPlaylists == null || newPlaylists.isEmpty()) return;
        int startPosition = playlists.size();
        playlists.addAll(newPlaylists);
        if (selectedPosition == RecyclerView.NO_POSITION) selectedPosition = 0;
        notifyItemRangeInserted(startPosition, newPlaylists.size());
    }

    public void setShowFooter(boolean show) {
        if (showFooter != show) {
            int footerPosition = playlists.size();
            showFooter = show;
            if (show) {
                notifyItemInserted(footerPosition);
            } else {
                notifyItemRemoved(footerPosition);
            }
        }
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setSelection(int position) {
        int oldPos = selectedPosition;
        selectedPosition = position >= 0 && position < playlists.size()
                ? position : RecyclerView.NO_POSITION;
        if (oldPos != selectedPosition) {
            if (oldPos >= 0 && oldPos < playlists.size()) notifyItemChanged(oldPos);
            if (selectedPosition >= 0) notifyItemChanged(selectedPosition);
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
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_playlist_info, parent, false);
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
        
        viewHolder.itemView.post(() -> {
            int width = viewHolder.itemView.getWidth();
            if (width > 0) {
                View cover = viewHolder.itemView.findViewById(R.id.ivCover);
                cover.getLayoutParams().height = width;
                cover.requestLayout();
            }
        });
        
        String picUrl = playlist.getPicUrl();
        if (picUrl != null && !picUrl.isEmpty()) {
            Glide.with(viewHolder.itemView.getContext())
                    .load(picUrl)
                    .placeholder(R.drawable.ic_playlist_music)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .fitCenter()
                    .override(300, 300)
                    .into(viewHolder.ivCover);
        } else {
            viewHolder.ivCover.setImageResource(R.drawable.ic_playlist_music);
        }

        String creator = playlist.getCreator();
        int songCount = playlist.getSongCount();
        
        boolean hasCreator = creator != null && !creator.isEmpty();
        boolean hasSongCount = songCount > 0;
        
        if (hasCreator || hasSongCount) {
            viewHolder.tvCreator.setVisibility(View.VISIBLE);
            viewHolder.tvSongCount.setVisibility(View.VISIBLE);
            
            String creatorText = hasCreator ? creator : "";
            viewHolder.tvCreator.setText(creatorText);
            viewHolder.tvCreator.setHint(hasCreator ? null : " ");
            
            if (hasSongCount) {
                viewHolder.tvSongCount.setText(songCount + "首");
            } else {
                viewHolder.tvSongCount.setText(" ");
            }
        } else {
            viewHolder.tvCreator.setVisibility(View.GONE);
            viewHolder.tvSongCount.setVisibility(View.GONE);
        }

        viewHolder.tvInfo.setVisibility(View.GONE);

        viewHolder.itemView.setOnClickListener(v -> {
            int oldPos = selectedPosition;
            int newPos = viewHolder.getAdapterPosition();
            if (newPos == RecyclerView.NO_POSITION) return;

            selectedPosition = newPos;
            if (oldPos != selectedPosition) {
                if (oldPos >= 0 && oldPos < playlists.size()) notifyItemChanged(oldPos);
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
        TextView tvName;
        TextView tvInfo;
        TextView tvCreator;
        TextView tvSongCount;

        ViewHolder(View itemView) {
            super(itemView);
            ivCover = itemView.findViewById(R.id.ivCover);
            tvName = itemView.findViewById(R.id.tvName);
            tvInfo = itemView.findViewById(R.id.tvInfo);
            tvCreator = itemView.findViewById(R.id.tvCreator);
            tvSongCount = itemView.findViewById(R.id.tvSongCount);
        }
    }

    static class FooterViewHolder extends RecyclerView.ViewHolder {
        FooterViewHolder(View itemView) {
            super(itemView);
        }
    }
}
