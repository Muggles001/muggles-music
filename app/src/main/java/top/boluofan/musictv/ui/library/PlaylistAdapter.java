package top.boluofan.musictv.ui.library;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.KeyEvent;
import android.widget.ImageButton;
import android.widget.TextView;
import top.boluofan.musictv.R;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import top.boluofan.musictv.ui.common.FocusAnimationHelper;


public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.ViewHolder> {

    private List<LibraryPlaylistRow> playlists = new ArrayList<>();
    private List<LibraryPlaylistRow> visiblePlaylists = new ArrayList<>();
    private OnItemClickListener listener;
    private int selectedPosition = 0;
    private int pageSize = 10;
    private int currentPage = 0;
    private int nextFocusLeftId = View.NO_ID;
    private boolean managementMode;
    private OnManageClickListener manageListener;

    public interface OnItemClickListener {
        void onItemClick(LibraryPlaylistRow playlist);
    }

    public interface OnManageClickListener {
        void onRename(LibraryPlaylistRow playlist);
        void onDelete(LibraryPlaylistRow playlist);
    }

    public void setData(List<LibraryPlaylistRow> playlists) {
        this.playlists = playlists == null ? new ArrayList<>() : new ArrayList<>(playlists);
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

    public void setManagementMode(boolean managementMode) {
        if (this.managementMode == managementMode) return;
        this.managementMode = managementMode;
        notifyDataSetChanged();
    }

    public boolean isManagementMode() {
        return managementMode;
    }

    public void setOnManageClickListener(OnManageClickListener listener) {
        manageListener = listener;
    }

    public int getPageCount() {
        return pageSize <= 0 ? 1 : Math.max(1, (playlists.size() + pageSize - 1) / pageSize);
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public int findGlobalPositionById(String playlistId) {
        if (playlistId == null) return RecyclerView.NO_POSITION;
        for (int i = 0; i < playlists.size(); i++) {
            if (playlistId.equals(playlists.get(i).getPlaylistId())) return i;
        }
        return RecyclerView.NO_POSITION;
    }

    public int getPageSize() {
        return pageSize;
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
        LibraryPlaylistRow row = visiblePlaylists.get(position);
        String name = row.getName();
        holder.tvName.setText(name.equals("All Songs") ? "所有歌曲" : name);
        holder.tvIndex.setText(String.valueOf(currentPage * pageSize + position + 1));
        holder.tvCount.setText(row.getSongCount() + " 首歌曲");
        holder.btnRename.setVisibility(managementMode ? View.VISIBLE : View.GONE);
        holder.btnDelete.setVisibility(managementMode ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> {
            int oldPos = selectedPosition;
            int newPos = holder.getAdapterPosition();
            if (newPos == RecyclerView.NO_POSITION) return;
            
            selectedPosition = newPos;
            if (oldPos != selectedPosition) {
                notifyItemChanged(oldPos);
                notifyItemChanged(selectedPosition);
            }
            if (managementMode) {
                if (manageListener != null) manageListener.onRename(row);
                return;
            }
            if (listener != null) listener.onItemClick(row);
        });
        
        // Simple selection visual
        holder.itemView.setSelected(selectedPosition == position);
        holder.itemView.setNextFocusRightId(managementMode
                ? R.id.btnPlaylistRename : View.NO_ID);
        holder.btnRename.setNextFocusLeftId(R.id.item_playlist_root);
        holder.btnRename.setNextFocusRightId(R.id.btnPlaylistDelete);
        holder.btnDelete.setNextFocusLeftId(R.id.btnPlaylistRename);
        holder.btnDelete.setNextFocusRightId(R.id.btnPlaylistDelete);
        holder.btnRename.setOnClickListener(v -> {
            if (manageListener != null) manageListener.onRename(row);
        });
        holder.btnDelete.setOnClickListener(v -> {
            if (manageListener != null) manageListener.onDelete(row);
        });
        holder.btnRename.setOnKeyListener((v, keyCode, event) ->
                moveManagementFocus(holder, v, keyCode, event, R.id.btnPlaylistRename));
        holder.btnDelete.setOnKeyListener((v, keyCode, event) ->
                moveManagementFocus(holder, v, keyCode, event, R.id.btnPlaylistDelete));

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

    private boolean moveManagementFocus(ViewHolder holder, View source, int keyCode,
                                        KeyEvent event, int targetId) {
        if (event.getAction() != KeyEvent.ACTION_DOWN
                || (keyCode != KeyEvent.KEYCODE_DPAD_UP
                && keyCode != KeyEvent.KEYCODE_DPAD_DOWN)) {
            return false;
        }
        int position = holder.getAdapterPosition();
        if (position == RecyclerView.NO_POSITION) return true;
        int targetPosition = position + (keyCode == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
        if (targetPosition < 0) {
            View manage = source.getRootView().findViewById(R.id.btnManagePlaylists);
            return manage == null || manage.requestFocus();
        }
        if (targetPosition >= getItemCount()) {
            View root = source.getRootView();
            View pager = root.findViewById(R.id.btnLibraryNextPage);
            if (pager == null || !pager.isEnabled()) {
                pager = root.findViewById(R.id.btnLibraryPrevPage);
            }
            return pager == null || pager.requestFocus();
        }
        View parent = (View) holder.itemView.getParent();
        if (!(parent instanceof RecyclerView)) return true;
        RecyclerView recyclerView = (RecyclerView) parent;
        recyclerView.scrollToPosition(targetPosition);
        recyclerView.post(() -> {
            RecyclerView.ViewHolder target =
                    recyclerView.findViewHolderForAdapterPosition(targetPosition);
            if (target == null) return;
            View control = target.itemView.findViewById(targetId);
            if (control != null) control.requestFocus();
        });
        return true;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvCount;
        TextView tvIndex;
        ImageButton btnRename;
        ImageButton btnDelete;

        ViewHolder(View itemView) {
            super(itemView);
            FocusAnimationHelper.applyFocusAnimation(itemView);
            tvIndex = itemView.findViewById(R.id.tvIndex);
            tvName = itemView.findViewById(R.id.tvName);
            tvCount = itemView.findViewById(R.id.tvCount);
            btnRename = itemView.findViewById(R.id.btnPlaylistRename);
            btnDelete = itemView.findViewById(R.id.btnPlaylistDelete);
            FocusAnimationHelper.applyFocusAnimation(
                    btnRename, FocusAnimationHelper.Profile.ICON);
            FocusAnimationHelper.applyFocusAnimation(
                    btnDelete, FocusAnimationHelper.Profile.ICON);
        }
    }
}
