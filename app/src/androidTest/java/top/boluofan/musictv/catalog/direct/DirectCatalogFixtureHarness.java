package top.boluofan.musictv.catalog.direct;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import androidx.test.platform.app.InstrumentationRegistry;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import top.boluofan.musictv.catalog.model.MusicInfo;
import top.boluofan.musictv.catalog.direct.DirectHttpTransport;
import top.boluofan.musictv.catalog.direct.kg.KgCatalog;
import top.boluofan.musictv.catalog.direct.kw.KwCatalog;
import top.boluofan.musictv.catalog.direct.mg.MgCatalog;
import top.boluofan.musictv.catalog.direct.tx.TxCatalog;

final class DirectCatalogFixtureHarness {
    private DirectCatalogFixtureHarness() {
    }

    static List<MusicInfo> mapSongs(String source, String fixture) throws Exception {
        JsonArray raw = readFixture(fixture);
        if ("kw".equals(source)) {
            Method method = KwCatalog.class.getDeclaredMethod("mapSongs", JsonArray.class);
            method.setAccessible(true);
            return invokeMap(method, new KwCatalog(new DirectHttpTransport()), raw);
        }
        if ("kg".equals(source)) {
            Method method = KgCatalog.class.getDeclaredMethod("mapSongs", JsonArray.class);
            method.setAccessible(true);
            return invokeMap(method, new KgCatalog(new DirectHttpTransport()), raw);
        }
        if ("tx".equals(source)) {
            Method method = TxCatalog.class.getDeclaredMethod("mapSongs", JsonArray.class);
            method.setAccessible(true);
            return invokeMap(method, new TxCatalog(new DirectHttpTransport()), raw);
        }
        if ("mg".equals(source)) {
            Method method = MgCatalog.class.getDeclaredMethod("mapSongs", JsonArray.class);
            method.setAccessible(true);
            return invokeMap(method, new MgCatalog(new DirectHttpTransport()), raw);
        }
        throw new IllegalArgumentException("Unsupported fixture source: " + source);
    }

    static MusicInfo onlySong(String source, String fixture) throws Exception {
        List<MusicInfo> songs = mapSongs(source, fixture);
        assertEquals(1, songs.size());
        return songs.get(0);
    }

    static void assertSong(MusicInfo song, String source, String id, String name,
                           String singer, String interval) {
        assertEquals(source, song.getSource());
        assertEquals(id, song.getId());
        assertEquals(name, song.getName());
        assertEquals(singer, song.getSinger());
        assertEquals(interval, song.getInterval());
    }

    static void assertQuality(MusicInfo song, String type, String size, String hash) {
        Map<String, MusicInfo.QualityDetail> qualities = song.get_types();
        assertNotNull(qualities);
        MusicInfo.QualityDetail quality = qualities.get(type);
        assertNotNull(type, quality);
        assertEquals(size, quality.getSize());
        assertEquals(hash, quality.getHash());
    }

    @SuppressWarnings("unchecked")
    private static List<MusicInfo> invokeMap(Method method, Object target, Object... arguments)
            throws Exception {
        return (List<MusicInfo>) method.invoke(target, arguments);
    }

    private static JsonArray readFixture(String name) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                InstrumentationRegistry.getInstrumentation().getContext().getAssets()
                        .open("catalog/" + name), StandardCharsets.UTF_8))) {
            return new JsonParser().parse(reader).getAsJsonArray();
        }
    }
}
