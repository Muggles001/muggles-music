package top.boluofan.musictv.player.queue;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import top.boluofan.musictv.catalog.model.MusicInfo;
import top.boluofan.musictv.player.queue.PlaybackExtrasMapper;
import top.boluofan.musictv.player.url.PlaybackUrlResolver;

/** Builds a Media3 queue while keeping malformed server entries out of it. */
public final class PlaybackQueue {
    private final List<MediaItem> mediaItems;
    private final List<Integer> sourceIndices;

    private PlaybackQueue(List<MediaItem> mediaItems, List<Integer> sourceIndices) {
        this.mediaItems = Collections.unmodifiableList(mediaItems);
        this.sourceIndices = Collections.unmodifiableList(sourceIndices);
    }

    public static PlaybackQueue from(@Nullable List<MusicInfo> songs) {
        List<MediaItem> items = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        if (songs == null) return new PlaybackQueue(items, indices);

        for (int i = 0; i < songs.size(); i++) {
            MediaItem item = createMediaItem(songs.get(i));
            if (item != null) {
                items.add(item);
                indices.add(i);
            }
        }
        return new PlaybackQueue(items, indices);
    }

    @Nullable
    public static MediaItem createMediaItem(@Nullable MusicInfo song) {
        if (song == null) return null;
        String source = normalized(song.getSource());
        String songmid = normalized(song.getSongmid());
        if (source == null || songmid == null) return null;

        Uri artworkUri = null;
        String coverUrl = normalized(song.getPicUrl());
        if (coverUrl != null) artworkUri = Uri.parse(coverUrl);

        MediaMetadata.Builder metadata = new MediaMetadata.Builder()
                .setTitle(song.getName())
                .setArtist(song.getSinger())
                .setAlbumTitle(song.getAlbumName())
                .setExtras(PlaybackExtrasMapper.from(song));
        if (artworkUri != null) metadata.setArtworkUri(artworkUri);

        return new MediaItem.Builder()
                .setMediaId(songmid)
                .setUri(PlaybackUrlResolver.buildResolveUri(song))
                .setMediaMetadata(metadata.build())
                .build();
    }

    public List<MediaItem> getMediaItems() {
        return mediaItems;
    }

    public int size() {
        return mediaItems.size();
    }

    public boolean isEmpty() {
        return mediaItems.isEmpty();
    }

    public void play(Player player, int queueIndex) {
        player.setMediaItems(mediaItems, queueIndex, 0);
        player.prepare();
        player.play();
    }

    public boolean playSourceIndex(Player player, int sourceIndex) {
        int queueIndex = queueIndexForSourceIndex(sourceIndex);
        if (queueIndex < 0) return false;
        play(player, queueIndex);
        return true;
    }

    public boolean playSourceIndex(Player player, int sourceIndex, boolean shuffle) {
        int queueIndex = queueIndexForSourceIndex(sourceIndex);
        if (queueIndex < 0) return false;
        player.setMediaItems(mediaItems, queueIndex, 0);
        player.setShuffleModeEnabled(shuffle);
        player.prepare();
        player.play();
        return true;
    }

    /** Returns the queue position for an index in the original server list. */
    public int queueIndexForSourceIndex(int sourceIndex) {
        return sourceIndices.indexOf(sourceIndex);
    }

    private static String normalized(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
