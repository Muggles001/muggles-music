package top.boluofan.musictv.player.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import top.boluofan.musictv.test.InMemorySharedPreferences;

public class PlayerPreferencesTest {
    @Test
    public void preservesLegacyFilesKeysAndDefaults() {
        InMemorySharedPreferences lxPreferences = new InMemorySharedPreferences();
        InMemorySharedPreferences playerPreferences = new InMemorySharedPreferences();
        PlayerPreferences preferences = new PlayerPreferences(lxPreferences, playerPreferences);

        assertEquals("", preferences.getLastPlaylistName());
        assertNull(preferences.getCurrentSongName());

        lxPreferences.putInitial(PlayerPreferences.KEY_LAST_PLAYLIST_NAME, "收藏歌单");
        preferences.setCurrentSongName("当前歌曲");

        assertEquals("收藏歌单", preferences.getLastPlaylistName());
        assertEquals("当前歌曲", preferences.getCurrentSongName());
    }
}
