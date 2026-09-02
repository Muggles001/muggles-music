package top.boluofan.musictv.library;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;

import okhttp3.Request;
import okhttp3.ResponseBody;
import okio.Timeout;
import org.junit.Test;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import top.boluofan.musictv.catalog.LxApiService;
import top.boluofan.musictv.catalog.PlaylistLinkResolver;
import top.boluofan.musictv.catalog.model.ListData;
import top.boluofan.musictv.catalog.model.MusicInfo;
import top.boluofan.musictv.catalog.model.Playlist;

public class PlaylistImportActionsTest {
    @Test
    public void prepareAndCommitRefreshSameSourceIdentityAndReportSkippedSongs() {
        FakeService service = new FakeService();
        Playlist detail = playlist(null, "平台名称", "wy", "123");
        MusicInfo valid = song("valid", "wy", "song-1");
        MusicInfo invalid = song("missing-id", "wy", null);
        detail.setSongs(Arrays.asList(valid, invalid));
        detail.setSongCount(2);
        service.details.add(FakeCall.success(detail));

        Playlist existing = playlist("local-id", "本地名称", "wy", "123");
        ListData firstRead = data(existing);
        ListData commitRead = data(existing);
        service.reads.add(FakeCall.success(firstRead));
        service.reads.add(FakeCall.success(commitRead));
        service.writes.add(FakeCall.success(ResponseBody.create(null, "{}")));

        PlaylistImportActions actions = new PlaylistImportActions(
                service.api, "u", "p", "t", new PlaylistLinkResolver(),
                Runnable::run, Runnable::run,
                new LibraryMutationCoordinator());
        Recorder<PlaylistImportPreview> preview = new Recorder<>();

        actions.prepare("https://music.163.com/playlist?id=123", "auto", preview);

        assertNull(preview.failure);
        assertNotNull(preview.value);
        assertTrue(preview.value.isRefresh());
        assertEquals("本地名称", preview.value.getName());
        assertEquals(1, preview.value.getImportableCount());
        assertEquals(1, preview.value.getSkippedCount());

        Recorder<PlaylistImportActions.ImportResult> result = new Recorder<>();
        actions.commit(preview.value, result);

        assertNull(result.failure);
        assertEquals("local-id", result.value.getPlaylistId());
        assertTrue(result.value.isRefreshed());
        assertEquals(1, service.writeCount);
        assertEquals("本地名称", service.savedData.getUserList().get(0).getName());
        assertEquals("valid", service.savedData.getUserList().get(0).getSongs().get(0).getName());
    }

    @Test
    public void importedPlatformDoesNotDependOnPlaybackSourceCapabilities() {
        FakeService service = new FakeService();
        Playlist detail = playlist(null, "QQ 歌单", "tx", "998877");
        detail.setSongs(Collections.singletonList(song("song", "tx", "song-id")));
        detail.setSongCount(1);
        service.details.add(FakeCall.success(detail));
        service.reads.add(FakeCall.success(new ListData()));
        PlaylistImportActions actions = new PlaylistImportActions(
                service.api, "", "", "", new PlaylistLinkResolver(),
                Runnable::run, Runnable::run,
                new LibraryMutationCoordinator());
        Recorder<PlaylistImportPreview> recorder = new Recorder<>();

        actions.prepare("https://y.qq.com/n/ryqq/playlist/998877", "auto", recorder);

        assertNull(recorder.failure);
        assertEquals("tx", recorder.value.getReference().getSource());
        assertEquals(1, service.detailCount);
    }

    private static ListData data(Playlist playlist) {
        ListData data = new ListData();
        data.setUserList(new ArrayList<>(Collections.singletonList(playlist)));
        return data;
    }

    private static Playlist playlist(String id, String name, String source, String sourceListId) {
        Playlist playlist = new Playlist();
        playlist.setId(id);
        playlist.setName(name);
        playlist.setSource(source);
        playlist.setSourceListId(sourceListId);
        return playlist;
    }

    private static MusicInfo song(String name, String source, String id) {
        MusicInfo song = new MusicInfo();
        song.setName(name);
        song.setSource(source);
        song.setSongmid(id);
        return song;
    }

    private static final class Recorder<T> implements PlaylistImportActions.Callback<T> {
        private T value;
        private PlaylistImportActions.Failure failure;

        @Override
        public void onSuccess(T value) {
            this.value = value;
        }

        @Override
        public void onFailure(PlaylistImportActions.Failure failure) {
            this.failure = failure;
        }
    }

    private static final class FakeService {
        private final Deque<FakeCall<ListData>> reads = new ArrayDeque<>();
        private final Deque<FakeCall<ResponseBody>> writes = new ArrayDeque<>();
        private final Deque<FakeCall<Playlist>> details = new ArrayDeque<>();
        private final LxApiService api;
        private int detailCount;
        private int writeCount;
        private ListData savedData;

        private FakeService() {
            api = (LxApiService) Proxy.newProxyInstance(
                    LxApiService.class.getClassLoader(), new Class<?>[]{LxApiService.class},
                    (proxy, method, args) -> {
                        if ("getPlaylistDetail".equals(method.getName())) {
                            detailCount++;
                            return details.removeFirst();
                        }
                        if ("getUserList".equals(method.getName())) return reads.removeFirst();
                        if ("updateUserList".equals(method.getName())) {
                            writeCount++;
                            savedData = (ListData) args[3];
                            return writes.removeFirst();
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
        }
    }

    private static final class FakeCall<T> implements Call<T> {
        private final Response<T> response;
        private boolean executed;
        private boolean canceled;

        private FakeCall(Response<T> response) {
            this.response = response;
        }

        private static <T> FakeCall<T> success(T value) {
            return new FakeCall<>(Response.success(value));
        }

        @Override
        public Response<T> execute() {
            executed = true;
            return response;
        }

        @Override
        public void enqueue(Callback<T> callback) {
            executed = true;
            callback.onResponse(this, response);
        }

        @Override
        public boolean isExecuted() { return executed; }

        @Override
        public void cancel() { canceled = true; }

        @Override
        public boolean isCanceled() { return canceled; }

        @Override
        public Call<T> clone() { return new FakeCall<>(response); }

        @Override
        public Request request() {
            return new Request.Builder().url("http://127.0.0.1/").build();
        }

        @Override
        public Timeout timeout() { return Timeout.NONE; }
    }
}
