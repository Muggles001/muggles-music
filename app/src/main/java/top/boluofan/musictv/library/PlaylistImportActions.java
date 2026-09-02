package top.boluofan.musictv.library;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;
import top.boluofan.musictv.catalog.LxApiService;
import top.boluofan.musictv.catalog.PlaylistLinkResolver;
import top.boluofan.musictv.catalog.PlaylistReference;
import top.boluofan.musictv.catalog.model.ListData;
import top.boluofan.musictv.catalog.model.MusicInfo;
import top.boluofan.musictv.catalog.model.Playlist;

public final class PlaylistImportActions {
    private static final int MAX_PAGES = 100;
    private final LxApiService api;
    private final String username;
    private final String password;
    private final String token;
    private final PlaylistLinkResolver linkResolver;
    private final Executor background;
    private final Executor callbacks;
    private final LibraryMutationCoordinator mutations;

    public PlaylistImportActions(LxApiService api, String username, String password, String token,
                                 PlaylistLinkResolver linkResolver, Executor background,
                                 Executor callbacks) {
        this(api, username, password, token, linkResolver, background, callbacks,
                LibraryMutationCoordinator.shared());
    }

    PlaylistImportActions(LxApiService api, String username, String password, String token,
                          PlaylistLinkResolver linkResolver, Executor background,
                          Executor callbacks,
                          LibraryMutationCoordinator mutations) {
        this.api = api;
        this.username = username;
        this.password = password;
        this.token = token;
        this.linkResolver = linkResolver;
        this.background = background;
        this.callbacks = callbacks;
        this.mutations = mutations;
    }

    public Operation prepare(String input, String sourceHint, Callback<PlaylistImportPreview> callback) {
        ActiveOperation operation = new ActiveOperation();
        background.execute(() -> {
            if (operation.isCanceled()) return;
            PlaylistReference reference;
            try {
                reference = linkResolver.resolve(input, sourceHint);
            } catch (PlaylistLinkResolver.Failure failure) {
                postFailure(operation, callback, Failure.Kind.INVALID_INPUT, failure);
                return;
            }
            loadPage(operation, callback, reference, 1, null,
                    new LinkedHashMap<>(), 0, null);
        });
        return operation;
    }

    private void loadPage(ActiveOperation operation, Callback<PlaylistImportPreview> callback,
                          PlaylistReference reference, int page, Playlist metadata,
                          Map<String, MusicInfo> songs, int observed, String previousSignature) {
        Call<Playlist> call = api.getPlaylistDetail(
                reference.getSource(), reference.getDetailRequestId(), page);
        if (!operation.replace(call)) return;
        call.enqueue(new retrofit2.Callback<Playlist>() {
            @Override
            public void onResponse(Call<Playlist> request, Response<Playlist> response) {
                if (operation.isCanceled()) return;
                Playlist pageData = response.body();
                if (!response.isSuccessful() || pageData == null) {
                    fail(operation, callback, Failure.Kind.DETAIL_RESPONSE, null);
                    return;
                }
                Playlist base = metadata == null ? pageData : metadata;
                List<MusicInfo> pageSongs = pageData.getSongs() == null
                        ? new ArrayList<>() : pageData.getSongs();
                String signature = pageSignature(pageSongs);
                if (page > 1 && signature.equals(previousSignature)) {
                    fail(operation, callback, Failure.Kind.INCOMPLETE_PLAYLIST, null);
                    return;
                }
                for (MusicInfo song : pageSongs) {
                    String identity = songIdentity(song);
                    if (identity != null && !songs.containsKey(identity)) songs.put(identity, song);
                }
                int nextObserved = observed + pageSongs.size();
                int expected = Math.max(base.getSongCount(), nextObserved);
                if (base.getSongCount() > nextObserved && !pageSongs.isEmpty()
                        && page < MAX_PAGES) {
                    loadPage(operation, callback, reference, page + 1, base, songs,
                            nextObserved, signature);
                    return;
                }
                if (base.getSongCount() > nextObserved) {
                    fail(operation, callback, Failure.Kind.INCOMPLETE_PLAYLIST, null);
                    return;
                }
                if (songs.isEmpty()) {
                    fail(operation, callback, Failure.Kind.NO_IMPORTABLE_SONGS, null);
                    return;
                }
                PlaylistImportPreview preview = new PlaylistImportPreview(
                        reference, null, safeName(base.getName()), base.getPicUrl(),
                        base.getDesc(), base.getCreator(), expected,
                        new ArrayList<>(songs.values()));
                loadExistingIdentity(operation, callback, preview);
            }

            @Override
            public void onFailure(Call<Playlist> request, Throwable error) {
                fail(operation, callback, Failure.Kind.TRANSPORT, error);
            }
        });
    }

    private void loadExistingIdentity(ActiveOperation operation,
                                      Callback<PlaylistImportPreview> callback,
                                      PlaylistImportPreview preview) {
        Call<ListData> call = api.getUserList(username, password, token);
        if (!operation.replace(call)) return;
        call.enqueue(new retrofit2.Callback<ListData>() {
            @Override
            public void onResponse(Call<ListData> request, Response<ListData> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    fail(operation, callback, Failure.Kind.READ_RESPONSE, null);
                    return;
                }
                Playlist existing = PlaylistImportMutations.findBySourceIdentity(
                        response.body().getUserList(), preview.getReference());
                PlaylistImportPreview result = existing == null ? preview
                        : preview.withExistingPlaylist(existing.getId(), existing.getName());
                succeed(operation, callback, result);
            }

            @Override
            public void onFailure(Call<ListData> request, Throwable error) {
                fail(operation, callback, Failure.Kind.TRANSPORT, error);
            }
        });
    }

    public Operation commit(PlaylistImportPreview preview, Callback<ImportResult> callback) {
        ActiveOperation operation = new ActiveOperation();
        if (preview == null || preview.getSongs().isEmpty()) {
            fail(operation, callback, Failure.Kind.NO_IMPORTABLE_SONGS, null);
            return operation;
        }
        mutations.enqueue(completion -> {
            operation.setQueueCompletion(completion);
            if (operation.isCanceled()) {
                operation.finishQueue();
                return;
            }
            readAndCommit(operation, preview, callback);
        });
        return operation;
    }

    private void readAndCommit(ActiveOperation operation, PlaylistImportPreview preview,
                               Callback<ImportResult> callback) {
        Call<ListData> read = api.getUserList(username, password, token);
        if (!operation.replace(read)) {
            operation.finishQueue();
            return;
        }
        read.enqueue(new retrofit2.Callback<ListData>() {
            @Override
            public void onResponse(Call<ListData> request, Response<ListData> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    failAndFinish(operation, callback, Failure.Kind.READ_RESPONSE, null);
                    return;
                }
                ListData data = response.body();
                Playlist target = PlaylistImportMutations.apply(data, preview);
                if (target == null) {
                    failAndFinish(operation, callback, Failure.Kind.NO_IMPORTABLE_SONGS, null);
                    return;
                }
                write(operation, preview, data, target, callback);
            }

            @Override
            public void onFailure(Call<ListData> request, Throwable error) {
                failAndFinish(operation, callback, Failure.Kind.TRANSPORT, error);
            }
        });
    }

    private void write(ActiveOperation operation, PlaylistImportPreview preview, ListData data,
                       Playlist target, Callback<ImportResult> callback) {
        operation.markPersistenceStarted();
        Call<ResponseBody> write = api.updateUserList(username, password, token, data);
        if (!operation.replaceDuringCommit(write)) {
            operation.finishQueue();
            return;
        }
        write.enqueue(new retrofit2.Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> request, Response<ResponseBody> response) {
                if (!response.isSuccessful()) {
                    failAndFinish(operation, callback, Failure.Kind.WRITE_RESPONSE, null);
                    return;
                }
                if (operation.complete()) {
                    callbacks.execute(() -> callback.onSuccess(new ImportResult(
                            target.getId(), target.getName(), preview.isRefresh(),
                            preview.getImportableCount(), preview.getSkippedCount())));
                }
                operation.finishQueue();
            }

            @Override
            public void onFailure(Call<ResponseBody> request, Throwable error) {
                reconcileAfterUnknownWrite(operation, preview, target, callback, error);
            }
        });
    }

    private void reconcileAfterUnknownWrite(ActiveOperation operation,
                                            PlaylistImportPreview preview, Playlist target,
                                            Callback<ImportResult> callback, Throwable cause) {
        Call<ListData> read = api.getUserList(username, password, token);
        if (!operation.replaceDuringCommit(read)) {
            operation.finishQueue();
            return;
        }
        read.enqueue(new retrofit2.Callback<ListData>() {
            @Override
            public void onResponse(Call<ListData> request, Response<ListData> response) {
                Playlist saved = response.isSuccessful() && response.body() != null
                        ? PlaylistImportMutations.findBySourceIdentity(
                                response.body().getUserList(), preview.getReference()) : null;
                if (saved != null && operation.complete()) {
                    callbacks.execute(() -> callback.onSuccess(new ImportResult(
                            saved.getId(), saved.getName(), preview.isRefresh(),
                            preview.getImportableCount(), preview.getSkippedCount())));
                } else {
                    failAndFinish(operation, callback, Failure.Kind.TRANSPORT, cause);
                    return;
                }
                operation.finishQueue();
            }

            @Override
            public void onFailure(Call<ListData> request, Throwable error) {
                failAndFinish(operation, callback, Failure.Kind.TRANSPORT, cause);
            }
        });
    }

    private static String songIdentity(MusicInfo song) {
        if (song == null || !hasText(song.getSource()) || !hasText(song.getSongmid())
                || !hasText(song.getName())) return null;
        return song.getSource().trim() + ':' + song.getSongmid().trim();
    }

    private static String pageSignature(List<MusicInfo> songs) {
        StringBuilder signature = new StringBuilder();
        for (MusicInfo song : songs) {
            signature.append(song == null ? "null" : songIdentity(song)).append('|');
        }
        return signature.toString();
    }

    private static String safeName(String name) {
        return hasText(name) ? name.trim() : "导入歌单";
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private <T> void postFailure(ActiveOperation operation, Callback<T> callback,
                                 Failure.Kind kind, Throwable cause) {
        if (operation.complete()) callbacks.execute(
                () -> callback.onFailure(new Failure(kind, cause)));
    }

    private <T> void succeed(ActiveOperation operation, Callback<T> callback, T value) {
        if (operation.complete()) callbacks.execute(() -> callback.onSuccess(value));
    }

    private <T> void fail(ActiveOperation operation, Callback<T> callback,
                          Failure.Kind kind, Throwable cause) {
        if (operation.complete()) callbacks.execute(
                () -> callback.onFailure(new Failure(kind, cause)));
    }

    private <T> void failAndFinish(ActiveOperation operation, Callback<T> callback,
                                   Failure.Kind kind, Throwable cause) {
        fail(operation, callback, kind, cause);
        operation.finishQueue();
    }

    public interface Callback<T> {
        void onSuccess(T value);
        void onFailure(Failure failure);
    }

    public interface Operation {
        void cancel();
        boolean isCanceled();
    }

    public static final class ImportResult {
        private final String playlistId;
        private final String playlistName;
        private final boolean refreshed;
        private final int importedCount;
        private final int skippedCount;

        ImportResult(String playlistId, String playlistName, boolean refreshed,
                     int importedCount, int skippedCount) {
            this.playlistId = playlistId;
            this.playlistName = playlistName;
            this.refreshed = refreshed;
            this.importedCount = importedCount;
            this.skippedCount = skippedCount;
        }

        public String getPlaylistId() { return playlistId; }
        public String getPlaylistName() { return playlistName; }
        public boolean isRefreshed() { return refreshed; }
        public int getImportedCount() { return importedCount; }
        public int getSkippedCount() { return skippedCount; }
    }

    public static final class Failure {
        public enum Kind {
            INVALID_INPUT,
            DETAIL_RESPONSE,
            INCOMPLETE_PLAYLIST,
            NO_IMPORTABLE_SONGS,
            READ_RESPONSE,
            WRITE_RESPONSE,
            TRANSPORT
        }

        private final Kind kind;
        private final Throwable cause;

        Failure(Kind kind, Throwable cause) {
            this.kind = kind;
            this.cause = cause;
        }

        public Kind getKind() { return kind; }
        public Throwable getCause() { return cause; }
    }

    private static final class ActiveOperation implements Operation {
        private Call<?> current;
        private boolean canceled;
        private boolean complete;
        private boolean persistenceStarted;
        private Runnable queueCompletion;

        synchronized boolean replace(Call<?> call) {
            if (canceled || complete) {
                call.cancel();
                return false;
            }
            current = call;
            return true;
        }

        synchronized boolean replaceDuringCommit(Call<?> call) {
            if (complete) {
                call.cancel();
                return false;
            }
            current = call;
            return true;
        }

        synchronized void markPersistenceStarted() {
            persistenceStarted = true;
        }

        synchronized boolean complete() {
            if (complete || (canceled && !persistenceStarted)) return false;
            complete = true;
            current = null;
            return true;
        }

        synchronized void setQueueCompletion(Runnable completion) {
            queueCompletion = completion;
        }

        void finishQueue() {
            Runnable completion;
            synchronized (this) {
                completion = queueCompletion;
                queueCompletion = null;
            }
            if (completion != null) completion.run();
        }

        @Override
        public void cancel() {
            Runnable completion = null;
            synchronized (this) {
                if (canceled || complete) return;
                canceled = true;
                if (!persistenceStarted && current != null) current.cancel();
                if (!persistenceStarted) {
                    current = null;
                    completion = queueCompletion;
                    queueCompletion = null;
                }
            }
            if (completion != null) completion.run();
        }

        @Override
        public synchronized boolean isCanceled() {
            return canceled;
        }
    }
}
