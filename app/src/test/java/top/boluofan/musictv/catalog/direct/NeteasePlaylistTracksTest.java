package top.boluofan.musictv.catalog.direct;

import static org.junit.Assert.assertEquals;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Map;

import org.junit.Test;

public class NeteasePlaylistTracksTest {
    @Test
    public void mergesFetchedTracksBackIntoSourceOrder() {
        JsonArray trackIds = new JsonArray();
        trackIds.add(trackId("3"));
        trackIds.add(trackId("1"));
        trackIds.add(trackId("2"));
        JsonArray partial = new JsonArray();
        partial.add(track("1", "one"));
        Map<String, JsonObject> available = DirectLxApiService.trackMap(partial);
        JsonArray fetched = new JsonArray();
        fetched.add(track("2", "two"));
        fetched.add(track("3", "three"));
        available.putAll(DirectLxApiService.trackMap(fetched));

        JsonArray ordered = DirectLxApiService.orderedTracks(trackIds, available);

        assertEquals(3, ordered.size());
        assertEquals("3", ordered.get(0).getAsJsonObject().get("id").getAsString());
        assertEquals("1", ordered.get(1).getAsJsonObject().get("id").getAsString());
        assertEquals("2", ordered.get(2).getAsJsonObject().get("id").getAsString());
    }

    private static JsonObject trackId(String id) {
        JsonObject value = new JsonObject();
        value.addProperty("id", id);
        return value;
    }

    private static JsonObject track(String id, String name) {
        JsonObject value = trackId(id);
        value.addProperty("name", name);
        return value;
    }
}
