package top.boluofan.musictv.player.queue;

import android.os.Bundle;
import top.boluofan.musictv.catalog.model.MusicInfo;

public final class PlaybackExtrasMapper {
    private PlaybackExtrasMapper() {
    }

    public static Bundle from(MusicInfo song) {
        Bundle extras = new Bundle();
        putString(extras, "song_id", song.getId());
        putString(extras, "source", song.getSource());
        putString(extras, "songmid", song.getSongmid());
        putString(extras, "pic_url", song.getPicUrl());
        putString(extras, "original_name", song.getName());
        putString(extras, "singer", song.getSinger());
        putString(extras, "hash", song.getHash());
        putString(extras, "interval", song.getInterval());
        putString(extras, "copyrightId", song.getCopyrightId());
        putString(extras, "albumId", song.getAlbumId());
        putString(extras, "lrcUrl", song.getLrcUrl());
        putString(extras, "mrcUrl", song.getMrcUrl());
        putString(extras, "trcUrl", song.getTrcUrl());
        return extras;
    }

    private static void putString(Bundle extras, String key, String value) {
        extras.putString(key, value == null ? "" : value);
    }
}
