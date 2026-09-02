package top.boluofan.musictv.ui.library;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import top.boluofan.musictv.catalog.model.Playlist;

public class LibraryPlaylistRowTest {
    @Test
    public void rowKeepsStableIdWhenNamesMatch() {
        Playlist first = playlist("first", "同名歌单", 2);
        Playlist second = playlist("second", "同名歌单", 3);

        LibraryPlaylistRow firstRow = LibraryPlaylistRow.from(first);
        LibraryPlaylistRow secondRow = LibraryPlaylistRow.from(second);

        assertEquals("first", firstRow.getPlaylistId());
        assertEquals("second", secondRow.getPlaylistId());
        assertEquals("同名歌单", firstRow.getName());
        assertEquals("同名歌单", secondRow.getName());
        assertEquals(2, firstRow.getSongCount());
        assertEquals(3, secondRow.getSongCount());
    }

    private static Playlist playlist(String id, String name, int count) {
        Playlist playlist = new Playlist();
        playlist.setId(id);
        playlist.setName(name);
        playlist.setSongCount(count);
        return playlist;
    }
}
