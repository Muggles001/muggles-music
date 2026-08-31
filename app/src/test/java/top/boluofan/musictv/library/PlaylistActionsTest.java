package top.boluofan.musictv.library;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.ResponseBody;
import okio.Timeout;
import org.junit.Test;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import top.boluofan.musictv.catalog.LxApiService;
import top.boluofan.musictv.catalog.model.ListData;
import top.boluofan.musictv.catalog.model.MusicInfo;
import top.boluofan.musictv.catalog.model.Playlist;

public class PlaylistActionsTest {
    @Test
    public void loadDataReturnsCompleteLibraryPayload() {
        FakeService service = new FakeService();
        ListData data = library(playlist("first", "First"));
        service.reads.add(FakeCall.success(data));
        Recorder<ListData> recorder = new Recorder<>();

        new PlaylistActions(service.api, "user", "password", "token").loadData(recorder);

        assertSame(data, recorder.value);
        assertNull(recorder.failure);
    }

    @Test
    public void loadDetailReturnsTypedPlaylistAndPreservesPageOneContract() {
        FakeService service = new FakeService();
        Playlist playlist = playlist("remote", "Remote");
        service.details.add(FakeCall.success(playlist));
        Recorder<Playlist> recorder = new Recorder<>();

        new PlaylistActions(service.api, "u", "p", "t")
                .loadDetail("kw", "remote", recorder);

        assertSame(playlist, recorder.value);
        assertEquals("kw", service.detailSource);
        assertEquals("remote", service.detailId);
        assertEquals(1, service.detailPage);
    }

    @Test
    public void loadChoicesReadsOnceInOriginalOrderAndPassesCredentials() {
        FakeService service = new FakeService();
        service.reads.add(FakeCall.success(library(
                playlist("first", "First"), playlist("second", "Second"))));
        Recorder<List<String>> recorder = new Recorder<>();

        new PlaylistActions(service.api, "user", "password", "token").loadChoices(recorder);

        assertEquals(1, service.readCount);
        assertEquals(Arrays.asList("First", "Second"), recorder.value);
        assertNull(recorder.failure);
        assertEquals(Collections.singletonList(Arrays.asList("user", "password", "token")),
                service.readCredentials);
    }

    @Test
    public void addSongPerformsFreshReadAndSavesMutatedPayload() {
        FakeService service = new FakeService();
        Playlist first = playlist("first", "Target");
        Playlist second = playlist("second", "Target");
        MusicInfo existing = song("Existing", "kw");
        first.setSongs(new ArrayList<>(Collections.singletonList(existing)));
        ListData data = library(first, second);
        service.reads.add(FakeCall.success(data));
        service.reads.add(FakeCall.success(data));
        service.writes.add(FakeCall.success(responseBody()));
        PlaylistActions actions = new PlaylistActions(service.api, "u", "p", "t");
        Recorder<List<String>> choices = new Recorder<>();
        Recorder<PlaylistActions.AddResult> added = new Recorder<>();
        MusicInfo song = song("New", "tx");

        actions.loadChoices(choices);
        actions.addSong("Target", song, added);

        assertEquals(2, service.readCount);
        assertEquals(1, service.writeCount);
        assertSame(data, service.savedData);
        assertEquals(Arrays.asList(song, existing), first.getSongs());
        assertEquals(2, first.getSongCount());
        assertNull(second.getSongs());
        assertEquals("Target", added.value.getPlaylistName());
        assertEquals(2, added.value.getSongCount());
        assertEquals(Arrays.asList("u", "p", "t"), service.writeCredentials);
    }

    @Test
    public void duplicateSongDoesNotSave() {
        FakeService service = new FakeService();
        Playlist target = playlist("id", "Target");
        target.setSongs(new ArrayList<>(Collections.singletonList(song("Same", "kw"))));
        service.reads.add(FakeCall.success(library(target)));
        Recorder<PlaylistActions.AddResult> recorder = new Recorder<>();

        new PlaylistActions(service.api, "u", "p", "t")
                .addSong("Target", song("Same", "kw"), recorder);

        assertFailure(PlaylistActions.Failure.Kind.DUPLICATE_SONG, recorder);
        assertEquals(0, service.writeCount);
    }

    @Test
    public void addSongSkippingNullEntriesPreservesPersistedNullSlots() {
        FakeService service = new FakeService();
        Playlist target = playlist("id", "Target");
        MusicInfo existing = song("Existing", "kw");
        target.setSongs(new ArrayList<>(Arrays.asList(null, existing)));
        service.reads.add(FakeCall.success(library(target)));
        service.writes.add(FakeCall.success(responseBody()));
        MusicInfo added = song("New", "tx");
        Recorder<PlaylistActions.AddResult> recorder = new Recorder<>();

        new PlaylistActions(service.api, "u", "p", "t")
                .addSongSkippingNullEntries("Target", added, recorder);

        assertEquals(Arrays.asList(added, null, existing), target.getSongs());
        assertEquals(3, recorder.value.getSongCount());
        assertNull(recorder.failure);
        assertEquals(1, service.writeCount);
    }

    @Test
    public void missingTargetDoesNotSave() {
        FakeService service = new FakeService();
        service.reads.add(FakeCall.success(library(playlist("id", "Other"))));
        Recorder<PlaylistActions.AddResult> recorder = new Recorder<>();

        new PlaylistActions(service.api, "u", "p", "t")
                .addSong("Target", song("New", "kw"), recorder);

        assertFailure(PlaylistActions.Failure.Kind.TARGET_NOT_FOUND, recorder);
        assertEquals(0, service.writeCount);
    }

    @Test
    public void nullOrEmptyUserListsAreNoPlaylists() {
        FakeService service = new FakeService();
        service.reads.add(FakeCall.success(new ListData()));
        service.reads.add(FakeCall.success(library()));
        Recorder<List<String>> nullList = new Recorder<>();
        Recorder<PlaylistActions.AddResult> emptyList = new Recorder<>();
        PlaylistActions actions = new PlaylistActions(service.api, "u", "p", "t");

        actions.loadChoices(nullList);
        actions.addSong("Target", song("New", "kw"), emptyList);

        assertFailure(PlaylistActions.Failure.Kind.NO_PLAYLISTS, nullList);
        assertFailure(PlaylistActions.Failure.Kind.NO_PLAYLISTS, emptyList);
        assertEquals(0, service.writeCount);
    }

    @Test
    public void nullAndNonSuccessfulReadResponsesAreReadResponseFailures() {
        FakeService service = new FakeService();
        service.reads.add(FakeCall.success(null));
        service.reads.add(FakeCall.response(Response.error(503, responseBody())));
        Recorder<List<String>> nullBody = new Recorder<>();
        Recorder<List<String>> nonSuccessful = new Recorder<>();
        PlaylistActions actions = new PlaylistActions(service.api, "u", "p", "t");

        actions.loadChoices(nullBody);
        actions.loadChoices(nonSuccessful);

        assertFailure(PlaylistActions.Failure.Kind.READ_RESPONSE, nullBody);
        assertFailure(PlaylistActions.Failure.Kind.READ_RESPONSE, nonSuccessful);
    }

    @Test
    public void nonSuccessfulWriteIsWriteResponseFailure() {
        FakeService service = new FakeService();
        service.reads.add(FakeCall.success(library(playlist("id", "Target"))));
        service.writes.add(FakeCall.response(Response.error(409, responseBody())));
        Recorder<PlaylistActions.AddResult> recorder = new Recorder<>();

        new PlaylistActions(service.api, "u", "p", "t")
                .addSong("Target", song("New", "kw"), recorder);

        assertFailure(PlaylistActions.Failure.Kind.WRITE_RESPONSE, recorder);
    }

    @Test
    public void readAndWriteTransportFailuresPreserveCause() {
        IOException readError = new IOException("read");
        IOException writeError = new IOException("write");
        FakeService service = new FakeService();
        service.reads.add(FakeCall.failure(readError));
        service.reads.add(FakeCall.success(library(playlist("id", "Target"))));
        service.writes.add(FakeCall.failure(writeError));
        Recorder<List<String>> read = new Recorder<>();
        Recorder<PlaylistActions.AddResult> write = new Recorder<>();
        PlaylistActions actions = new PlaylistActions(service.api, "u", "p", "t");

        actions.loadChoices(read);
        actions.addSong("Target", song("New", "kw"), write);

        assertFailure(PlaylistActions.Failure.Kind.TRANSPORT, read);
        assertSame(readError, read.failure.getCause());
        assertFailure(PlaylistActions.Failure.Kind.TRANSPORT, write);
        assertSame(writeError, write.failure.getCause());
    }

    @Test
    public void cancelCancelsCurrentReadAndSuppressesLateCallback() {
        FakeService service = new FakeService();
        FakeCall<ListData> read = FakeCall.manual();
        service.reads.add(read);
        Recorder<List<String>> recorder = new Recorder<>();
        PlaylistActions.Operation operation = new PlaylistActions(
                service.api, "u", "p", "t").loadChoices(recorder);

        operation.cancel();
        read.dispatchResponse(Response.success(library(playlist("id", "Target"))));

        assertTrue(operation.isCanceled());
        assertTrue(read.isCanceled());
        assertNull(recorder.value);
        assertNull(recorder.failure);
    }

    @Test
    public void cancelAfterReadCancelsWriteAndSuppressesLateCallback() {
        FakeService service = new FakeService();
        service.reads.add(FakeCall.success(library(playlist("id", "Target"))));
        FakeCall<ResponseBody> write = FakeCall.manual();
        service.writes.add(write);
        Recorder<PlaylistActions.AddResult> recorder = new Recorder<>();
        PlaylistActions.Operation operation = new PlaylistActions(
                service.api, "u", "p", "t").addSong("Target", song("New", "kw"), recorder);

        operation.cancel();
        write.dispatchResponse(Response.success(responseBody()));

        assertTrue(write.isCanceled());
        assertNull(recorder.value);
        assertNull(recorder.failure);
    }

    @Test
    public void collectPlaylistCreatesAtTailAndSavesFullPayload() {
        FakeService service = new FakeService();
        Playlist existing = playlist("existing", "Existing");
        ListData data = library(existing);
        service.reads.add(FakeCall.success(data));
        service.writes.add(FakeCall.success(responseBody()));
        CollectionRecorder recorder = new CollectionRecorder();
        MusicInfo first = song("First", "kw");
        MusicInfo second = song("Second", "tx");

        new PlaylistActions(service.api, "u", "p", "t").collectPlaylist(
                "Board", "tx", "board-1", Arrays.asList(first, second), recorder);

        assertEquals(1, service.readCount);
        assertEquals(1, service.writeCount);
        assertSame(data, service.savedData);
        assertEquals(2, data.getUserList().size());
        assertSame(existing, data.getUserList().get(0));
        Playlist created = data.getUserList().get(1);
        assertTrue(created.getId().matches("playlist_\\d+"));
        assertEquals("Board", created.getName());
        assertEquals("tx", created.getSource());
        assertEquals("board-1", created.getSourceListId());
        assertEquals(Arrays.asList(first, second), created.getSongs());
        assertEquals(2, created.getSongCount());
        assertTrue(recorder.created);
        assertNull(recorder.overwriteRequired);
        assertNull(recorder.failure);
        assertEquals(Arrays.asList("u", "p", "t"), service.writeCredentials);
    }

    @Test
    public void collectPlaylistSnapshotsSongsAtInvocation() {
        FakeService service = new FakeService();
        FakeCall<ListData> read = FakeCall.manual();
        service.reads.add(read);
        service.writes.add(FakeCall.success(responseBody()));
        MusicInfo original = song("Original", "kw");
        List<MusicInfo> songs = new ArrayList<>(Collections.singletonList(original));
        CollectionRecorder recorder = new CollectionRecorder();

        new PlaylistActions(service.api, "u", "p", "t").collectPlaylist(
                "Board", "kw", "board-1", songs, recorder);
        songs.clear();
        songs.add(song("Changed", "tx"));
        read.dispatchResponse(Response.success(new ListData()));

        Playlist created = service.savedData.getUserList().get(0);
        assertEquals(Collections.singletonList(original), created.getSongs());
        assertEquals(1, created.getSongCount());
    }

    @Test
    public void existingPlaylistRequiresConfirmationBeforeFirstMatchIsOverwritten() {
        FakeService service = new FakeService();
        Playlist first = playlist("first", "Board");
        Playlist second = playlist("second", "Board");
        MusicInfo oldSong = song("Old", "kw");
        first.setSongs(new ArrayList<>(Collections.singletonList(oldSong)));
        ListData data = library(first, second);
        service.reads.add(FakeCall.success(data));
        service.writes.add(FakeCall.success(responseBody()));
        CollectionRecorder recorder = new CollectionRecorder();
        MusicInfo replacement = song("New", "tx");

        new PlaylistActions(service.api, "u", "p", "t").collectPlaylist(
                "Board", "tx", "board-2", Collections.singletonList(replacement), recorder);

        assertEquals(0, service.writeCount);
        assertEquals(Collections.singletonList(oldSong), first.getSongs());
        assertNull(second.getSongs());
        assertFalse(recorder.created);
        assertFalse(recorder.overwritten);
        assertTrue(recorder.overwriteRequired != null);

        recorder.overwriteRequired.confirm();

        assertEquals(1, service.writeCount);
        assertSame(data, service.savedData);
        assertEquals(Collections.singletonList(replacement), first.getSongs());
        assertEquals("tx", first.getSource());
        assertEquals("board-2", first.getSourceListId());
        assertNull(second.getSongs());
        assertTrue(recorder.overwritten);
    }

    @Test
    public void overwriteConfirmationUsesInvocationSnapshotOnlyOnce() {
        FakeService service = new FakeService();
        Playlist existing = playlist("id", "Board");
        service.reads.add(FakeCall.success(library(existing)));
        service.writes.add(FakeCall.success(responseBody()));
        List<MusicInfo> songs = new ArrayList<>(Collections.singletonList(song("Original", "kw")));
        CollectionRecorder recorder = new CollectionRecorder();

        new PlaylistActions(service.api, "u", "p", "t").collectPlaylist(
                "Board", "kw", "board-1", songs, recorder);
        songs.clear();
        recorder.overwriteRequired.confirm();
        recorder.overwriteRequired.confirm();

        assertEquals(1, service.writeCount);
        assertEquals("Original", existing.getSongs().get(0).getName());
    }

    @Test
    public void collectPlaylistMapsReadAndWriteResponseFailures() {
        FakeService service = new FakeService();
        service.reads.add(FakeCall.response(Response.error(503, responseBody())));
        service.reads.add(FakeCall.success(library()));
        service.writes.add(FakeCall.response(Response.error(409, responseBody())));
        CollectionRecorder read = new CollectionRecorder();
        CollectionRecorder write = new CollectionRecorder();
        PlaylistActions actions = new PlaylistActions(service.api, "u", "p", "t");

        actions.collectPlaylist("Board", "kw", "id", Collections.emptyList(), read);
        actions.collectPlaylist("Board", "kw", "id", Collections.emptyList(), write);

        assertCollectionFailure(PlaylistActions.Failure.Kind.READ_RESPONSE, read);
        assertCollectionFailure(PlaylistActions.Failure.Kind.WRITE_RESPONSE, write);
    }

    @Test
    public void collectPlaylistPreservesReadAndWriteTransportCauses() {
        IOException readError = new IOException("read");
        IOException writeError = new IOException("write");
        FakeService service = new FakeService();
        service.reads.add(FakeCall.failure(readError));
        service.reads.add(FakeCall.success(library()));
        service.writes.add(FakeCall.failure(writeError));
        CollectionRecorder read = new CollectionRecorder();
        CollectionRecorder write = new CollectionRecorder();
        PlaylistActions actions = new PlaylistActions(service.api, "u", "p", "t");

        actions.collectPlaylist("Board", "kw", "id", Collections.emptyList(), read);
        actions.collectPlaylist("Board", "kw", "id", Collections.emptyList(), write);

        assertCollectionFailure(PlaylistActions.Failure.Kind.TRANSPORT, read);
        assertSame(readError, read.failure.getCause());
        assertCollectionFailure(PlaylistActions.Failure.Kind.TRANSPORT, write);
        assertSame(writeError, write.failure.getCause());
    }

    @Test
    public void cancelCollectReadSuppressesLateCallback() {
        FakeService service = new FakeService();
        FakeCall<ListData> read = FakeCall.manual();
        service.reads.add(read);
        CollectionRecorder recorder = new CollectionRecorder();
        PlaylistActions.Operation operation = new PlaylistActions(
                service.api, "u", "p", "t").collectPlaylist(
                "Board", "kw", "id", Collections.emptyList(), recorder);

        operation.cancel();
        read.dispatchResponse(Response.success(library()));

        assertTrue(read.isCanceled());
        assertCollectionEmpty(recorder);
    }

    @Test
    public void cancelPendingOverwritePreventsConfirmation() {
        FakeService service = new FakeService();
        service.reads.add(FakeCall.success(library(playlist("id", "Board"))));
        CollectionRecorder recorder = new CollectionRecorder();
        PlaylistActions.Operation operation = new PlaylistActions(
                service.api, "u", "p", "t").collectPlaylist(
                "Board", "kw", "id", Collections.emptyList(), recorder);

        operation.cancel();
        recorder.overwriteRequired.confirm();

        assertEquals(0, service.writeCount);
        assertFalse(recorder.overwritten);
        assertNull(recorder.failure);
    }

    @Test
    public void cancelCollectWriteSuppressesLateCallback() {
        FakeService service = new FakeService();
        service.reads.add(FakeCall.success(library()));
        FakeCall<ResponseBody> write = FakeCall.manual();
        service.writes.add(write);
        CollectionRecorder recorder = new CollectionRecorder();
        PlaylistActions.Operation operation = new PlaylistActions(
                service.api, "u", "p", "t").collectPlaylist(
                "Board", "kw", "id", Collections.emptyList(), recorder);

        operation.cancel();
        write.dispatchResponse(Response.success(responseBody()));

        assertTrue(write.isCanceled());
        assertCollectionEmpty(recorder);
    }

    private static void assertFailure(PlaylistActions.Failure.Kind kind, Recorder<?> recorder) {
        assertNull(recorder.value);
        assertEquals(kind, recorder.failure.getKind());
    }

    private static void assertCollectionFailure(PlaylistActions.Failure.Kind kind,
                                                CollectionRecorder recorder) {
        assertFalse(recorder.created);
        assertFalse(recorder.overwritten);
        assertEquals(kind, recorder.failure.getKind());
    }

    private static void assertCollectionEmpty(CollectionRecorder recorder) {
        assertFalse(recorder.created);
        assertNull(recorder.overwriteRequired);
        assertFalse(recorder.overwritten);
        assertNull(recorder.failure);
    }

    private static ListData library(Playlist... playlists) {
        ListData data = new ListData();
        data.setUserList(new ArrayList<>(Arrays.asList(playlists)));
        return data;
    }

    private static Playlist playlist(String id, String name) {
        Playlist playlist = new Playlist();
        playlist.setId(id);
        playlist.setName(name);
        return playlist;
    }

    private static MusicInfo song(String name, String source) {
        MusicInfo song = new MusicInfo();
        song.setName(name);
        song.setSource(source);
        return song;
    }

    private static ResponseBody responseBody() {
        return ResponseBody.create(MediaType.parse("application/json"), "{}");
    }

    private static final class Recorder<T> implements PlaylistActions.Callback<T> {
        T value;
        PlaylistActions.Failure failure;

        @Override
        public void onSuccess(T value) {
            this.value = value;
        }

        @Override
        public void onFailure(PlaylistActions.Failure failure) {
            this.failure = failure;
        }
    }

    private static final class CollectionRecorder implements PlaylistActions.CollectCallback {
        boolean created;
        PlaylistActions.OverwriteConfirmation overwriteRequired;
        boolean overwritten;
        PlaylistActions.Failure failure;

        @Override
        public void onCreated() {
            created = true;
        }

        @Override
        public void onOverwriteRequired(PlaylistActions.OverwriteConfirmation confirmation) {
            overwriteRequired = confirmation;
        }

        @Override
        public void onOverwritten() {
            overwritten = true;
        }

        @Override
        public void onFailure(PlaylistActions.Failure failure) {
            this.failure = failure;
        }
    }

    private static final class FakeService {
        final Deque<FakeCall<ListData>> reads = new ArrayDeque<>();
        final Deque<FakeCall<ResponseBody>> writes = new ArrayDeque<>();
        final Deque<FakeCall<Playlist>> details = new ArrayDeque<>();
        final List<List<String>> readCredentials = new ArrayList<>();
        final LxApiService api;
        int readCount;
        int writeCount;
        List<String> writeCredentials;
        ListData savedData;
        String detailSource;
        String detailId;
        int detailPage;

        FakeService() {
            api = (LxApiService) Proxy.newProxyInstance(
                    LxApiService.class.getClassLoader(), new Class<?>[]{LxApiService.class},
                    (proxy, method, args) -> {
                        if ("getUserList".equals(method.getName())) {
                            readCount++;
                            readCredentials.add(credentials(args));
                            return reads.removeFirst();
                        }
                        if ("updateUserList".equals(method.getName())) {
                            writeCount++;
                            writeCredentials = credentials(args);
                            savedData = (ListData) args[3];
                            return writes.removeFirst();
                        }
                        if ("getPlaylistDetail".equals(method.getName())) {
                            detailSource = (String) args[0];
                            detailId = (String) args[1];
                            detailPage = (Integer) args[2];
                            return details.removeFirst();
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
        }

        private static List<String> credentials(Object[] args) {
            return Arrays.asList((String) args[0], (String) args[1], (String) args[2]);
        }
    }

    private static final class FakeCall<T> implements Call<T> {
        private final Response<T> response;
        private final Throwable error;
        private final boolean manual;
        private Callback<T> callback;
        private boolean executed;
        private boolean canceled;

        private FakeCall(Response<T> response, Throwable error, boolean manual) {
            this.response = response;
            this.error = error;
            this.manual = manual;
        }

        static <T> FakeCall<T> success(T value) {
            return response(Response.success(value));
        }

        static <T> FakeCall<T> response(Response<T> response) {
            return new FakeCall<>(response, null, false);
        }

        static <T> FakeCall<T> failure(Throwable error) {
            return new FakeCall<>(null, error, false);
        }

        static <T> FakeCall<T> manual() {
            return new FakeCall<>(null, null, true);
        }

        @Override
        public Response<T> execute() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void enqueue(Callback<T> callback) {
            executed = true;
            this.callback = callback;
            if (manual) return;
            if (error != null) callback.onFailure(this, error);
            else callback.onResponse(this, response);
        }

        void dispatchResponse(Response<T> response) {
            callback.onResponse(this, response);
        }

        @Override
        public boolean isExecuted() {
            return executed;
        }

        @Override
        public void cancel() {
            canceled = true;
        }

        @Override
        public boolean isCanceled() {
            return canceled;
        }

        @Override
        public Call<T> clone() {
            return new FakeCall<>(response, error, manual);
        }

        @Override
        public Request request() {
            return new Request.Builder().url("http://127.0.0.1/").build();
        }

        @Override
        public Timeout timeout() {
            return Timeout.NONE;
        }
    }
}
