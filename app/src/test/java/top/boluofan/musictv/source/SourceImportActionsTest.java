package top.boluofan.musictv.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.google.gson.JsonObject;
import java.io.IOException;
import org.junit.Test;

public class SourceImportActionsTest {
    @Test
    public void downloadsThenActivatesAndReturnsValidatedResult() {
        ImportedSource source = source("A");
        FakeRuntime runtime = new FakeRuntime();
        Recorder recorder = new Recorder();
        SourceImportActions actions = new SourceImportActions(
                Runnable::run, url -> source, runtime, () -> true);

        actions.importSource("https://example.test/source.js", recorder);
        JsonObject capabilities = new JsonObject();
        capabilities.add("kw", new JsonObject());
        runtime.callback.onLoaded(capabilities);

        assertSame(source, runtime.activated);
        assertSame(source, recorder.source);
        assertSame(capabilities, recorder.capabilities);
        assertNull(recorder.error);
        assertEquals(true, actions.hasPrevious());
    }

    @Test
    public void downloadFailureUsesOneFailureChannel() {
        Recorder recorder = new Recorder();
        SourceImportActions actions = new SourceImportActions(
                Runnable::run,
                url -> { throw new IOException("download failed"); },
                new FakeRuntime(), () -> false);

        actions.importSource("https://example.test/source.js", recorder);

        assertEquals("download failed", recorder.error);
        assertNull(recorder.source);
    }

    @Test
    public void cancellationSuppressesLateRuntimeCallback() {
        FakeRuntime runtime = new FakeRuntime();
        Recorder recorder = new Recorder();
        SourceImportActions actions = new SourceImportActions(
                Runnable::run, url -> source("A"), runtime, () -> false);

        SourceImportActions.Operation operation =
                actions.importSource("https://example.test/source.js", recorder);
        operation.cancel();
        runtime.callback.onLoaded(new JsonObject());

        assertNull(recorder.source);
        assertNull(recorder.error);
    }

    @Test
    public void activeAndRollbackDelegateToRuntimeOwner() {
        FakeRuntime runtime = new FakeRuntime();
        runtime.active = source("active");
        runtime.restored = source("previous");
        SourceImportActions actions = new SourceImportActions(
                Runnable::run, url -> null, runtime, () -> true);

        assertSame(runtime.active, actions.getActiveSource());
        assertSame(runtime.restored, actions.rollback());
    }

    private static ImportedSource source(String version) {
        String script = "/*!\n * @name Test Source\n * @version " + version
                + "\n */\nconst version = '" + version + "';";
        return new ImportedSource(
                SourceScriptMetadata.parse(script), "https://example.test/source.js",
                script, new JsonObject());
    }

    private static final class Recorder implements SourceImportActions.Callback {
        ImportedSource source;
        JsonObject capabilities;
        String error;

        @Override
        public void onSuccess(ImportedSource source, JsonObject capabilities) {
            this.source = source;
            this.capabilities = capabilities;
        }

        @Override
        public void onFailure(String error) {
            this.error = error;
        }
    }

    private static final class FakeRuntime implements SourceImportActions.Runtime {
        ImportedSource activated;
        ImportedSource active;
        ImportedSource restored;
        SourceRuntimeEngine.LoadCallback callback;

        @Override
        public void activate(ImportedSource source, SourceRuntimeEngine.LoadCallback callback) {
            activated = source;
            this.callback = callback;
        }

        @Override
        public ImportedSource active() {
            return active;
        }

        @Override
        public ImportedSource rollback() {
            return restored;
        }
    }
}
