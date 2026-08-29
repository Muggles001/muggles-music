package top.boluofan.musictv;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.ResolvingDataSource;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.session.DefaultMediaNotificationProvider;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionCommands;
import androidx.media3.session.SessionResult;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import top.boluofan.musictv.player.audio.PlaybackSpectrumAnalyzer;
import top.boluofan.musictv.player.audio.PlaybackSpectrumBus;
import top.boluofan.musictv.player.settings.FailedTrackRecovery;
import top.boluofan.musictv.player.settings.SleepTimerContract;
import top.boluofan.musictv.player.settings.SleepTimerController;
import top.boluofan.musictv.settings.AppSettingsPreferences;

public class MusicService extends MediaSessionService {
    private static final String TAG = "MusicService";
    private MediaSession mediaSession;
    private ExoPlayer player;
    private PlaybackSpectrumAnalyzer spectrumAnalyzer;
    private AppSettingsPreferences settingsPreferences;
    private final FailedTrackRecovery failedTrackRecovery = new FailedTrackRecovery();
    private SleepTimerController sleepTimerController;

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void onCreate() {
        super.onCreate();
        settingsPreferences = new AppSettingsPreferences(this);
        sleepTimerController = new SleepTimerController(
                new Handler(Looper.getMainLooper()), new SleepTimerController.Listener() {
                    @Override
                    public void pausePlayback() {
                        if (player != null) player.pause();
                    }

                    @Override
                    public void publish(SleepTimerContract.State state) {
                        publishSleepTimerState(state);
                    }
                });

        DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        PlaybackUrlResolver playbackUrlResolver = new PlaybackUrlResolver(this);
        ResolvingDataSource.Factory resolvingFactory = new ResolvingDataSource.Factory(
                httpDataSourceFactory, playbackUrlResolver::resolve);

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();

        spectrumAnalyzer = new PlaybackSpectrumAnalyzer();
        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(this) {
            @Override
            protected AudioSink buildAudioSink(Context context, boolean enableFloatOutput,
                    boolean enableAudioTrackPlaybackParams) {
                try {
                    return new DefaultAudioSink.Builder(context)
                            .setEnableFloatOutput(enableFloatOutput)
                            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                            .setAudioProcessors(new AudioProcessor[]{spectrumAnalyzer})
                            .build();
                } catch (RuntimeException e) {
                    Log.w(TAG, "Spectrum analysis is unavailable for this audio output", e);
                    return super.buildAudioSink(context, enableFloatOutput,
                            enableAudioTrackPlaybackParams);
                }
            }
        };

        player = new ExoPlayer.Builder(this, renderersFactory)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(this).setDataSourceFactory(resolvingFactory))
                .setAudioAttributes(audioAttributes, true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build();
        player.addListener(createServicePlayerListener());

        Intent intent = new Intent(this, top.boluofan.musictv.ui.MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        mediaSession = new MediaSession.Builder(this, player)
                .setSessionActivity(pendingIntent)
                .setId("io.github.muggles001.mugglesmusic.session")
                .setCallback(createSessionCallback())
                .build();

        DefaultMediaNotificationProvider notificationProvider = new DefaultMediaNotificationProvider.Builder(this).build();
        setMediaNotificationProvider(notificationProvider);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (player != null && !settingsPreferences.isBackgroundPlaybackEnabled()) {
            player.pause();
            stopSelf();
            return;
        }
        if (player != null && !player.getPlayWhenReady()) {
            stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        if (sleepTimerController != null) {
            sleepTimerController.close();
        }
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
        spectrumAnalyzer = null;
        PlaybackSpectrumBus.getShared().clear();
        super.onDestroy();
    }

    private Player.Listener createServicePlayerListener() {
        return new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                if (!settingsPreferences.isSkipFailedTracksEnabled()) return;
                int nextIndex = failedTrackRecovery.recordFailureAndFindNext(
                        player.getCurrentMediaItemIndex(), player.getMediaItemCount());
                if (nextIndex == FailedTrackRecovery.INDEX_UNSET) return;
                Log.w(TAG, "Skipping failed queue item at index "
                        + player.getCurrentMediaItemIndex() + " after " + error.getErrorCodeName());
                player.seekToDefaultPosition(nextIndex);
                player.prepare();
                player.play();
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    failedTrackRecovery.reset();
                } else if (playbackState == Player.STATE_ENDED) {
                    sleepTimerController.onNaturalCompletion();
                }
            }

            @Override
            public void onTimelineChanged(Timeline timeline, int reason) {
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                    failedTrackRecovery.reset();
                }
            }

            @Override
            public void onMediaItemTransition(MediaItem mediaItem, int reason) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
                        || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                    sleepTimerController.onNaturalCompletion();
                }
            }
        };
    }

    private MediaSession.Callback createSessionCallback() {
        SessionCommand setTimer = new SessionCommand(SleepTimerContract.COMMAND_SET, Bundle.EMPTY);
        SessionCommand getTimer = new SessionCommand(SleepTimerContract.COMMAND_GET, Bundle.EMPTY);
        return new MediaSession.Callback() {
            @Override
            public MediaSession.ConnectionResult onConnect(
                    MediaSession session, MediaSession.ControllerInfo controller) {
                MediaSession.ConnectionResult defaults = MediaSession.Callback.super.onConnect(
                        session, controller);
                SessionCommands commands = defaults.availableSessionCommands.buildUpon()
                        .add(setTimer)
                        .add(getTimer)
                        .build();
                return MediaSession.ConnectionResult.accept(
                        commands, defaults.availablePlayerCommands);
            }

            @Override
            public ListenableFuture<SessionResult> onCustomCommand(
                    MediaSession session,
                    MediaSession.ControllerInfo controller,
                    SessionCommand command,
                    Bundle args) {
                if (SleepTimerContract.COMMAND_GET.equals(command.customAction)) {
                    return Futures.immediateFuture(new SessionResult(
                            SessionResult.RESULT_SUCCESS,
                            SleepTimerContract.encode(sleepTimerController.snapshot())));
                }
                if (SleepTimerContract.COMMAND_SET.equals(command.customAction)) {
                    SleepTimerContract.State request = SleepTimerContract.decode(args);
                    int result = sleepTimerController.set(request.mode, request.value)
                            ? SessionResult.RESULT_SUCCESS : SessionResult.RESULT_ERROR_BAD_VALUE;
                    return Futures.immediateFuture(new SessionResult(
                            result, SleepTimerContract.encode(sleepTimerController.snapshot())));
                }
                return MediaSession.Callback.super.onCustomCommand(
                        session, controller, command, args);
            }
        };
    }

    private void publishSleepTimerState(SleepTimerContract.State state) {
        if (mediaSession == null) return;
        Bundle stateBundle = SleepTimerContract.encode(state);
        mediaSession.setSessionExtras(stateBundle);
        mediaSession.broadcastCustomCommand(
                new SessionCommand(SleepTimerContract.COMMAND_STATE, Bundle.EMPTY), stateBundle);
    }

    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }
}
