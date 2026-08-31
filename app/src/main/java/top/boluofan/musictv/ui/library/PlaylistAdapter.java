package top.boluofan.musictv.ui.library;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import top.boluofan.musictv.R;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import top.boluofan.musictv.ui.common.FocusAnimationHelper;


public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.ViewHolder> {

    private List<String> playlists = new ArrayList<>();
    private List<String> visiblePlaylists = new ArrayList<>();
    private Map<String, List<String>> data;
    private OnItemClickListener listener;
    private int selectedPosition = 0;
    private int pageSize = 10;
    private int currentPage = 0;
    private int nextFocusLeftId = View.NO_ID;

    public interface OnItemClickListener {
        void onItemClick(String playlistName);
    }

    public void setData(Map<String, List<String>> data) {
        this.data = data;
        this.playlists = new ArrayList<>(data.keySet());
        currentPage = 0;
        selectedPosition = 0;
        updateVisiblePlaylists();
        notifyDataSetChanged();
    }

    public void setPageSize(int pageSize) {
        this.pageSize = Math.max(1, pageSize);
        currentPage = Math.min(currentPage, Math.max(0, getPageCount() - 1));
        updateVisiblePlaylists();
        notifyDataSetChanged();
    }

    public void setNextFocusLeftId(int id) {
        nextFocusLeftId = id;
    }

    public int getPageCount() {
        return pageSize <= 0 ? 1 : Math.max(1, (playlists.size() + pageSize - 1) / pageSize);
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void setPage(int page) {
        int nextPage = Math.max(0, Math.min(page, getPageCount() - 1));
        if (nextPage == currentPage && !visiblePlaylists.isEmpty()) return;
        currentPage = nextPage;
        selectedPosition = 0;
        updateVisiblePlaylists();
        notifyDataSetChanged();
    }

    private void updateVisiblePlaylists() {
        visiblePlaylists.clear();
        int from = Math.min(currentPage * pageSize, playlists.size());
        int to = Math.min(from + pageSize, playlists.size());
        if (from < to) visiblePlaylists.addAll(playlists.subList(from, to));
    }
    
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_library_playlist, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String name = visiblePlaylists.get(position);
        List<String> songs = data.get(name);
        holder.tvName.setText(name.equals("All Songs") ? "所有歌曲" : name);
        holder.tvIndex.setText(String.valueOf(currentPage * pageSize + position + 1));
        holder.tvCount.setText((songs != null ? songs.size() : 0) + " 首歌曲");

        holder.itemView.setOnClickListener(v -> {
            int oldPos = selectedPosition;
            int newPos = holder.getAdapterPosition();
            if (newPos == RecyclerView.NO_POSITION) return;
            
            selectedPosition = newPos;
            if (oldPos != selectedPosition) {
                notifyItemChanged(oldPos);
                notifyItemChanged(selectedPosition);
            }
            if (listener != null) listener.onItemClick(name);
        });
        
        // Simple selection visual
        holder.itemView.setSelected(selectedPosition == position);

        // Let RecyclerView move between rows normally. Only the final row on
        // the page hands the remote to the paging controls.
        holder.itemView.setNextFocusDownId(
                position == getItemCount() - 1 ? R.id.btnLibraryNextPage : View.NO_ID);
        holder.itemView.setNextFocusLeftId(nextFocusLeftId);
    }

    @Override
    public int getItemCount() {
        return visiblePlaylists.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvCount;
        TextView tvIndex;

        ViewHolder(View itemView) {
            super(itemView);
            FocusAnimationHelper.applyFocusAnimation(itemView);
            tvIndex = itemView.findViewById(R.id.tvIndex);
            tvName = itemView.findViewById(R.id.tvName);
            tvCount = itemView.findViewById(R.id.tvCount);
        }
    }
}
