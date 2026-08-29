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
import top.boluofan.musictv.util.FocusAnimationHelper;

import java.util.ArrayList;
import java.util.List;

public class SquarePlaylistAdapter extends RecyclerView.Adapter<SquarePlaylistAdapter.ViewHolder> {

    private List<Playlist> playlists = new ArrayList<>();
    private OnItemClickListener listener;

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

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_playlist_info, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int position) {
        Playlist playlist = playlists.get(position);
        
        viewHolder.tvName.setText(playlist.getName());
        
        String picUrl = playlist.getPicUrl();
        if (picUrl != null && !picUrl.isEmpty()) {
            Glide.with(viewHolder.itemView.getContext())
                    .load(picUrl)
                    .placeholder(R.drawable.ic_playlist_music)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .centerCrop()
                    .override(300, 300)
                    .into(viewHolder.ivCover);
        } else {
            Glide.with(viewHolder.itemView.getContext()).clear(viewHolder.ivCover);
            viewHolder.ivCover.setImageResource(R.drawable.ic_playlist_music);
        }

        String creator = playlist.getCreator();
        int songCount = playlist.getSongCount();
        
        boolean hasCreator = creator != null && !creator.isEmpty();
        boolean hasSongCount = songCount > 0;
        
        viewHolder.tvCreator.setText(hasCreator ? creator : "");
        viewHolder.tvSongCount.setText(hasSongCount ? songCount + "首" : "");

        viewHolder.itemView.setOnClickListener(v -> {
            int newPos = viewHolder.getAdapterPosition();
            if (newPos == RecyclerView.NO_POSITION) return;
            if (listener != null) listener.onItemClick(playlist);
        });

        viewHolder.itemView.setSelected(false);
    }

    @Override
    public int getItemCount() {
        return playlists.size();
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        Glide.with(holder.itemView.getContext()).clear(holder.ivCover);
        holder.itemView.clearFocus();
        holder.itemView.animate().cancel();
        holder.itemView.setScaleX(1f);
        holder.itemView.setScaleY(1f);
        holder.itemView.setTranslationZ(0f);
        holder.itemView.setSelected(false);
        super.onViewRecycled(holder);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivCover;
        TextView tvName;
        TextView tvCreator;
        TextView tvSongCount;

        ViewHolder(View itemView) {
            super(itemView);
            FocusAnimationHelper.applyFocusAnimation(itemView, FocusAnimationHelper.Profile.CARD);
            ivCover = itemView.findViewById(R.id.ivCover);
            tvName = itemView.findViewById(R.id.tvName);
            tvCreator = itemView.findViewById(R.id.tvCreator);
            tvSongCount = itemView.findViewById(R.id.tvSongCount);
        }
    }

}
