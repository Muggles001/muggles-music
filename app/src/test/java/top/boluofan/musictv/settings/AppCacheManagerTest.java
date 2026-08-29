package top.boluofan.musictv.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AppCacheManagerTest {
    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void measureIncludesNestedFiles() throws Exception {
        File root = temporaryFolder.newFolder("cache");
        write(new File(root, "image.bin"), 7);
        File nested = new File(root, "nested");
        assertTrue(nested.mkdir());
        write(new File(nested, "response.bin"), 11);

        assertEquals(18L, AppCacheManager.measure(root));
    }

    @Test
    public void missingRootHasNoOwnedBytes() {
        File root = new File(temporaryFolder.getRoot(), "missing");

        assertEquals(0L, AppCacheManager.measure(root));
    }

    @Test
    public void measureAddsOnlyExplicitlyOwnedRoots() throws Exception {
        File runtime = temporaryFolder.newFolder("runtime");
        File images = temporaryFolder.newFolder("images");
        File unrelated = temporaryFolder.newFolder("unrelated");
        write(new File(runtime, "source.js"), 5);
        write(new File(images, "cover.bin"), 7);
        write(new File(unrelated, "other.bin"), 11);

        assertEquals(12L, AppCacheManager.measure(runtime, images));
    }

    private static void write(File file, int byteCount) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(new byte[byteCount]);
        }
    }
}
