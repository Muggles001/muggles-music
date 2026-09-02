package top.boluofan.musictv.library;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

import top.boluofan.musictv.catalog.PlaylistReference;
import top.boluofan.musictv.catalog.model.ListData;
import top.boluofan.musictv.catalog.model.MusicInfo;
import top.boluofan.musictv.catalog.model.Playlist;

public class PlaylistImportMutationsTest {
    @Test
    public void sameSourceIdentityRefreshesInPlaceAndKeepsLocalName() {
        Playlist existing = playlist("local-id", "我的名字", "wy", "123");
        Playlist other = playlist("other-id", "其他", "tx", "123");
        ListData data = new ListData();
        data.setUserList(new ArrayList<>(Arrays.asList(existing, other)));
        PlaylistImportPreview preview = preview("wy", "123", "平台名字");

        Playlist result = PlaylistImportMutations.apply(data, preview);

        assertSame(existing, result);
        assertEquals("local-id", result.getId());
        assertEquals("我的名字", result.getName());
        assertEquals(Arrays.asList(existing, other), data.getUserList());
        assertEquals("new-song", result.getSongs().get(0).getName());
    }

    @Test
    public void sameNameDifferentIdentityCreatesNewPlaylistAtTail() {
        Playlist existing = playlist("local-id", "同名", "tx", "123");
        ListData data = new ListData();
        data.setUserList(new ArrayList<>(Collections.singletonList(existing)));
        PlaylistImportPreview preview = preview("wy", "123", "同名");

        Playlist created = PlaylistImportMutations.apply(data, preview);

        assertEquals(2, data.getUserList().size());
        assertSame(created, data.getUserList().get(1));
        assertNotEquals("local-id", created.getId());
        assertEquals("同名", created.getName());
    }

    private static PlaylistImportPreview preview(String source, String id, String name) {
        MusicInfo song = new MusicInfo();
        song.setName("new-song");
        song.setSource(source);
        song.setSongmid("song-id");
        return new PlaylistImportPreview(new PlaylistReference(source, id, null), null,
                name, "cover", "desc", "creator", 1,
                Collections.singletonList(song));
    }

    private static Playlist playlist(String id, String name, String source, String sourceListId) {
        Playlist playlist = new Playlist();
        playlist.setId(id);
        playlist.setName(name);
        playlist.setSource(source);
        playlist.setSourceListId(sourceListId);
        return playlist;
    }
}
