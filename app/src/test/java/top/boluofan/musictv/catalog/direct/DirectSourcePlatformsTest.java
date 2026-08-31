package top.boluofan.musictv.catalog.direct;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.google.gson.JsonObject;
import org.junit.Test;

public class DirectSourcePlatformsTest {
    @Test
    public void snapshotFiltersCapabilitiesInStablePlatformOrder() {
        JsonObject capabilities = new JsonObject();
        capabilities.add("wy", new JsonObject());
        capabilities.add("unknown", new JsonObject());
        capabilities.add("kw", new JsonObject());
        capabilities.add("mg", new JsonObject());

        DirectSourcePlatforms.Snapshot snapshot =
                DirectSourcePlatforms.snapshot(capabilities);

        assertArrayEquals(new String[] {"mg", "kw", "wy"}, snapshot.codes());
        assertArrayEquals(new String[] {"咪咕", "酷我", "网易云"}, snapshot.names());
        assertEquals(snapshot.codes().length, snapshot.names().length);
    }

    @Test
    public void emptyCapabilitiesReturnAnEmptyPairedSnapshot() {
        DirectSourcePlatforms.Snapshot snapshot =
                DirectSourcePlatforms.snapshot((JsonObject) null);

        assertArrayEquals(new String[0], snapshot.codes());
        assertArrayEquals(new String[0], snapshot.names());
        assertEquals(snapshot.codes().length, snapshot.names().length);
    }

    @Test
    public void returnedArraysCannotMutateTheSnapshot() {
        JsonObject capabilities = new JsonObject();
        capabilities.add("kg", new JsonObject());
        DirectSourcePlatforms.Snapshot snapshot =
                DirectSourcePlatforms.snapshot(capabilities);

        snapshot.codes()[0] = "changed";
        snapshot.names()[0] = "changed";

        assertArrayEquals(new String[] {"kg"}, snapshot.codes());
        assertArrayEquals(new String[] {"酷狗"}, snapshot.names());
    }
}
