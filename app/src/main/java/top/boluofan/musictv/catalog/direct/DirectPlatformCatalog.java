package top.boluofan.musictv.catalog.direct;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import top.boluofan.musictv.catalog.model.LyricInfo;
import top.boluofan.musictv.catalog.model.MusicInfo;
import top.boluofan.musictv.catalog.model.Playlist;

public interface DirectPlatformCatalog {
    List<MusicInfo> search(String keyword, int page, int limit) throws IOException;

    JsonObject playlistList(String tagId, String sortId, int page) throws IOException;

    Playlist playlistDetail(String id, int page) throws IOException;

    List<MusicInfo> boardSongs(String id, int page) throws IOException;

    LyricInfo lyric(Map<String, String> params) throws IOException;
}
