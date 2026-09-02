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

    public static boolean toggleFavorite(ListData listData, MusicInfo song) {
        if (listData == null || song == null) return false;
        List<MusicInfo> favorites = listData.getLoveList();
        if (favorites == null) {
            favorites = new ArrayList<>();
            listData.setLoveList(favorites);
        }
        boolean removed = false;
        for (int i = favorites.size() - 1; i >= 0; i--) {
            if (sameSong(favorites.get(i), song)) {
                favorites.remove(i);
                removed = true;
            }
        }
        if (removed) return false;
        favorites.add(0, song);
        return true;
    }

    public static boolean isFavorite(List<MusicInfo> favorites, MusicInfo song) {
        if (favorites == null || song == null) return false;
        for (MusicInfo favorite : favorites) {
            if (sameSong(favorite, song)) return true;
        }
        return false;
    }

    public static boolean deletePlaylist(ListData listData, String playlistId) {
        List<Playlist> playlists = listData == null ? null : listData.getUserList();
        if (playlists == null || playlistId == null) return false;
        for (int i = 0; i < playlists.size(); i++) {
            Playlist playlist = playlists.get(i);
            if (playlist != null && Objects.equals(playlistId, playlist.getId())) {
                playlists.remove(i);
                return true;
            }
        }
        return false;
    }

    public static RenameResult renamePlaylist(ListData listData, String playlistId,
                                               String newName) {
        String normalizedName = newName == null ? "" : newName.trim();
        if (normalizedName.isEmpty()) return RenameResult.INVALID_NAME;
        List<Playlist> playlists = listData == null ? null : listData.getUserList();
        if (playlists == null || playlistId == null) return RenameResult.NOT_FOUND;
        Playlist target = null;
        for (Playlist playlist : playlists) {
            if (playlist == null) continue;
            if (Objects.equals(playlistId, playlist.getId())) {
                target = playlist;
            } else if (normalizedName.equals(playlist.getName())) {
                return RenameResult.DUPLICATE_NAME;
            }
        }
        if (target == null) return RenameResult.NOT_FOUND;
        target.setName(normalizedName);
        return RenameResult.RENAMED;
    }

    private static boolean sameSong(MusicInfo first, MusicInfo second) {
        if (first == null || second == null) return false;
        String firstId = normalized(first.getSongmid());
        String secondId = normalized(second.getSongmid());
        if (firstId != null && secondId != null) {
            return Objects.equals(normalized(first.getSource()), normalized(second.getSource()))
                    && firstId.equals(secondId);
        }
        return Objects.equals(normalized(first.getSource()), normalized(second.getSource()))
                && Objects.equals(normalized(first.getName()), normalized(second.getName()))
                && Objects.equals(normalized(first.getSinger()), normalized(second.getSinger()));
    }

    private static String normalized(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public enum RenameResult {
        RENAMED,
        NOT_FOUND,
        DUPLICATE_NAME,
        INVALID_NAME
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
