package top.boluofan.musictv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.os.SystemClock;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import java.util.ArrayList;
import java.util.List;
import top.boluofan.musictv.R;
import top.boluofan.musictv.api.model.MusicInfo;
import top.boluofan.musictv.util.FocusAnimationHelper;

public class LxMusicAdapter extends RecyclerView.Adapter<LxMusicAdapter.ViewHolder> {
    private List<MusicInfo> songs = new ArrayList<>();
    private OnItemClickListener listener;
    private OnPlayClickListener playListener;
    private OnFullscreenClickListener fullscreenListener;
    private OnDeleteClickListener deleteListener;
    private OnFavClickListener favListener;
    private int playingIndex = -1;
    private boolean isPlaying = false;
    private boolean showDeleteButton = false;
    private boolean showFavButton = true;
    private int nextFocusDownId = View.NO_ID;
    private int nextFocusLeftId = View.NO_ID;
    private int indexOffset = 0;
    private OnFirstItemUpListener firstItemUpListener;
    private int lastFocusedPosition = RecyclerView.NO_POSITION;
    private int lastFocusedControlId = R.id.item_song_root;
    private int pendingFocusPosition = RecyclerView.NO_POSITION;
    private int pendingFocusControlId = R.id.item_song_root;
    private int focusMoveGeneration;
    private int actionFocusGeneration;
    private RecyclerView pendingPlaybackRecyclerView;
    private int pendingPlaybackPosition = RecyclerView.NO_POSITION;
    private int pendingPlaybackTargetId = View.NO_ID;
    private int pendingPlaybackGeneration;
    private int playbackFocusGeneration;
    private long pendingPlaybackExpiresAt;

    public interface OnItemClickListener {
        void onItemClick(MusicInfo song, int position);
    }

    public interface OnPlayClickListener {
        void onPlayClick(MusicInfo song, int position);
    }

    public interface OnFullscreenClickListener {
        void onFullscreenClick(MusicInfo song, int position);
    }

    public interface OnDeleteClickListener {
        void onDeleteClick(MusicInfo song, int position);
    }

    public interface OnFavClickListener {
        void onFavClick(MusicInfo song, int position);
    }

    public interface OnFirstItemUpListener {
        boolean onFirstItemUp();
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setOnPlayClickListener(OnPlayClickListener listener) {
        this.playListener = listener;
    }

    public void setOnFullscreenClickListener(OnFullscreenClickListener listener) {
        this.fullscreenListener = listener;
    }

    public void setOnDeleteClickListener(OnDeleteClickListener listener) {
        this.deleteListener = listener;
    }

    public void setOnFavClickListener(OnFavClickListener listener) {
        this.favListener = listener;
    }

    public void setShowDeleteButton(boolean show) {
        this.showDeleteButton = show;
    }

    public void setShowFavButton(boolean show) {
        this.showFavButton = show;
    }

    public void setNextFocusDownId(int id) {
        nextFocusDownId = id;
    }

    public void setNextFocusLeftId(int id) {
        nextFocusLeftId = id;
    }

    public void setOnFirstItemUpListener(OnFirstItemUpListener listener) {
        firstItemUpListener = listener;
    }

    public void setIndexOffset(int offset) {
        indexOffset = Math.max(0, offset);
    }

    /** Route a vertical key that bubbled past a row back through the list. */
    public boolean handleVerticalKey(View source, int keyCode) {
        if (source == null || (keyCode != android.view.KeyEvent.KEYCODE_DPAD_UP
                && keyCode != android.view.KeyEvent.KEYCODE_DPAD_DOWN)) {
            return false;
        }
        cancelDeferredActionFocusForNavigation();
        RecyclerView recyclerView = source instanceof RecyclerView
                ? (RecyclerView) source : findParentRecyclerView(source);
        if (recyclerView == null) return false;
        if (source == recyclerView) {
            if (getItemCount() == 0) return true;
            View focusedChild = recyclerView.findFocus();
            if (focusedChild != null && focusedChild != recyclerView) {
                RecyclerView.ViewHolder childHolder = recyclerView.findContainingViewHolder(focusedChild);
                if (childHolder != null) {
                    rememberFocusedPosition(childHolder.getAdapterPosition(), focusedChild.getId());
                    return moveVerticalWithinList(focusedChild, childHolder.getAdapterPosition(), keyCode);
                }
            }
            int position = pendingFocusPosition != RecyclerView.NO_POSITION
                    ? pendingFocusPosition : lastFocusedPosition;
            int controlId = pendingFocusPosition != RecyclerView.NO_POSITION
                    ? pendingFocusControlId : lastFocusedControlId;
            if (position == RecyclerView.NO_POSITION) {
                position = recyclerView.getChildCount() > 0
                        ? recyclerView.getChildAdapterPosition(recyclerView.getChildAt(0)) : 0;
            }
            if (position == RecyclerView.NO_POSITION) position = 0;
            return moveVerticalWithinList(recyclerView, position, controlId, keyCode);
        }
        RecyclerView.ViewHolder holder = recyclerView.findContainingViewHolder(source);
        if (holder == null) {
            int position = pendingFocusPosition != RecyclerView.NO_POSITION
                    ? pendingFocusPosition : lastFocusedPosition;
            if (position != RecyclerView.NO_POSITION) {
                int controlId = pendingFocusPosition != RecyclerView.NO_POSITION
                        ? pendingFocusControlId : lastFocusedControlId;
                return moveVerticalWithinList(recyclerView, position, controlId, keyCode);
            }
            return true;
        }
        rememberFocusedPosition(holder.getAdapterPosition(), source.getId());
        return moveVerticalWithinList(source, holder.getAdapterPosition(), keyCode);
    }

    public boolean hasFocusHistory() {
        return lastFocusedPosition != RecyclerView.NO_POSITION
                || pendingFocusPosition != RecyclerView.NO_POSITION;
    }

    public void setSongs(List<MusicInfo> songs) {
        cancelAllDeferredFocus();
        this.songs = songs != null ? songs : new ArrayList<>();
        if (playingIndex >= this.songs.size()) {
            playingIndex = -1;
        }
        if (lastFocusedPosition >= this.songs.size()) {
            lastFocusedPosition = RecyclerView.NO_POSITION;
            pendingFocusPosition = RecyclerView.NO_POSITION;
        }
        notifyDataSetChanged();
    }

    public void setPlayingIndex(int index) {
        int oldIndex = playingIndex;
        playingIndex = index >= 0 && index < songs.size() ? index : -1;
        if (oldIndex >= 0 && oldIndex < songs.size()) notifyItemChanged(oldIndex);
        if (playingIndex >= 0 && playingIndex != oldIndex) {
            notifyItemChanged(playingIndex);
        }
        restorePendingPlaybackFocus();
    }

    public void setPlayerPlaying(boolean playing) {
        if (isPlaying != playing) {
            isPlaying = playing;
            if (playingIndex >= 0 && playingIndex < songs.size()) {
                notifyItemChanged(playingIndex);
            }
        }
        restorePendingPlaybackFocus();
    }

    /** Reassert the play-button focus after MediaController redraws its row. */
    public void restorePendingPlaybackFocus() {
        if (pendingPlaybackRecyclerView == null
                || pendingPlaybackPosition == RecyclerView.NO_POSITION) return;
        final int generation = pendingPlaybackGeneration;
        if (generation == 0 || generation != playbackFocusGeneration) {
            clearPendingPlaybackFocus();
            return;
        }
        if (SystemClock.uptimeMillis() > pendingPlaybackExpiresAt) {
            clearPendingPlaybackFocus();
            return;
        }
        RecyclerView recyclerView = pendingPlaybackRecyclerView;
        int position = pendingPlaybackPosition;
        int targetId = pendingPlaybackTargetId;
        recyclerView.post(() -> {
            if (generation != playbackFocusGeneration
                    || generation != pendingPlaybackGeneration) {
                return;
            }
            if (SystemClock.uptimeMillis() > pendingPlaybackExpiresAt) {
                clearPendingPlaybackFocus();
                return;
            }
            RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(position);
            if (holder == null) return;
            if (!canRestorePlaybackFocus(recyclerView, holder.itemView)) {
                invalidatePendingPlaybackFocus();
                return;
            }
            View target = holder.itemView.findViewById(targetId);
            if (target != null && target.isShown()) target.requestFocus();
        });
    }

    public int getPlayingIndex() {
        return playingIndex;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_song, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MusicInfo song = songs.get(position);
        holder.bind(song, position == playingIndex, isPlaying, indexOffset);
        holder.itemView.setOnClickListener(v -> {
            int adapterPosition = holder.getAdapterPosition();
            if (adapterPosition == RecyclerView.NO_POSITION) return;
            RecyclerView recyclerView = findParentRecyclerView(v);
            rememberPendingPlaybackFocus(recyclerView, adapterPosition, R.id.item_song_root);
            if (listener != null) {
                listener.onItemClick(song, adapterPosition);
            }
            restoreFocusAfterAction(recyclerView, v, adapterPosition, R.id.item_song_root, true);
        });
        holder.btnPlay.setOnClickListener(v -> {
            int adapterPosition = holder.getAdapterPosition();
            if (adapterPosition == RecyclerView.NO_POSITION) return;
            // setPlayingIndex() may synchronously rebind this row, so the
            // RecyclerView must be captured before invoking the callback.
            RecyclerView recyclerView = findParentRecyclerView(v);
            rememberPendingPlaybackFocus(recyclerView, adapterPosition, R.id.btnItemPlay);
            if (playListener != null) {
                playListener.onPlayClick(song, adapterPosition);
            }
            restoreFocusAfterAction(recyclerView, v, adapterPosition, R.id.btnItemPlay, true);
        });
        holder.btnFullscreen.setOnClickListener(v -> {
            int adapterPosition = holder.getAdapterPosition();
            if (adapterPosition == RecyclerView.NO_POSITION) return;
            if (fullscreenListener != null) {
                fullscreenListener.onFullscreenClick(song, adapterPosition);
            }
        });
        holder.btnDelete.setVisibility(showDeleteButton ? View.VISIBLE : View.GONE);
        if (showDeleteButton && deleteListener != null) {
            holder.btnDelete.setOnClickListener(v -> {
                int adapterPosition = holder.getAdapterPosition();
                if (adapterPosition == RecyclerView.NO_POSITION) return;
                RecyclerView recyclerView = findParentRecyclerView(v);
                deleteListener.onDeleteClick(song, adapterPosition);
                restoreFocusAfterAction(recyclerView, v, adapterPosition, R.id.btnItemDelete, false);
            });
        } else {
            holder.btnDelete.setOnClickListener(null);
        }
        holder.btnFav.setVisibility(showFavButton ? View.VISIBLE : View.GONE);
        if (showFavButton && favListener != null) {
            holder.btnFav.setOnClickListener(v -> {
                int adapterPosition = holder.getAdapterPosition();
                if (adapterPosition == RecyclerView.NO_POSITION) return;
                RecyclerView recyclerView = findParentRecyclerView(v);
                favListener.onFavClick(song, adapterPosition);
                restoreFocusAfterAction(recyclerView, v, adapterPosition, R.id.btnItemFav, false);
            });
        } else {
            holder.btnFav.setOnClickListener(null);
        }
        configureRowFocus(holder, position);
    }

    private void configureRowFocus(ViewHolder holder, int position) {
        int downId = nextFocusDownId != View.NO_ID && position == getItemCount() - 1
                ? nextFocusDownId : View.NO_ID;
        // The old layout pointed every song row to a synthetic rvPlaylists id.
        // On pages without that view Android fell back to the primary rail.
        holder.itemView.setNextFocusLeftId(nextFocusLeftId);
        holder.itemView.setNextFocusDownId(downId);

        int rowLeftId = nextFocusLeftId != View.NO_ID ? nextFocusLeftId : R.id.item_song_root;
        holder.btnPlay.setNextFocusLeftId(rowLeftId);
        holder.btnPlay.setNextFocusRightId(R.id.btnItemFullscreen);
        holder.btnFullscreen.setNextFocusLeftId(R.id.btnItemPlay);
        holder.btnFullscreen.setNextFocusRightId(showFavButton
                ? R.id.btnItemFav : (showDeleteButton ? R.id.btnItemDelete : R.id.floatingPlayerContainer));
        holder.btnFav.setNextFocusLeftId(R.id.btnItemFullscreen);
        holder.btnFav.setNextFocusRightId(showDeleteButton ? R.id.btnItemDelete : R.id.floatingPlayerContainer);
        holder.btnDelete.setNextFocusLeftId(showFavButton
                ? R.id.btnItemFav : R.id.btnItemFullscreen);
        holder.btnDelete.setNextFocusRightId(R.id.floatingPlayerContainer);

        holder.btnPlay.setNextFocusDownId(downId);
        holder.btnFullscreen.setNextFocusDownId(downId);
        holder.btnFav.setNextFocusDownId(downId);
        holder.btnDelete.setNextFocusDownId(downId);

        View.OnKeyListener verticalNavigation = (view, keyCode, event) -> {
            if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) {
                return false;
            }
            if (isDirectionalKey(keyCode)) {
                cancelDeferredActionFocusForNavigation();
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT
                        || keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                    invalidatePendingMoveFocus();
                }
            }
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT
                    && (view == holder.itemView || view == holder.btnPlay)
                    && rowLeftId != View.NO_ID) {
                View target = view.getRootView().findViewById(rowLeftId);
                return target != null && target.requestFocus();
            }
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT
                    && isRightmostControl(view)) {
                View floating = view.getRootView().findViewById(R.id.floatingPlayerContainer);
                // FloatingPlayerWindow handles the visible case from the
                // Activity key dispatch. Consume the hidden case here so
                // Android cannot wrap focus to the previous song row.
                return floating == null || floating.getVisibility() != View.VISIBLE
                        || floating.getAlpha() < 0.1f;
            }
            if (keyCode != android.view.KeyEvent.KEYCODE_DPAD_UP
                    && keyCode != android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                return false;
            }
            rememberFocusedPosition(holder.getAdapterPosition(), view.getId());
            return moveVerticalWithinList(view, holder.getAdapterPosition(), keyCode);
        };
        holder.itemView.setOnKeyListener(verticalNavigation);
        holder.btnPlay.setOnKeyListener(verticalNavigation);
        holder.btnFullscreen.setOnKeyListener(verticalNavigation);
        holder.btnFav.setOnKeyListener(verticalNavigation);
        holder.btnDelete.setOnKeyListener(verticalNavigation);
    }

    private boolean isRightmostControl(View view) {
        if (view == null) return false;
        if (showDeleteButton) return view.getId() == R.id.btnItemDelete;
        if (showFavButton) return view.getId() == R.id.btnItemFav;
        return view.getId() == R.id.btnItemFullscreen;
    }

    private boolean moveVerticalWithinList(View source, int position, int keyCode) {
        if (position == RecyclerView.NO_POSITION) return true;
        RecyclerView recyclerView = findParentRecyclerView(source);
        if (recyclerView == null) return true;
        return moveVerticalWithinList(recyclerView, position, source.getId(), keyCode);
    }

    private boolean moveVerticalWithinList(RecyclerView recyclerView, int position,
                                           int controlId, int keyCode) {
        if (position == RecyclerView.NO_POSITION) return true;
        int generation = ++focusMoveGeneration;

        if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) {
            if (position == 0) {
                clearPendingMoveFocus();
                return firstItemUpListener == null || firstItemUpListener.onFirstItemUp();
            }
            return focusRowControl(recyclerView, position - 1, controlId, generation);
        }

        if (position < getItemCount() - 1) {
            return focusRowControl(recyclerView, position + 1, controlId, generation);
        }
        clearPendingMoveFocus();
        if (nextFocusDownId != View.NO_ID) {
            View target = recyclerView.getRootView().findViewById(nextFocusDownId);
            if (target != null && target.isShown() && target.requestFocus()) return true;
        }
        // The final row is a hard lower boundary even if a page has no next
        // pager button. This keeps a held Down from leaking into the rail.
        return true;
    }

    private boolean focusRowControl(RecyclerView recyclerView, int position, int controlId,
                                    int generation) {
        RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(position);
        if (holder != null) {
            View target = holder.itemView.findViewById(controlId);
            View focusTarget = target != null && target.isShown() ? target : holder.itemView;
            boolean focused = focusTarget.requestFocus();
            if (focused) {
                pendingFocusPosition = RecyclerView.NO_POSITION;
                rememberFocusedPosition(position, focusTarget.getId());
            } else {
                pendingFocusPosition = position;
                pendingFocusControlId = controlId;
                postFocusRetry(recyclerView, position, controlId, generation, 2);
            }
            // This is still an in-list transition. Consume the key even when
            // RecyclerView is between a rebind and its next layout pass.
            return true;
        }
        pendingFocusPosition = position;
        pendingFocusControlId = controlId;
        recyclerView.scrollToPosition(position);
        postFocusRetry(recyclerView, position, controlId, generation, 2);
        return true;
    }

    private void postFocusRetry(RecyclerView recyclerView, int position, int controlId,
                                int generation, int attemptsLeft) {
        recyclerView.postDelayed(() -> {
            if (generation != focusMoveGeneration
                    || pendingFocusPosition != position
                    || pendingFocusControlId != controlId
                    || !recyclerView.isShown()
                    || !canRestoreListMoveFocus(recyclerView, position)) {
                return;
            }
            RecyclerView.ViewHolder targetHolder = recyclerView.findViewHolderForAdapterPosition(position);
            if (targetHolder == null) {
                if (attemptsLeft > 0) {
                    postFocusRetry(recyclerView, position, controlId,
                            generation, attemptsLeft - 1);
                }
                return;
            }
            View target = targetHolder.itemView.findViewById(controlId);
            View focusTarget = target != null && target.isShown() ? target : targetHolder.itemView;
            if (focusTarget.requestFocus()) {
                rememberFocusedPosition(position, focusTarget.getId());
                if (pendingFocusPosition == position) {
                    pendingFocusPosition = RecyclerView.NO_POSITION;
                }
            }
        }, 24L);
    }

    private void rememberFocusedPosition(int position, int controlId) {
        if (position == RecyclerView.NO_POSITION) return;
        lastFocusedPosition = position;
        lastFocusedControlId = controlId == View.NO_ID ? R.id.item_song_root : controlId;
    }

    private void restoreFocusAfterAction(RecyclerView recyclerView, View source, int position, int targetId,
                                         boolean confirmAfterPlayerRefresh) {
        if (position == RecyclerView.NO_POSITION) return;
        if (recyclerView == null) {
            FocusAnimationHelper.keepFocusAfterClick(source);
            return;
        }
        final int generation = ++actionFocusGeneration;
        Runnable restoreFocusNow = () -> restoreActionFocus(
                recyclerView, source, position, targetId, generation, false);
        recyclerView.post(restoreFocusNow);
        // MediaController callbacks can rebind the playing row after its click
        // callback returns. A delayed retry must not override a newer remote
        // navigation decision.
        if (confirmAfterPlayerRefresh) {
            recyclerView.postDelayed(() -> restoreActionFocus(
                    recyclerView, source, position, targetId, generation, true), 320L);
        }
    }

    private void restoreActionFocus(RecyclerView recyclerView, View source, int position,
                                    int targetId, int generation, boolean guardCurrentFocus) {
        if (generation != actionFocusGeneration) return;
        RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(position);
        if (guardCurrentFocus && !canRestorePlaybackFocus(
                recyclerView, holder != null ? holder.itemView : source)) {
            return;
        }
        if (holder != null) {
            View target = holder.itemView.findViewById(targetId);
            if (target != null && target.isShown() && target.requestFocus()) return;
        }
        if (source != null && source.isShown() && source.isEnabled() && source.isFocusable()) {
            source.requestFocus();
        }
    }

    private boolean canRestorePlaybackFocus(RecyclerView recyclerView, View targetItem) {
        if (recyclerView == null || !recyclerView.isShown()) return false;
        View currentFocus = recyclerView.getRootView().findFocus();
        return currentFocus == null
                || currentFocus == recyclerView
                || isWithinView(currentFocus, targetItem);
    }

    private boolean canRestoreListMoveFocus(RecyclerView recyclerView, int targetPosition) {
        View currentFocus = recyclerView.getRootView().findFocus();
        if (currentFocus == null || currentFocus == recyclerView) return true;
        if (!isWithinView(currentFocus, recyclerView)) return false;
        RecyclerView.ViewHolder currentHolder = recyclerView.findContainingViewHolder(currentFocus);
        if (currentHolder == null) return true;
        int currentPosition = currentHolder.getAdapterPosition();
        return currentPosition == targetPosition || currentPosition == lastFocusedPosition;
    }

    private boolean isWithinView(View child, View ancestor) {
        if (child == null || ancestor == null) return false;
        View current = child;
        while (current != null) {
            if (current == ancestor) return true;
            if (!(current.getParent() instanceof View)) return false;
            current = (View) current.getParent();
        }
        return false;
    }

    private boolean isDirectionalKey(int keyCode) {
        return keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP
                || keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    private void cancelDeferredActionFocusForNavigation() {
        actionFocusGeneration++;
        invalidatePendingPlaybackFocus();
    }

    private void invalidatePendingMoveFocus() {
        focusMoveGeneration++;
        clearPendingMoveFocus();
    }

    private void cancelAllDeferredFocus() {
        cancelDeferredActionFocusForNavigation();
        invalidatePendingMoveFocus();
    }

    private void clearPendingMoveFocus() {
        pendingFocusPosition = RecyclerView.NO_POSITION;
    }

    private void invalidatePendingPlaybackFocus() {
        playbackFocusGeneration++;
        clearPendingPlaybackFocus();
    }

    private void rememberPendingPlaybackFocus(RecyclerView recyclerView, int position, int targetId) {
        if (recyclerView == null || position == RecyclerView.NO_POSITION) return;
        int generation = ++playbackFocusGeneration;
        pendingPlaybackRecyclerView = recyclerView;
        pendingPlaybackPosition = position;
        pendingPlaybackTargetId = targetId;
        pendingPlaybackGeneration = generation;
        pendingPlaybackExpiresAt = SystemClock.uptimeMillis() + 1400L;
        recyclerView.postDelayed(() -> {
            if (generation == playbackFocusGeneration
                    && generation == pendingPlaybackGeneration
                    && SystemClock.uptimeMillis() >= pendingPlaybackExpiresAt) {
                clearPendingPlaybackFocus();
            }
        }, 1450L);
    }

    private void clearPendingPlaybackFocus() {
        pendingPlaybackRecyclerView = null;
        pendingPlaybackPosition = RecyclerView.NO_POSITION;
        pendingPlaybackTargetId = View.NO_ID;
        pendingPlaybackGeneration = 0;
        pendingPlaybackExpiresAt = 0L;
    }

    private RecyclerView findParentRecyclerView(View view) {
        ViewParent parent = view.getParent();
        while (parent instanceof View) {
            if (parent instanceof RecyclerView) return (RecyclerView) parent;
            parent = parent.getParent();
        }
        return null;
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private static final String[] SOURCES = {"kw", "kg", "tx", "wy", "mg"};
        private static final String[] SOURCE_NAMES = {"酷我", "酷狗", "QQ音乐", "网易云", "咪咕"};
        
        private static String getSourceDisplayName(String source) {
            if (source == null || source.isEmpty()) return null;
            for (int i = 0; i < SOURCES.length; i++) {
                if (SOURCES[i].equals(source)) {
                    return SOURCE_NAMES[i];
                }
            }
            return source;
        }
        
        private final ImageView ivEqualizer;
        private final TextView tvIndex;
        private final ImageView ivCover;
        private final TextView tvName;
        private final TextView tvArtist;
        private final TextView tvSource;
        private final ImageView btnPlay;
        private final ImageView btnFullscreen;
        private final ImageView btnDelete;
        private final ImageView btnFav;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            FocusAnimationHelper.applyFocusAnimation(itemView);
            ivEqualizer = itemView.findViewById(R.id.ivEqualizer);
            tvIndex = itemView.findViewById(R.id.tvIndex);
            ivCover = itemView.findViewById(R.id.ivCover);
            tvName = itemView.findViewById(R.id.tvSongName);
            tvArtist = itemView.findViewById(R.id.tvArtist);
            tvSource = itemView.findViewById(R.id.tvSource);
            btnPlay = itemView.findViewById(R.id.btnItemPlay);
            btnFullscreen = itemView.findViewById(R.id.btnItemFullscreen);
            btnDelete = itemView.findViewById(R.id.btnItemDelete);
            btnFav = itemView.findViewById(R.id.btnItemFav);
        }

        void bind(MusicInfo song, boolean isCurrentSong, boolean isPlayingNow, int indexOffset) {
            tvName.setText(song.getName());
            tvArtist.setText(song.getSinger() != null ? song.getSinger() : "未知歌手");
            
            String sourceName = song.getSearchSource();
            if (sourceName == null || sourceName.isEmpty()) {
                sourceName = getSourceDisplayName(song.getSource());
            }
            if (sourceName != null && !sourceName.isEmpty()) {
                tvSource.setText(sourceName);
                tvSource.setVisibility(View.VISIBLE);
            } else {
                tvSource.setVisibility(View.GONE);
            }
            
            if (isCurrentSong) {
                ivEqualizer.setVisibility(View.VISIBLE);
                tvIndex.setVisibility(View.GONE);
            } else {
                ivEqualizer.setVisibility(View.GONE);
                tvIndex.setVisibility(View.VISIBLE);
                tvIndex.setText(String.valueOf(indexOffset + getAdapterPosition() + 1));
            }
            
            if (song.getPicUrl() != null && !song.getPicUrl().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(song.getPicUrl())
                        .placeholder(R.drawable.ic_cover_placeholder)
                        .transform(new RoundedCorners(8))
                        .into(ivCover);
            } else {
                ivCover.setImageResource(R.drawable.ic_cover_placeholder);
            }
        }
    }
}
