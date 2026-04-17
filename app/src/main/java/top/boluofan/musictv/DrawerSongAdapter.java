package top.boluofan.musictv;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.AnimationDrawable;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import com.google.gson.JsonObject;


public class DrawerSongAdapter extends RecyclerView.Adapter<DrawerSongAdapter.ViewHolder> {

    public static class DrawerSongItem {
        public String title;
        public String artist;
        public String coverUrl;
        public String source;
        public String songmid;

        public DrawerSongItem(String title, String artist, String coverUrl, String source, String songmid) {
            this.title = title;
            this.artist = artist;
            this.coverUrl = coverUrl;
            this.source = source;
            this.songmid = songmid;
        }
    }

    private List<DrawerSongItem> songs = new ArrayList<>();
    private OnItemClickListener listener;
    private int playingIndex = -1;
    private boolean isPlayerPlaying = false;
    private Context context;
    private top.boluofan.musictv.api.LxApiService apiService;
    private String baseUrl;

    private Map<String, String> coverUrlCache = new HashMap<>();
    private Map<String, String> artistCache = new HashMap<>();

    public DrawerSongAdapter(Context context) {
        this.context = context;
        this.apiService = top.boluofan.musictv.api.LxRetrofitClient.getApiService(context);
        this.baseUrl = top.boluofan.musictv.api.LxRetrofitClient.getServerUrl(context);
    }

    public interface OnItemClickListener {
        void onItemClick(String song, int position);
    }

    public void setSongs(List<DrawerSongItem> songs) {
        this.songs = songs;
        notifyDataSetChanged();
    }

    public void setPlayingIndex(int index) {
        int oldIndex = this.playingIndex;
        this.playingIndex = index;
        if (oldIndex != -1) notifyItemChanged(oldIndex);
        if (index != -1) notifyItemChanged(index);
    }

    public void setPlayerPlaying(boolean isPlaying) {
        if (this.isPlayerPlaying == isPlaying) return;
        this.isPlayerPlaying = isPlaying;
        if (playingIndex != -1) notifyItemChanged(playingIndex);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_drawer_song, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DrawerSongItem song = songs.get(position);
        holder.tvTitle.setText(song.title);
        
        if (song.artist != null && !song.artist.isEmpty() && !"未知歌手".equals(song.artist)) {
            holder.tvArtist.setText(song.artist);
        } else {
            holder.tvArtist.setText("加载中...");
        }

        boolean isPlaying = (position == playingIndex);
        
        // Explicitly handle focus visual changes with animation
        holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                holder.tvTitle.setTextColor(Color.BLACK);
                holder.tvArtist.setTextColor(Color.parseColor("#666666"));
                holder.ivArrow.setAlpha(1.0f);
                v.animate().scaleX(1.03f).scaleY(1.03f).setDuration(200).start();
                v.setElevation(20f);
            } else {
                if (isPlaying) {
                    holder.tvTitle.setTextColor(Color.parseColor("#26a2ff"));
                } else {
                    holder.tvTitle.setTextColor(Color.WHITE);
                }
                holder.tvArtist.setTextColor(Color.parseColor("#B3FFFFFF"));
                holder.ivArrow.setAlpha(0f);
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start();
                v.setElevation(4f);
            }
        });

        // Initial state sync
        if (holder.itemView.isFocused()) {
            holder.tvTitle.setTextColor(Color.BLACK);
            holder.tvArtist.setTextColor(Color.parseColor("#666666"));
            holder.ivArrow.setAlpha(1.0f);
            holder.itemView.setScaleX(1.05f);
            holder.itemView.setScaleY(1.05f);
        } else {
            if (isPlaying) {
                holder.tvTitle.setTextColor(Color.parseColor("#26a2ff"));
            } else {
                holder.tvTitle.setTextColor(Color.WHITE);
            }
            holder.tvArtist.setTextColor(Color.parseColor("#B3FFFFFF"));
            holder.ivArrow.setAlpha(0f);
            holder.itemView.setScaleX(1.0f);
            holder.itemView.setScaleY(1.0f);
        }

        // Show equalizer if playing
        if (isPlaying) {
            holder.ivEqualizer.setVisibility(View.VISIBLE);
            if (holder.viewPlayingOverlay != null) holder.viewPlayingOverlay.setVisibility(View.VISIBLE);
            if (isPlayerPlaying) {
                holder.ivEqualizer.setImageResource(R.drawable.anim_equalizer);
                if (holder.ivEqualizer.getDrawable() instanceof AnimationDrawable) {
                    ((AnimationDrawable) holder.ivEqualizer.getDrawable()).start();
                }
            } else {
                holder.ivEqualizer.setImageResource(R.drawable.ic_equalizer);
            }
        } else {
            holder.ivEqualizer.setVisibility(View.GONE);
            if (holder.viewPlayingOverlay != null) holder.viewPlayingOverlay.setVisibility(View.GONE);
        }

        holder.itemView.setFocusable(true);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(song.title, position);
        });

        // Load cover
        Glide.with(context).clear(holder.ivCover);
        
        if (song.coverUrl != null && !song.coverUrl.isEmpty()) {
            loadCover(song.coverUrl, holder.ivCover);
        } else {
            holder.ivCover.setImageResource(R.drawable.ic_cover_placeholder);
        }
    }

    private void loadCover(String url, ImageView target) {
        Glide.with(context)
            .load(url)
            .placeholder(R.drawable.ic_cover_placeholder)
            .error(R.drawable.ic_cover_placeholder)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .transform(new com.bumptech.glide.load.resource.bitmap.RoundedCorners(8))
            .into(target);
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivCover;
        TextView tvTitle;
        TextView tvArtist;
        ImageView ivEqualizer;
        ImageView ivArrow;
        View viewPlayingOverlay;

        ViewHolder(View itemView) {
            super(itemView);
            ivCover = itemView.findViewById(R.id.id_cover_under_overlay);
            tvTitle = itemView.findViewById(R.id.tvDrawerItemTitle);
            tvArtist = itemView.findViewById(R.id.tvDrawerItemArtist);
            ivEqualizer = itemView.findViewById(R.id.ivDrawerPlaying);
            ivArrow = itemView.findViewById(R.id.ivDrawerArrow);
            viewPlayingOverlay = itemView.findViewById(R.id.viewDrawerPlayingOverlay);
        }
    }
}
