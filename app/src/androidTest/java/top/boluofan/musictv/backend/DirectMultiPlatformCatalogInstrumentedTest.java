package top.boluofan.musictv.backend;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import top.boluofan.musictv.api.model.Playlist;
import top.boluofan.musictv.api.model.LyricInfo;
import top.boluofan.musictv.api.model.MusicInfo;

public class DirectMultiPlatformCatalogInstrumentedTest {
    private static final String[] SOURCES = {"mg", "kw", "kg", "tx"};

    @Test
    public void searchesEveryAdditionalPlatform() throws Exception {
        DirectCatalogProvider provider = new DirectCatalogProvider();
        runInParallel(source -> {
            assertFalse(source + " search returned no songs",
                    provider.search(source, "周杰伦", 1, 3).isEmpty());
            return null;
        });
    }

    @Test
    public void opensPlaylistsAndRankingsForEveryAdditionalPlatform() throws Exception {
        DirectCatalogProvider provider = new DirectCatalogProvider();
        runInParallel(source -> {
            JsonObject page = provider.playlistList(source, "", "hot", 1);
            JsonArray playlists = page.getAsJsonArray("list");
            assertNotNull(source + " playlist response is missing list", playlists);
            assertTrue(source + " playlist square is empty", playlists.size() > 0);
            String playlistId = playlists.get(0).getAsJsonObject().get("id").getAsString();
            Playlist detail = provider.playlistDetail(source, playlistId, 1);
            assertNotNull(source + " playlist detail is null", detail);
            assertNotNull(source + " playlist songs are null", detail.getSongs());
            assertFalse(source + " playlist has no songs", detail.getSongs().isEmpty());

            JsonArray boards = provider.boards(source);
            assertTrue(source + " boards are empty", boards.size() > 0);
            String boardId = boards.get(0).getAsJsonObject().get("id").getAsString();
            assertFalse(source + " board has no songs",
                    provider.boardSongs(source, boardId, 1).isEmpty());
            return null;
        });
    }

    @Test
    public void loadsLyricsForEveryAdditionalPlatform() throws Exception {
        DirectCatalogProvider provider = new DirectCatalogProvider();
        runInParallel(source -> {
            LyricInfo lyric = provider.lyric(source, lyricParams(source));
            assertNotNull(source + " lyric response is null", lyric);
            assertFalse(source + " lyric is empty", lyric.getPrimaryLyric().trim().isEmpty());
            return null;
        });
    }

    private static Map<String, String> lyricParams(String source) {
        Map<String, String> params = new HashMap<>();
        params.put("source", source);
        if ("kw".equals(source)) {
            params.put("songmid", "228908");
        } else if ("kg".equals(source)) {
            params.put("name", "晴天");
            params.put("hash", "B3A52A7A958BF0AED0EBFBA2E9A818B7");
            params.put("interval", "04:29");
        } else if ("tx".equals(source)) {
            params.put("songmid", "0039MnYb0qxYhV");
        } else if ("mg".equals(source)) {
            params.put("lrcUrl", "https://d.musicapp.migu.cn/data/oss/resource/00/2t/1l/"
                    + "38a861e9806641448f8cab425b1f2b18");
        }
        return params;
    }

    private static void runInParallel(SourceTask task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(SOURCES.length);
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (String source : SOURCES) {
                futures.add(executor.submit((Callable<Void>) () -> task.run(source)));
            }
            for (Future<Void> future : futures) future.get(60, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private interface SourceTask {
        Void run(String source) throws Exception;
    }
}
