package top.boluofan.musictv.player.lyrics;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class LyricRequestCoordinator {
    private static final long EXTERNAL_TIMEOUT_MS = 15_000L;

    public interface Cancelable {
        void cancel();
    }

    public interface ServerGateway {
        Cancelable request(Request request, ServerCallback callback);
    }

    public interface ServerCallback {
        void onSuccess(String primary, String lxlyric, String translated);
        void onFailure(String message);
    }

    public interface ExternalScraper {
        void scrape(Request request, ExternalCallback callback);
    }

    public interface ExternalCallback {
        void onSuccess(String artist, String picUrl, String lyrics);
        void onFailure(String message);
    }

    public interface Scheduler {
        Cancelable schedule(long delayMs, Runnable runnable);
    }

    public interface LyricSelector {
        String select(String preferred, String alternative);
    }

    public interface Listener {
        void onLoading(Request request);
        void onServerLyrics(Request request, String primary, String translated);
        void onExternalResult(Request request, String artist, String picUrl, String lyrics);
        void onTimeout(Request request);
        void onError(Request request, String message);
    }

    public static final class Request {
        private final String source;
        private final String songmid;
        private final String mediaId;
        private final String songName;
        private final String artist;
        private final Map<String, String> params;
        private final boolean forceExternal;

        public Request(String source, String songmid, String mediaId, String songName,
                       String artist, Map<String, String> params, boolean forceExternal) {
            this.source = source;
            this.songmid = songmid;
            this.mediaId = mediaId;
            this.songName = songName;
            this.artist = artist;
            this.params = Collections.unmodifiableMap(params == null
                    ? new LinkedHashMap<>() : new LinkedHashMap<>(params));
            this.forceExternal = forceExternal;
        }

        public String getSource() { return source; }
        public String getSongmid() { return songmid; }
        public String getMediaId() { return mediaId; }
        public String getSongName() { return songName; }
        public String getArtist() { return artist; }
        public Map<String, String> getParams() { return params; }
        public boolean isForceExternal() { return forceExternal; }
    }

    private enum Phase { IDLE, SERVER, EXTERNAL, FINISHED }

    private final ServerGateway server;
    private final ExternalScraper scraper;
    private final Scheduler scheduler;
    private final LyricSelector selector;
    private final Listener listener;
    private final long timeoutMs;
    private long generation;
    private boolean active;
    private Phase phase = Phase.IDLE;
    private Cancelable serverCall;
    private Cancelable timeoutTask;

    public LyricRequestCoordinator(ServerGateway server, ExternalScraper scraper,
                                   Scheduler scheduler, LyricSelector selector,
                                   Listener listener) {
        this(server, scraper, scheduler, selector, listener, EXTERNAL_TIMEOUT_MS);
    }

    LyricRequestCoordinator(ServerGateway server, ExternalScraper scraper,
                            Scheduler scheduler, LyricSelector selector,
                            Listener listener, long timeoutMs) {
        this.server = Objects.requireNonNull(server);
        this.scraper = Objects.requireNonNull(scraper);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.selector = Objects.requireNonNull(selector);
        this.listener = Objects.requireNonNull(listener);
        this.timeoutMs = timeoutMs;
    }

    public void load(Request request) {
        Objects.requireNonNull(request);
        generation++;
        cancelHandles();
        active = true;
        phase = Phase.IDLE;
        long requestGeneration = generation;
        listener.onLoading(request);
        if (!isCurrent(requestGeneration)) return;
        if (request.isForceExternal() || !hasText(request.getSongmid())) {
            startExternal(request, requestGeneration);
        } else {
            startServer(request, requestGeneration);
        }
    }

    public void cancel() {
        if (!active && serverCall == null && timeoutTask == null) return;
        generation++;
        active = false;
        phase = Phase.IDLE;
        cancelHandles();
    }

    private void startServer(Request request, long requestGeneration) {
        phase = Phase.SERVER;
        Cancelable candidate = server.request(request, new ServerCallback() {
            @Override
            public void onSuccess(String primary, String lxlyric, String translated) {
                if (!isCurrent(requestGeneration, Phase.SERVER)) return;
                serverCall = null;
                String selected = selector.select(primary, lxlyric);
                if (!hasText(selected)) {
                    startExternal(request, requestGeneration);
                    return;
                }
                finish();
                listener.onServerLyrics(request, selected,
                        selected.equals(translated) ? "" : text(translated));
            }

            @Override
            public void onFailure(String message) {
                if (!isCurrent(requestGeneration, Phase.SERVER)) return;
                serverCall = null;
                startExternal(request, requestGeneration);
            }
        });
        if (isCurrent(requestGeneration, Phase.SERVER)) {
            serverCall = candidate;
        } else {
            cancel(candidate);
        }
    }

    private void startExternal(Request request, long requestGeneration) {
        if (!isCurrent(requestGeneration)) return;
        phase = Phase.EXTERNAL;
        Cancelable candidate = scheduler.schedule(timeoutMs, () -> {
            if (!isCurrent(requestGeneration, Phase.EXTERNAL)) return;
            timeoutTask = null;
            finish();
            listener.onTimeout(request);
        });
        if (isCurrent(requestGeneration, Phase.EXTERNAL)) {
            timeoutTask = candidate;
        } else {
            cancel(candidate);
            return;
        }

        scraper.scrape(request, new ExternalCallback() {
            @Override
            public void onSuccess(String artist, String picUrl, String lyrics) {
                if (!isCurrent(requestGeneration, Phase.EXTERNAL)) return;
                Cancelable timeout = timeoutTask;
                timeoutTask = null;
                finish();
                cancel(timeout);
                listener.onExternalResult(request, text(artist), text(picUrl), text(lyrics));
            }

            @Override
            public void onFailure(String message) {
                if (!isCurrent(requestGeneration, Phase.EXTERNAL)) return;
                Cancelable timeout = timeoutTask;
                timeoutTask = null;
                finish();
                cancel(timeout);
                listener.onError(request, text(message));
            }
        });
    }

    private boolean isCurrent(long requestGeneration) {
        return active && generation == requestGeneration;
    }

    private boolean isCurrent(long requestGeneration, Phase expected) {
        return isCurrent(requestGeneration) && phase == expected;
    }

    private void finish() {
        active = false;
        phase = Phase.FINISHED;
        serverCall = null;
        timeoutTask = null;
    }

    private void cancelHandles() {
        Cancelable call = serverCall;
        Cancelable timeout = timeoutTask;
        serverCall = null;
        timeoutTask = null;
        cancel(call);
        cancel(timeout);
    }

    private static void cancel(Cancelable cancelable) {
        if (cancelable != null) cancelable.cancel();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
