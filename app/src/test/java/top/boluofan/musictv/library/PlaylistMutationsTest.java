package top.boluofan.musictv.library;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import top.boluofan.musictv.catalog.model.ListData;
import top.boluofan.musictv.catalog.model.MusicInfo;
import top.boluofan.musictv.catalog.model.Playlist;

public class PlaylistMutationsTest {
    @Test
    public void findByNameHandlesNullEmptyAndMissingLists() {
        Playlist target = playlist("target-id", "Target");

        assertNull(PlaylistMutations.findByName(null, "Target"));
        assertNull(PlaylistMutations.findByName(Collections.emptyList(), "Target"));
        assertNull(PlaylistMutations.findByName(Arrays.asList(null, target), null));
        assertNull(PlaylistMutations.findByName(Arrays.asList(null, target), "Missing"));
    }

    @Test
    public void findByNameReturnsFirstMatchingPlaylist() {
        Playlist first = playlist("first-id", "Target");
        Playlist second = playlist("second-id", "Target");

        Playlist result = PlaylistMutations.findByName(Arrays.asList(null, first, second), "Target");

        assertSame(first, result);
    }

    @Test
    public void prependSongAllowsSameNameFromDifferentSourceAndPreservesOldOrder() {
        MusicInfo existing = song("same-name", "kw");
        MusicInfo tail = song("tail", "mg");
        MusicInfo added = song("same-name", "tx");
        Playlist playlist = playlist("id", "List");
        playlist.setSongs(new ArrayList<>(Arrays.asList(existing, tail)));

        boolean changed = PlaylistMutations.prependSongIfAbsent(playlist, added);

        assertTrue(changed);
        assertEquals(Arrays.asList(added, existing, tail), playlist.getSongs());
        assertEquals(3, playlist.getSongCount());
    }

    @Test
    public void prependSongRejectsMatchingNameAndSourceWithoutMutation() {
        MusicInfo existing = song("same-name", "kw");
        MusicInfo duplicate = song("same-name", "kw");
        List<MusicInfo> original = new ArrayList<>(Collections.singletonList(existing));
        Playlist playlist = playlist("id", "List");
        playlist.setSongs(original);
        playlist.setSongCount(9);

        boolean changed = PlaylistMutations.prependSongIfAbsent(playlist, duplicate);

        assertFalse(changed);
        assertSame(original, playlist.getSongs());
        assertEquals(Collections.singletonList(existing), playlist.getSongs());
        assertEquals(9, playlist.getSongCount());
    }

    @Test(expected = NullPointerException.class)
    public void prependSongPreservesSearchAndRankingNullEntryFailure() {
        Playlist playlist = playlist("id", "List");
        playlist.setSongs(new ArrayList<>(Collections.singletonList(null)));

        PlaylistMutations.prependSongIfAbsent(playlist, song("new", "kw"));
    }

    @Test
    public void prependSongSkippingNullsPreservesPlaylistDetailOrder() {
        MusicInfo existing = song("existing", "kw");
        MusicInfo added = song("new", "tx");
        Playlist playlist = playlist("id", "List");
        playlist.setSongs(new ArrayList<>(Arrays.asList(null, existing)));

        assertTrue(PlaylistMutations.prependSongIfAbsentSkippingNulls(playlist, added));
        assertEquals(Arrays.asList(added, null, existing), playlist.getSongs());
        assertEquals(3, playlist.getSongCount());
    }

    @Test
    public void prependSongInitializesEmptySongListAndRejectsNullInput() {
        Playlist playlist = playlist("id", "List");
        MusicInfo added = song("new", "kg");

        assertFalse(PlaylistMutations.prependSongIfAbsent(null, added));
        assertFalse(PlaylistMutations.prependSongIfAbsent(playlist, null));
        assertTrue(PlaylistMutations.prependSongIfAbsent(playlist, added));
        assertEquals(Collections.singletonList(added), playlist.getSongs());
        assertEquals(1, playlist.getSongCount());
    }

    @Test
    public void createCollectedPlaylistAppendsCompleteCopyWithoutReorderingExistingLists() {
        Playlist existing = playlist("existing-id", "Existing");
        ListData listData = new ListData();
        listData.setUserList(new ArrayList<>(Collections.singletonList(existing)));
        MusicInfo first = song("first", "kw");
        MusicInfo second = song("second", "tx");
        List<MusicInfo> sourceSongs = new ArrayList<>(Arrays.asList(first, null, second));

        Playlist created = PlaylistMutations.createCollectedPlaylist(
                listData, "new-id", "Collected", "mg", "source-list-id", sourceSongs);

        assertEquals(Arrays.asList(existing, created), listData.getUserList());
        assertEquals("new-id", created.getId());
        assertEquals("Collected", created.getName());
        assertEquals("mg", created.getSource());
        assertEquals("source-list-id", created.getSourceListId());
        assertEquals(Arrays.asList(first, null, second), created.getSongs());
        assertEquals(3, created.getSongCount());
        sourceSongs.clear();
        assertEquals(3, created.getSongs().size());
    }

    @Test
    public void createCollectedPlaylistInitializesNullUserAndSongLists() {
        ListData listData = new ListData();

        assertNull(PlaylistMutations.createCollectedPlaylist(
                null, "id", "Empty", "kw", "remote-id", null));
        Playlist created = PlaylistMutations.createCollectedPlaylist(
                listData, "id", "Empty", "kw", "remote-id", null);

        assertEquals(Collections.singletonList(created), listData.getUserList());
        assertTrue(created.getSongs().isEmpty());
        assertEquals(0, created.getSongCount());
    }

    @Test
    public void overwriteCollectedPlaylistPreservesIdentityAndReplacesSnapshotFields() {
        Playlist playlist = playlist("stable-id", "Stable name");
        playlist.setSource("old-source");
        playlist.setSourceListId("old-list-id");
        playlist.setSongs(new ArrayList<>(Collections.singletonList(song("old", "kw"))));
        MusicInfo first = song("first", "tx");
        MusicInfo second = song("second", "mg");
        List<MusicInfo> sourceSongs = new ArrayList<>(Arrays.asList(first, null, second));

        assertNull(PlaylistMutations.overwriteCollectedPlaylist(
                null, "new-source", "new-list-id", sourceSongs));
        Playlist result = PlaylistMutations.overwriteCollectedPlaylist(
                playlist, "new-source", "new-list-id", sourceSongs);

        assertSame(playlist, result);
        assertEquals("stable-id", result.getId());
        assertEquals("Stable name", result.getName());
        assertEquals("new-source", result.getSource());
        assertEquals("new-list-id", result.getSourceListId());
        assertEquals(Arrays.asList(first, null, second), result.getSongs());
        assertEquals(3, result.getSongCount());
        sourceSongs.clear();
        assertEquals(3, result.getSongs().size());
    }

    private static Playlist playlist(String id, String name) {
        Playlist playlist = new Playlist();
        playlist.setId(id);
        playlist.setName(name);
        return playlist;
    }

    private static MusicInfo song(String name, String source) {
        MusicInfo song = new MusicInfo();
        song.setName(name);
        song.setSource(source);
        return song;
    }
}
