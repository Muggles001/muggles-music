package top.boluofan.musictv.player.lyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LyricRequestCoordinatorTest {
    private FakeServer server;
    private FakeScraper scraper;
    private FakeScheduler scheduler;
    private RecordingListener listener;
    private LyricRequestCoordinator coordinator;

    @Before
    public void setUp() {
        server = new FakeServer();
        scraper = new FakeScraper();
        scheduler = new FakeScheduler();
        listener = new RecordingListener();
        coordinator = new LyricRequestCoordinator(
                server,
                scraper,
                scheduler,
                (preferred, alternative) -> text(alternative).length() > text(preferred).length()
                        ? text(alternative) : text(preferred),
                listener,
                25L);
    }

    @Test
    public void regularRequestWithSongmidUsesServerFirst() {
        LyricRequestCoordinator.Request request = request("mid", false);

        coordinator.load(request);

        assertEquals(Collections.singletonList("loading:mid"), listener.events);
        assertSame(request, server.request);
        assertEquals(0, scraper.startCount);
        server.callback.onSuccess("short", "longer lyric", "translated");
        assertEquals("server:longer lyric:translated", listener.lastEvent());
        assertEquals(0, scraper.startCount);
    }

    @Test
    public void selectedTranslationIsNotEmittedTwice() {
        coordinator.load(request("mid", false));

        server.callback.onSuccess("translated only", "", "translated only");

        assertEquals("server:translated only:", listener.lastEvent());
    }

    @Test
    public void synchronousServerCallbackDoesNotLeaveReturnedHandleActive() {
        server.synchronousSuccess = true;

        coordinator.load(request("mid", false));

        assertEquals("server:sync lyric:", listener.lastEvent());
        assertTrue(server.cancelable.cancelled);
    }

    @Test
    public void forceAndBlankSongmidStartExternalWithoutServer() {
        coordinator.load(request("mid", true));

        assertEquals(0, server.startCount);
        assertEquals(1, scraper.startCount);
        assertEquals(25L, scheduler.lastDelayMs);

        coordinator.load(request("   ", false));

        assertEquals(0, server.startCount);
        assertEquals(2, scraper.startCount);
        assertTrue(scheduler.tasks.get(0).cancelled);
    }

    @Test
    public void publicConstructorUsesFixedFifteenSecondTimeout() {
        LyricRequestCoordinator productionCoordinator = new LyricRequestCoordinator(
                server, scraper, scheduler, (preferred, alternative) -> text(preferred), listener);

        productionCoordinator.load(request("mid", true));

        assertEquals(15_000L, scheduler.lastDelayMs);
    }

    @Test
    public void synchronousSchedulerTimeoutStopsBeforeScraperStarts() {
        scheduler.synchronousRun = true;

        coordinator.load(request("mid", true));

        assertEquals("timeout:mid", listener.lastEvent());
        assertEquals(0, scraper.startCount);
        assertTrue(scheduler.lastTask().cancelled);
    }

    @Test
    public void emptyServerLyricsFallBackToExternal() {
        coordinator.load(request("mid", false));

        server.callback.onSuccess(" ", null, "");

        assertEquals(1, scraper.startCount);
        assertEquals(25L, scheduler.lastDelayMs);
        assertEquals(Collections.singletonList("loading:mid"), listener.events);
    }

    @Test
    public void serverFailuresFallBackToExternal() {
        coordinator.load(request("first", false));
        server.callback.onFailure("http 500");

        assertEquals(1, scraper.startCount);
        assertFalse(listener.containsPrefix("error:"));

        coordinator.load(request("second", false));
        server.callback.onFailure("socket closed");

        assertEquals(2, scraper.startCount);
        assertFalse(listener.containsPrefix("error:"));
    }

    @Test
    public void serverFallbackAndLateServerSuccessTerminateOnlyOnce() {
        coordinator.load(request("mid", false));
        LyricRequestCoordinator.ServerCallback serverCallback = server.callback;
        serverCallback.onFailure("response failure");

        scraper.callback.onSuccess("artist", "pic", "lyrics");
        serverCallback.onSuccess("late server lyrics", "", "");

        assertEquals(1, listener.countPrefix("external:"));
        assertEquals(0, listener.countPrefix("server:"));
    }

    @Test
    public void externalSuccessCancelsTimeoutAndTerminatesOnce() {
        coordinator.load(request("mid", true));
        FakeScheduler.Task timeout = scheduler.lastTask();
        LyricRequestCoordinator.ExternalCallback callback = scraper.callback;

        callback.onSuccess("artist", "pic", "lyrics");
        callback.onFailure("late failure");
        timeout.runEvenIfCancelled();

        assertTrue(timeout.cancelled);
        assertEquals(1, listener.countPrefix("external:"));
        assertEquals(0, listener.countPrefix("error:"));
        assertEquals(0, listener.countPrefix("timeout:"));
    }

    @Test
    public void synchronousExternalCallbackCancelsAlreadyInstalledTimeout() {
        scraper.synchronousSuccess = true;

        coordinator.load(request("mid", true));

        assertEquals("external:sync artist:sync pic:sync lyrics", listener.lastEvent());
        assertTrue(scheduler.lastTask().cancelled);
    }

    @Test
    public void externalFailureCancelsTimeoutAndTerminatesOnce() {
        coordinator.load(request("mid", true));
        FakeScheduler.Task timeout = scheduler.lastTask();
        LyricRequestCoordinator.ExternalCallback callback = scraper.callback;

        callback.onFailure("not found");
        callback.onSuccess("late", "late", "late");
        timeout.runEvenIfCancelled();

        assertTrue(timeout.cancelled);
        assertEquals("error:not found", listener.lastEvent());
        assertEquals(1, listener.countPrefix("error:"));
        assertEquals(0, listener.countPrefix("external:"));
        assertEquals(0, listener.countPrefix("timeout:"));
    }

    @Test
    public void timeoutTerminatesAndRejectsLateExternalCallbacks() {
        LyricRequestCoordinator.Request request = request("mid", true);
        coordinator.load(request);
        LyricRequestCoordinator.ExternalCallback callback = scraper.callback;

        scheduler.lastTask().run();
        callback.onSuccess("late", "late", "late");
        callback.onFailure("late");

        assertEquals("timeout:mid", listener.lastEvent());
        assertEquals(1, listener.countPrefix("timeout:"));
        assertEquals(0, listener.countPrefix("external:"));
        assertEquals(0, listener.countPrefix("error:"));
    }

    @Test
    public void newLoadCancelsServerAndRejectsOldServerCallback() {
        coordinator.load(request("old", false));
        FakeCancelable oldCall = server.cancelable;
        LyricRequestCoordinator.ServerCallback oldCallback = server.callback;

        coordinator.load(request("new", false));
        oldCallback.onSuccess("old lyric", "", "");
        oldCallback.onFailure("late");
        server.callback.onSuccess("new lyric", "", "new translation");

        assertTrue(oldCall.cancelled);
        assertFalse(listener.events.contains("server:old lyric:"));
        assertEquals("server:new lyric:new translation", listener.lastEvent());
    }

    @Test
    public void newLoadCancelsTimeoutAndRejectsOldExternalCallbacks() {
        coordinator.load(request("old", true));
        FakeScheduler.Task oldTimeout = scheduler.lastTask();
        LyricRequestCoordinator.ExternalCallback oldCallback = scraper.callback;

        coordinator.load(request("new", true));
        oldCallback.onSuccess("old", "old", "old");
        oldCallback.onFailure("old failure");
        oldTimeout.runEvenIfCancelled();
        scraper.callback.onSuccess("new", "new-pic", "new lyrics");

        assertTrue(oldTimeout.cancelled);
        assertFalse(listener.events.contains("external:old:old:old"));
        assertEquals("external:new:new-pic:new lyrics", listener.lastEvent());
    }

    @Test
    public void cancelIsIdempotentAndRejectsAllLateCallbacks() {
        coordinator.load(request("server", false));
        FakeCancelable serverCall = server.cancelable;
        LyricRequestCoordinator.ServerCallback serverCallback = server.callback;
        coordinator.cancel();
        coordinator.cancel();
        serverCallback.onSuccess("late", "", "");

        coordinator.load(request("external", true));
        FakeScheduler.Task timeout = scheduler.lastTask();
        LyricRequestCoordinator.ExternalCallback externalCallback = scraper.callback;
        coordinator.cancel();
        coordinator.cancel();
        externalCallback.onSuccess("late", "late", "late");
        externalCallback.onFailure("late");
        timeout.runEvenIfCancelled();

        assertTrue(serverCall.cancelled);
        assertEquals(1, serverCall.cancelCount);
        assertTrue(timeout.cancelled);
        assertEquals(1, timeout.cancelCount);
        assertEquals(0, listener.countPrefix("server:"));
        assertEquals(0, listener.countPrefix("external:"));
        assertEquals(0, listener.countPrefix("timeout:"));
        assertEquals(0, listener.countPrefix("error:"));
    }

    @Test
    public void requestDefensivelyCopiesParams() {
        Map<String, String> params = new HashMap<>();
        params.put("source", "kw");
        LyricRequestCoordinator.Request request = new LyricRequestCoordinator.Request(
                "kw", "mid", "media", "song", "artist", params, false);

        params.put("source", "changed");

        assertEquals("kw", request.getSource());
        assertEquals("mid", request.getSongmid());
        assertEquals("media", request.getMediaId());
        assertEquals("song", request.getSongName());
        assertEquals("artist", request.getArtist());
        assertFalse(request.isForceExternal());
        assertEquals("kw", request.getParams().get("source"));
        try {
            request.getParams().put("new", "value");
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("params must be immutable");
    }

    private static LyricRequestCoordinator.Request request(String songmid, boolean forceExternal) {
        Map<String, String> params = new HashMap<>();
        params.put("source", "kw");
        params.put("songmid", songmid);
        return new LyricRequestCoordinator.Request(
                "kw", songmid, "media-" + songmid, "song-" + songmid,
                "artist-" + songmid, params, forceExternal);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static class FakeCancelable implements LyricRequestCoordinator.Cancelable {
        boolean cancelled;
        int cancelCount;

        @Override
        public void cancel() {
            if (cancelled) return;
            cancelled = true;
            cancelCount++;
        }
    }

    private static final class FakeServer implements LyricRequestCoordinator.ServerGateway {
        int startCount;
        boolean synchronousSuccess;
        LyricRequestCoordinator.Request request;
        LyricRequestCoordinator.ServerCallback callback;
        FakeCancelable cancelable;

        @Override
        public LyricRequestCoordinator.Cancelable request(
                LyricRequestCoordinator.Request request,
                LyricRequestCoordinator.ServerCallback callback) {
            startCount++;
            this.request = request;
            this.callback = callback;
            cancelable = new FakeCancelable();
            if (synchronousSuccess) callback.onSuccess("sync lyric", "", "");
            return cancelable;
        }
    }

    private static final class FakeScraper implements LyricRequestCoordinator.ExternalScraper {
        int startCount;
        boolean synchronousSuccess;
        LyricRequestCoordinator.Request request;
        LyricRequestCoordinator.ExternalCallback callback;

        @Override
        public void scrape(LyricRequestCoordinator.Request request,
                           LyricRequestCoordinator.ExternalCallback callback) {
            startCount++;
            this.request = request;
            this.callback = callback;
            if (synchronousSuccess) callback.onSuccess(
                    "sync artist", "sync pic", "sync lyrics");
        }
    }

    private static final class FakeScheduler implements LyricRequestCoordinator.Scheduler {
        final List<Task> tasks = new ArrayList<>();
        long lastDelayMs;
        boolean synchronousRun;

        @Override
        public LyricRequestCoordinator.Cancelable schedule(long delayMs, Runnable runnable) {
            lastDelayMs = delayMs;
            Task task = new Task(runnable);
            tasks.add(task);
            if (synchronousRun) task.run();
            return task;
        }

        Task lastTask() {
            return tasks.get(tasks.size() - 1);
        }

        private static final class Task extends FakeCancelable {
            private final Runnable runnable;

            Task(Runnable runnable) {
                this.runnable = runnable;
            }

            void run() {
                if (!cancelled) runnable.run();
            }

            void runEvenIfCancelled() {
                runnable.run();
            }
        }
    }

    private static final class RecordingListener implements LyricRequestCoordinator.Listener {
        final List<String> events = new ArrayList<>();

        @Override
        public void onLoading(LyricRequestCoordinator.Request request) {
            events.add("loading:" + request.getSongmid().trim());
        }

        @Override
        public void onServerLyrics(LyricRequestCoordinator.Request request, String primary,
                                   String translated) {
            events.add("server:" + primary + ":" + translated);
        }

        @Override
        public void onExternalResult(LyricRequestCoordinator.Request request, String artist,
                                     String picUrl, String lyrics) {
            events.add("external:" + artist + ":" + picUrl + ":" + lyrics);
        }

        @Override
        public void onTimeout(LyricRequestCoordinator.Request request) {
            events.add("timeout:" + request.getSongmid());
        }

        @Override
        public void onError(LyricRequestCoordinator.Request request, String message) {
            events.add("error:" + message);
        }

        String lastEvent() {
            return events.get(events.size() - 1);
        }

        int countPrefix(String prefix) {
            int count = 0;
            for (String event : events) {
                if (event.startsWith(prefix)) count++;
            }
            return count;
        }

        boolean containsPrefix(String prefix) {
            return countPrefix(prefix) > 0;
        }
    }
}
