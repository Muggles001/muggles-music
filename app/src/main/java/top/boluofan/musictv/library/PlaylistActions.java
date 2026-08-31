package top.boluofan.musictv.library;

import java.util.ArrayList;
import java.util.List;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;
import top.boluofan.musictv.catalog.LxApiService;
import top.boluofan.musictv.catalog.model.ListData;
import top.boluofan.musictv.catalog.model.MusicInfo;
import top.boluofan.musictv.catalog.model.Playlist;

public final class PlaylistActions {
    private final LxApiService apiService;
    private final String username;
    private final String password;
    private final String token;

    public PlaylistActions(LxApiService apiService, String username, String password, String token) {
        this.apiService = apiService;
        this.username = username;
        this.password = password;
        this.token = token;
    }

    public Operation loadChoices(Callback<List<String>> callback) {
        return loadData(new Callback<ListData>() {
            @Override
            public void onSuccess(ListData data) {
                List<Playlist> playlists = data.getUserList();
                if (playlists == null || playlists.isEmpty()) {
                    callback.onFailure(new Failure(Failure.Kind.NO_PLAYLISTS, null));
                    return;
                }
                List<String> names = new ArrayList<>(playlists.size());
                for (Playlist playlist : playlists) names.add(playlist.getName());
                callback.onSuccess(names);
            }

            @Override
            public void onFailure(Failure failure) {
                callback.onFailure(failure);
            }
        });
    }

    public Operation loadData(Callback<ListData> callback) {
        ActiveOperation operation = new ActiveOperation();
        Call<ListData> call = apiService.getUserList(username, password, token);
        operation.replace(call);
        call.enqueue(new retrofit2.Callback<ListData>() {
            @Override
            public void onResponse(Call<ListData> call, Response<ListData> response) {
                if (operation.isCanceled()) return;
                if (!response.isSuccessful() || response.body() == null) {
                    fail(operation, callback, Failure.Kind.READ_RESPONSE, null);
                    return;
                }
                succeed(operation, callback, response.body());
            }

            @Override
            public void onFailure(Call<ListData> call, Throwable error) {
                fail(operation, callback, Failure.Kind.TRANSPORT, error);
            }
        });
        return operation;
    }

    public Operation loadDetail(String source, String id, Callback<Playlist> callback) {
        ActiveOperation operation = new ActiveOperation();
        Call<Playlist> call = apiService.getPlaylistDetail(source, id, 1);
        operation.replace(call);
        call.enqueue(new retrofit2.Callback<Playlist>() {
            @Override
            public void onResponse(Call<Playlist> request, Response<Playlist> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    fail(operation, callback, Failure.Kind.DETAIL_RESPONSE, null);
                    return;
                }
                succeed(operation, callback, response.body());
            }

            @Override
            public void onFailure(Call<Playlist> request, Throwable error) {
                fail(operation, callback, Failure.Kind.TRANSPORT, error);
            }
        });
        return operation;
    }

    public Operation addSong(String targetName, MusicInfo song, Callback<AddResult> callback) {
        return addSong(targetName, song, callback, PlaylistMutations::prependSongIfAbsent);
    }

    public Operation addSongSkippingNullEntries(String targetName, MusicInfo song,
                                                Callback<AddResult> callback) {
        return addSong(targetName, song, callback,
                PlaylistMutations::prependSongIfAbsentSkippingNulls);
    }

    private Operation addSong(String targetName, MusicInfo song, Callback<AddResult> callback,
                              SongMutation mutation) {
        ActiveOperation operation = new ActiveOperation();
        Call<ListData> read = apiService.getUserList(username, password, token);
        operation.replace(read);
        read.enqueue(new retrofit2.Callback<ListData>() {
            @Override
            public void onResponse(Call<ListData> call, Response<ListData> response) {
                if (operation.isCanceled()) return;
                if (!response.isSuccessful() || response.body() == null) {
                    fail(operation, callback, Failure.Kind.READ_RESPONSE, null);
                    return;
                }
                ListData listData = response.body();
                List<Playlist> playlists = listData.getUserList();
                if (playlists == null || playlists.isEmpty()) {
                    fail(operation, callback, Failure.Kind.NO_PLAYLISTS, null);
                    return;
                }
                Playlist target = PlaylistMutations.findByName(playlists, targetName);
                if (target == null) {
                    fail(operation, callback, Failure.Kind.TARGET_NOT_FOUND, null);
                    return;
                }
                if (!mutation.prepend(target, song)) {
                    fail(operation, callback, Failure.Kind.DUPLICATE_SONG, null);
                    return;
                }
                save(operation, listData, target, callback);
            }

            @Override
            public void onFailure(Call<ListData> call, Throwable error) {
                fail(operation, callback, Failure.Kind.TRANSPORT, error);
            }
        });
        return operation;
    }

    public Operation collectPlaylist(String name, String source, String sourceListId,
                                     List<MusicInfo> songs, CollectCallback callback) {
        ActiveOperation operation = new ActiveOperation();
        List<MusicInfo> songsSnapshot = songs == null
                ? new ArrayList<>() : new ArrayList<>(songs);
        Call<ListData> read = apiService.getUserList(username, password, token);
        operation.replace(read);
        read.enqueue(new retrofit2.Callback<ListData>() {
            @Override
            public void onResponse(Call<ListData> call, Response<ListData> response) {
                if (operation.isCanceled()) return;
                if (!response.isSuccessful() || response.body() == null) {
                    failCollect(operation, callback, Failure.Kind.READ_RESPONSE, null);
                    return;
                }
                ListData listData = response.body();
                Playlist existing = PlaylistMutations.findByName(listData.getUserList(), name);
                if (existing == null) {
                    Playlist created = PlaylistMutations.createCollectedPlaylist(
                            listData, "playlist_" + System.currentTimeMillis(), name,
                            source, sourceListId, songsSnapshot);
                    saveCollection(operation, listData, callback, callback::onCreated);
                    return;
                }
                if (!operation.awaitingConfirmation()) return;
                callback.onOverwriteRequired(() -> {
                    if (!operation.beginConfirmation()) return;
                    PlaylistMutations.overwriteCollectedPlaylist(
                            existing, source, sourceListId, songsSnapshot);
                    saveCollection(operation, listData, callback, callback::onOverwritten);
                });
            }

            @Override
            public void onFailure(Call<ListData> call, Throwable error) {
                failCollect(operation, callback, Failure.Kind.TRANSPORT, error);
            }
        });
        return operation;
    }

    private void save(ActiveOperation operation, ListData listData, Playlist target,
                      Callback<AddResult> callback) {
        Call<ResponseBody> write = apiService.updateUserList(
                username, password, token, listData);
        if (!operation.replace(write)) return;
        write.enqueue(new retrofit2.Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (!response.isSuccessful()) {
                    fail(operation, callback, Failure.Kind.WRITE_RESPONSE, null);
                    return;
                }
                succeed(operation, callback,
                        new AddResult(target.getName(), target.getSongCount()));
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable error) {
                fail(operation, callback, Failure.Kind.TRANSPORT, error);
            }
        });
    }

    private void saveCollection(ActiveOperation operation, ListData listData,
                                CollectCallback callback, Runnable success) {
        Call<ResponseBody> write = apiService.updateUserList(
                username, password, token, listData);
        if (!operation.replace(write)) return;
        write.enqueue(new retrofit2.Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (!response.isSuccessful()) {
                    failCollect(operation, callback, Failure.Kind.WRITE_RESPONSE, null);
                    return;
                }
                if (!operation.complete()) return;
                success.run();
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable error) {
                failCollect(operation, callback, Failure.Kind.TRANSPORT, error);
            }
        });
    }

    private static <T> void succeed(ActiveOperation operation, Callback<T> callback, T value) {
        if (operation.complete()) callback.onSuccess(value);
    }

    private static <T> void fail(ActiveOperation operation, Callback<T> callback,
                                 Failure.Kind kind, Throwable cause) {
        if (operation.complete()) callback.onFailure(new Failure(kind, cause));
    }

    private static void failCollect(ActiveOperation operation, CollectCallback callback,
                                    Failure.Kind kind, Throwable cause) {
        if (operation.complete()) callback.onFailure(new Failure(kind, cause));
    }

    public interface Callback<T> {
        void onSuccess(T value);
        void onFailure(Failure failure);
    }

    private interface SongMutation {
        boolean prepend(Playlist playlist, MusicInfo song);
    }

    public interface CollectCallback {
        void onCreated();
        void onOverwriteRequired(OverwriteConfirmation confirmation);
        void onOverwritten();
        void onFailure(Failure failure);
    }

    public interface OverwriteConfirmation {
        void confirm();
    }

    public interface Operation {
        void cancel();
        boolean isCanceled();
    }

    public static final class AddResult {
        private final String playlistName;
        private final int songCount;

        AddResult(String playlistName, int songCount) {
            this.playlistName = playlistName;
            this.songCount = songCount;
        }

        public String getPlaylistName() {
            return playlistName;
        }

        public int getSongCount() {
            return songCount;
        }
    }

    public static final class Failure {
        public enum Kind {
            READ_RESPONSE,
            DETAIL_RESPONSE,
            NO_PLAYLISTS,
            TARGET_NOT_FOUND,
            DUPLICATE_SONG,
            WRITE_RESPONSE,
            TRANSPORT
        }

        private final Kind kind;
        private final Throwable cause;

        Failure(Kind kind, Throwable cause) {
            this.kind = kind;
            this.cause = cause;
        }

        public Kind getKind() {
            return kind;
        }

        public Throwable getCause() {
            return cause;
        }
    }

    private static final class ActiveOperation implements Operation {
        private Call<?> current;
        private boolean canceled;
        private boolean complete;
        private boolean confirmationPending;

        synchronized boolean replace(Call<?> call) {
            if (canceled || complete) {
                call.cancel();
                return false;
            }
            current = call;
            return true;
        }

        synchronized boolean complete() {
            if (canceled || complete) return false;
            complete = true;
            current = null;
            return true;
        }

        synchronized boolean awaitingConfirmation() {
            if (canceled || complete) return false;
            current = null;
            confirmationPending = true;
            return true;
        }

        synchronized boolean beginConfirmation() {
            if (canceled || complete || !confirmationPending) return false;
            confirmationPending = false;
            return true;
        }

        @Override
        public synchronized void cancel() {
            if (canceled || complete) return;
            canceled = true;
            if (current != null) current.cancel();
            current = null;
        }

        @Override
        public synchronized boolean isCanceled() {
            return canceled;
        }
    }
}
