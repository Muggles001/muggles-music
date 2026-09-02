package top.boluofan.musictv.ui.library;

import java.util.Objects;

import top.boluofan.musictv.catalog.model.Playlist;

public final class LibraryPlaylistRow {
    private final String playlistId;
    private final String name;
    private final int songCount;

    public LibraryPlaylistRow(String playlistId, String name, int songCount) {
        this.playlistId = playlistId;
        this.name = name;
        this.songCount = Math.max(0, songCount);
    }

    public static LibraryPlaylistRow from(Playlist playlist) {
        Objects.requireNonNull(playlist, "playlist");
        return new LibraryPlaylistRow(
                playlist.getId(), playlist.getName(), playlist.getSongCount());
    }

    public String getPlaylistId() {
        return playlistId;
    }

    public String getName() {
        return name;
    }

    public int getSongCount() {
        return songCount;
    }
}
