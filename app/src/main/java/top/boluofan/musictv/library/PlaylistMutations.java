package top.boluofan.musictv.library;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import top.boluofan.musictv.catalog.model.ListData;
import top.boluofan.musictv.catalog.model.MusicInfo;
import top.boluofan.musictv.catalog.model.Playlist;

public final class PlaylistMutations {
    private PlaylistMutations() {
    }

    public static Playlist findByName(List<Playlist> playlists, String name) {
        if (playlists == null || name == null) return null;
        for (Playlist playlist : playlists) {
            if (playlist != null && name.equals(playlist.getName())) return playlist;
        }
        return null;
    }

    public static boolean prependSongIfAbsent(Playlist playlist, MusicInfo song) {
        if (playlist == null || song == null) return false;
        List<MusicInfo> songs = playlist.getSongs();
        if (songs == null) songs = new ArrayList<>();

        for (MusicInfo existing : songs) {
            if (Objects.equals(existing.getName(), song.getName())
                    && Objects.equals(existing.getSource(), song.getSource())) {
                return false;
            }
        }

        songs.add(0, song);
        playlist.setSongs(songs);
        playlist.setSongCount(songs.size());
        return true;
    }

    public static boolean prependSongIfAbsentSkippingNulls(Playlist playlist, MusicInfo song) {
        if (playlist == null || song == null) return false;
        List<MusicInfo> songs = playlist.getSongs();
        if (songs == null) songs = new ArrayList<>();

        for (MusicInfo existing : songs) {
            // PlaylistDetail already tolerates persisted null slots and must retain their position.
            if (existing != null
                    && Objects.equals(existing.getName(), song.getName())
                    && Objects.equals(existing.getSource(), song.getSource())) {
                return false;
            }
        }

        songs.add(0, song);
        playlist.setSongs(songs);
        playlist.setSongCount(songs.size());
        return true;
    }

    public static Playlist createCollectedPlaylist(ListData listData, String id, String name,
                                                    String source, String sourceListId,
                                                    List<MusicInfo> songs) {
        if (listData == null) return null;
        Playlist playlist = new Playlist();
        playlist.setId(id);
        playlist.setName(name);
        replaceSnapshot(playlist, source, sourceListId, songs);

        List<Playlist> userPlaylists = listData.getUserList();
        if (userPlaylists == null) {
            userPlaylists = new ArrayList<>();
            listData.setUserList(userPlaylists);
        }
        userPlaylists.add(playlist);
        return playlist;
    }

    public static Playlist overwriteCollectedPlaylist(Playlist playlist, String source,
                                                       String sourceListId,
                                                       List<MusicInfo> songs) {
        if (playlist == null) return null;
        replaceSnapshot(playlist, source, sourceListId, songs);
        return playlist;
    }

    private static void replaceSnapshot(Playlist playlist, String source, String sourceListId,
                                        List<MusicInfo> songs) {
        List<MusicInfo> snapshot = songs == null ? new ArrayList<>() : new ArrayList<>(songs);
        playlist.setSource(source);
        playlist.setSourceListId(sourceListId);
        playlist.setSongs(snapshot);
        playlist.setSongCount(snapshot.size());
    }
}
