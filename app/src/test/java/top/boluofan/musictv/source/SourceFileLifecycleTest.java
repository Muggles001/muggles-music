package top.boluofan.musictv.source;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SourceFileLifecycleTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void artifactNameDependsOnlyOnScriptContent() throws Exception {
        File firstDirectory = temporaryFolder.newFolder("first");
        File secondDirectory = temporaryFolder.newFolder("second");

        File first = SourceFileLifecycle.persist(firstDirectory, "script contents");
        File second = SourceFileLifecycle.persist(secondDirectory, "script contents");

        assertEquals(first.getName(), second.getName());
        assertEquals(SourceFileLifecycle.sha256("script contents") + ".js", first.getName());
    }

    @Test
    public void distinctVersionsNeverOverwriteEachOther() throws Exception {
        File directory = temporaryFolder.newFolder("versions");

        File first = SourceFileLifecycle.persist(directory, "same-id version A");
        File second = SourceFileLifecycle.persist(directory, "same-id version B");

        assertNotEquals(first.getName(), second.getName());
        assertEquals("same-id version A", SourceFileLifecycle.read(first));
        assertEquals("same-id version B", SourceFileLifecycle.read(second));
    }

    @Test
    public void reconciliationKeepsOnlyActiveAndPrevious() throws Exception {
        File directory = temporaryFolder.newFolder("rotation");
        File first = SourceFileLifecycle.persist(directory, "A");
        SourceFileLifecycle.reconcile(directory, Collections.singleton(first));
        File second = SourceFileLifecycle.persist(directory, "B");
        SourceFileLifecycle.reconcile(directory,
                new HashSet<>(Arrays.asList(first, second)));
        File third = SourceFileLifecycle.persist(directory, "C");
        write(new File(directory, "abandoned.pending"), "partial");
        write(new File(directory, "old-version.js"), "old");

        SourceFileLifecycle.reconcile(directory,
                new HashSet<>(Arrays.asList(second, third)));

        assertFalse(first.exists());
        assertTrue(second.exists());
        assertTrue(third.exists());
        assertArrayEquals(sorted(second.getName(), third.getName()), sortedNames(directory));
    }

    @Test
    public void interruptedWriteCanBeRetried() throws Exception {
        File directory = temporaryFolder.newFolder("retry");
        String script = "complete script";
        File pending = new File(directory, SourceFileLifecycle.sha256(script) + ".pending");
        write(pending, "partial");

        File target = SourceFileLifecycle.persist(directory, script);

        assertEquals(script, SourceFileLifecycle.read(target));
        assertFalse(pending.exists());
    }

    @Test
    public void failedCommitArtifactIsRemovedAndRetryConverges() throws Exception {
        File directory = temporaryFolder.newFolder("commit-failure");
        File active = SourceFileLifecycle.persist(directory, "A");
        File uncommitted = SourceFileLifecycle.persist(directory, "B");

        SourceFileLifecycle.reconcile(directory, Collections.singleton(active));
        assertFalse(uncommitted.exists());
        File retried = SourceFileLifecycle.persist(directory, "B");
        SourceFileLifecycle.reconcile(directory,
                new HashSet<>(Arrays.asList(active, retried)));

        assertTrue(active.exists());
        assertTrue(retried.exists());
        assertArrayEquals(sorted(active.getName(), retried.getName()), sortedNames(directory));
    }

    @Test
    public void corruptedArtifactIsRepairedOnRetry() throws Exception {
        File directory = temporaryFolder.newFolder("repair");
        String script = "expected script";
        File target = new File(directory, SourceFileLifecycle.sha256(script) + ".js");
        write(target, "corrupt");

        File repaired = SourceFileLifecycle.persist(directory, script);

        assertEquals(target.getCanonicalPath(), repaired.getCanonicalPath());
        assertEquals(script, SourceFileLifecycle.read(repaired));
    }

    @Test
    public void legacyReferencedPathIsRetainedUntilItIsNoLongerReferenced() throws Exception {
        File directory = temporaryFolder.newFolder("legacy");
        File legacy = new File(directory, "legacy-source-id.js");
        write(legacy, "legacy");
        File orphan = new File(directory, "older-source-id.js");
        write(orphan, "orphan");

        SourceFileLifecycle.reconcile(directory, Collections.singleton(legacy));

        assertTrue(legacy.exists());
        assertFalse(orphan.exists());
    }

    @Test
    public void legacyPathIsRemovedAfterContentAddressedMigration() throws Exception {
        File directory = temporaryFolder.newFolder("migration");
        File legacy = new File(directory, "legacy-source-id.js");
        write(legacy, "legacy");

        File migrated = SourceFileLifecycle.persist(directory, "legacy");
        SourceFileLifecycle.reconcile(directory, Collections.singleton(migrated));

        assertFalse(legacy.exists());
        assertTrue(migrated.exists());
    }

    @Test
    public void clearRemovesOwnedContentsAndIsIdempotent() throws Exception {
        File directory = temporaryFolder.newFolder("clear");
        SourceFileLifecycle.persist(directory, "A");
        File nested = new File(directory, "nested");
        assertTrue(nested.mkdir());
        write(new File(nested, "orphan"), "value");

        SourceFileLifecycle.clear(directory);
        SourceFileLifecycle.clear(directory);

        assertArrayEquals(new String[0], sortedNames(directory));
    }

    private void write(File file, String value) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String[] sortedNames(File directory) {
        String[] names = directory.list();
        if (names == null) return new String[0];
        Arrays.sort(names);
        return names;
    }

    private String[] sorted(String... names) {
        Arrays.sort(names);
        return names;
    }
}
