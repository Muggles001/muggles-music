package top.boluofan.musictv.player.settings;

import android.content.Context;
import android.content.SharedPreferences;

public final class PlayerPreferences {
    private static final String LX_PREFS_NAME = "LxMusicPrefs";
    private static final String PLAYER_PREFS_NAME = "XiaoMusicPrefs";
    static final String KEY_LAST_PLAYLIST_NAME = "last_playlist_name";
    static final String KEY_CURRENT_SONG_NAME = "current_song_name";

    private final SharedPreferences lxPreferences;
    private final SharedPreferences playerPreferences;

    public PlayerPreferences(Context context) {
        Context appContext = context.getApplicationContext();
        lxPreferences = appContext.getSharedPreferences(LX_PREFS_NAME, Context.MODE_PRIVATE);
        playerPreferences = appContext.getSharedPreferences(
                PLAYER_PREFS_NAME, Context.MODE_PRIVATE);
    }

    PlayerPreferences(SharedPreferences lxPreferences, SharedPreferences playerPreferences) {
        this.lxPreferences = lxPreferences;
        this.playerPreferences = playerPreferences;
    }

    public String getLastPlaylistName() {
        return lxPreferences.getString(KEY_LAST_PLAYLIST_NAME, "");
    }

    public String getCurrentSongName() {
        return playerPreferences.getString(KEY_CURRENT_SONG_NAME, null);
    }

    public void setCurrentSongName(String songName) {
        playerPreferences.edit().putString(KEY_CURRENT_SONG_NAME, songName).apply();
    }
}
