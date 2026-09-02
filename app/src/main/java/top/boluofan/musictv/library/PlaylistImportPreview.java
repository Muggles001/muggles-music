package top.boluofan.musictv.library;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import top.boluofan.musictv.catalog.PlaylistReference;
import top.boluofan.musictv.catalog.model.MusicInfo;

public final class PlaylistImportPreview {
    private final PlaylistReference reference;
    private final String localPlaylistId;
    private final String name;
    private final String coverUrl;
    private final String description;
    private final String creator;
    private final int originalCount;
    private final List<MusicInfo> songs;

    public PlaylistImportPreview(PlaylistReference reference, String localPlaylistId,
                                 String name, String coverUrl, String description,
                                 String creator, int originalCount, List<MusicInfo> songs) {
        this.reference = reference;
        this.localPlaylistId = localPlaylistId;
        this.name = name;
        this.coverUrl = coverUrl;
        this.description = description;
        this.creator = creator;
        this.originalCount = Math.max(originalCount, songs == null ? 0 : songs.size());
        this.songs = Collections.unmodifiableList(
                songs == null ? new ArrayList<>() : new ArrayList<>(songs));
    }

    public PlaylistReference getReference() {
        return reference;
    }

    public String getLocalPlaylistId() {
        return localPlaylistId;
    }

    public boolean isRefresh() {
        return localPlaylistId != null;
    }

    public String getName() {
        return name;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public String getDescription() {
        return description;
    }

    public String getCreator() {
        return creator;
    }

    public int getOriginalCount() {
        return originalCount;
    }

    public List<MusicInfo> getSongs() {
        return songs;
    }

    public int getImportableCount() {
        return songs.size();
    }

    public int getSkippedCount() {
        return Math.max(0, originalCount - songs.size());
    }

    public PlaylistImportPreview withExistingPlaylist(String playlistId, String localName) {
        return new PlaylistImportPreview(reference, playlistId, localName, coverUrl,
                description, creator, originalCount, songs);
    }
}
