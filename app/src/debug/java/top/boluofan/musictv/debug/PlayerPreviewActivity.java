package top.boluofan.musictv.debug;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.request.RequestOptions;
import java.util.ArrayList;
import java.util.List;
import jp.wasabeef.glide.transformations.BlurTransformation;
import top.boluofan.musictv.LyricParser;
import top.boluofan.musictv.R;
import top.boluofan.musictv.player.control.PlayerControlsStateMachine;
import top.boluofan.musictv.player.lyrics.LyricTimeline;
import top.boluofan.musictv.player.visual.AsciiRibbonView;
import top.boluofan.musictv.player.visual.SpatialLyricsView;
import top.boluofan.musictv.player.visual.VinylTurntableView;
import top.boluofan.musictv.player.visual.WaveformProgressView;

public final class PlayerPreviewActivity extends AppCompatActivity {
    private static final long MOCK_POSITION_MS = 794_000L;
    private static final long MOCK_DURATION_MS = 846_000L;
    private static final int FIRST_LYRIC_ADAPTER_POSITION = 1;
    private static final int LAST_LYRIC_ADAPTER_POSITION = 9;
    private static final float[] MOCK_SPECTRUM = {
            0.24f, 0.46f, 0.82f, 0.62f, 0.34f, 0.72f,
            0.94f, 0.56f, 0.42f, 0.78f, 0.38f, 0.20f
    };
    private static final String ORIGINAL_LRC =
            "[12:56.00]Signals wake before the city unfolds\n"
                    + "[13:00.00]Through the static, city lights unfold\n"
                    + "[13:04.00]Silver signals circle through the cold\n"
                    + "[13:08.00]Every shadow learns a different name\n"
                    + "[13:12.00]We are afterimages in the frame\n"
                    + "[13:20.00]Turn the silence into something bright\n"
                    + "[13:26.00]Let the vinyl carry us tonight\n"
                    + "[13:32.00]Stay until the last horizon glows\n"
                    + "[13:36.00]Leave a little light behind";
    private static final String TRANSLATED_LRC =
            "[12:56.00]城市尚未醒来，信号先亮起\n"
                    + "[13:00.00]穿过静电噪声，城市灯火展开\n"
                    + "[13:04.00]银色信号在寒夜里盘旋\n"
                    + "[13:08.00]每一道影子都记住不同的名字\n"
                    + "[13:12.00]我们是画面中尚未散去的残像\n"
                    + "[13:20.00]把寂静变成一束明亮的光\n"
                    + "[13:26.00]今夜让黑胶带我们继续前行\n"
                    + "[13:32.00]留到最后一道地平线发亮\n"
                    + "[13:36.00]把一点光留在身后";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable spectrumUpdater = new Runnable() {
        @Override
        public void run() {
            if (asciiRibbonView == null || waveformProgressView == null) {
                return;
            }
            asciiRibbonView.setSpectrum(MOCK_SPECTRUM, 0.68f);
            waveformProgressView.setSpectrum(MOCK_SPECTRUM);
            handler.postDelayed(this, 500L);
        }
    };

    private View mainContent;
    private View drawer;
    private View optionMenu;
    private ImageButton playPauseButton;
    private LyricTimeline lyricTimeline;
    private SpatialLyricsView spatialLyricsView;
    private AsciiRibbonView asciiRibbonView;
    private VinylTurntableView vinylTurntableView;
    private WaveformProgressView waveformProgressView;
    private PlayerControlsStateMachine controlsStateMachine;
    private Bitmap artwork;
    private boolean playing = true;
    private int activeLyricPosition = 4;
    private float lastPointerY = Float.NaN;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setImmersiveMode();
        setContentView(R.layout.activity_player);

        mainContent = findViewById(R.id.layoutMainContent);
        drawer = findViewById(R.id.layoutDrawer);
        optionMenu = findViewById(R.id.layoutOptionMenu);
        asciiRibbonView = findViewById(R.id.asciiRibbonView);
        vinylTurntableView = findViewById(R.id.vinylTurntableView);
        waveformProgressView = findViewById(R.id.waveformProgressView);
        spatialLyricsView = findViewById(R.id.spatialLyricsView);
        playPauseButton = findViewById(R.id.btnPlayPause);

        drawer.setVisibility(View.GONE);
        optionMenu.setVisibility(View.GONE);
        configureArtwork();
        configureLyrics();
        configurePlaybackScene();
        configureControls();

        mainContent.post(mainContent::requestFocus);
        handler.post(spectrumUpdater);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setImmersiveMode();
        if (vinylTurntableView != null) {
            vinylTurntableView.onHostResume();
        }
        if (spatialLyricsView != null) spatialLyricsView.onHostResume();
    }

    @Override
    protected void onPause() {
        if (vinylTurntableView != null) {
            vinylTurntableView.onHostPause();
        }
        if (spatialLyricsView != null) spatialLyricsView.onHostPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(spectrumUpdater);
        if (controlsStateMachine != null) {
            controlsStateMachine.dispose();
        }
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            setImmersiveMode();
        }
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        if (controlsStateMachine != null && controlsStateMachine.isDisplayed()) {
            controlsStateMachine.onInteraction();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN
                && PlayerControlsStateMachine.isRevealKey(event.getKeyCode())) {
            boolean wasDisplayed = controlsStateMachine.isDisplayed();
            controlsStateMachine.onRevealKey(event.getKeyCode());
            if (!wasDisplayed || event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        handlePointerMotion(event);
        return super.dispatchGenericMotionEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        handlePointerMotion(event);
        return super.dispatchTouchEvent(event);
    }

    private void configureArtwork() {
        artwork = createArtwork(512);
        vinylTurntableView.setArtwork(artwork);
        ImageView background = findViewById(R.id.ivBlurBackground);
        Glide.with(this)
                .load(artwork)
                .apply(new RequestOptions().transform(
                        new CenterCrop(),
                        new BlurTransformation(20, 3)
                ))
                .into(background);
    }

    private void configureLyrics() {
        lyricTimeline = new LyricTimeline();
        lyricTimeline.setLines(createMockLyrics());
        lyricTimeline.setCurrentIndex(activeLyricPosition);
        spatialLyricsView.setLyrics(lyricTimeline.getLines());
        spatialLyricsView.setFocusFraction(0.50f);
        spatialLyricsView.setCurrentIndex(activeLyricPosition, false);
        findViewById(R.id.tvNoLyrics).setVisibility(View.GONE);
    }

    private void configurePlaybackScene() {
        TextView title = findViewById(R.id.tvBigTitle);
        TextView artist = findViewById(R.id.tvBigArtist);
        title.setText("Afterimage Protocol");
        artist.setText("MØNOLITH  /  林海信号");
        title.setSelected(true);
        artist.setSelected(true);

        asciiRibbonView.setAmbientColor(0xFFB17C67);
        asciiRibbonView.setPlaying(true);
        vinylTurntableView.setPlaying(true);
        waveformProgressView.setProgress(MOCK_POSITION_MS, MOCK_DURATION_MS);
    }

    private void configureControls() {
        View controls = findViewById(R.id.layoutBottomControls);
        controlsStateMachine = new PlayerControlsStateMachine(
                controls,
                handler,
                state -> {
                    if (state == PlayerControlsStateMachine.State.VISIBLE) {
                        playPauseButton.requestFocus();
                    } else if (state == PlayerControlsStateMachine.State.HIDDEN) {
                        mainContent.requestFocus();
                    }
                }
        );
        waveformProgressView.setOnSeekListener(positionMs -> {
            waveformProgressView.setProgress(positionMs, MOCK_DURATION_MS);
            controlsStateMachine.onInteraction();
        });

        playPauseButton.setImageResource(R.drawable.ic_pause);
        playPauseButton.setOnClickListener(view -> togglePlaying());
        findViewById(R.id.btnPrev).setOnClickListener(view -> moveActiveLyric(-1));
        findViewById(R.id.btnNext).setOnClickListener(view -> moveActiveLyric(1));
        findViewById(R.id.btnRepeat).setOnClickListener(view -> controlsStateMachine.onInteraction());
        findViewById(R.id.btnQueue).setOnClickListener(view -> controlsStateMachine.onInteraction());
        findViewById(R.id.btnScrape).setOnClickListener(view -> controlsStateMachine.onInteraction());
    }

    private void togglePlaying() {
        playing = !playing;
        asciiRibbonView.setPlaying(playing);
        vinylTurntableView.setPlaying(playing);
        playPauseButton.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        controlsStateMachine.onInteraction();
    }

    private void moveActiveLyric(int direction) {
        activeLyricPosition += direction;
        if (activeLyricPosition < FIRST_LYRIC_ADAPTER_POSITION) {
            activeLyricPosition = LAST_LYRIC_ADAPTER_POSITION;
        } else if (activeLyricPosition > LAST_LYRIC_ADAPTER_POSITION) {
            activeLyricPosition = FIRST_LYRIC_ADAPTER_POSITION;
        }
        lyricTimeline.setCurrentIndex(activeLyricPosition);
        spatialLyricsView.setCurrentIndex(activeLyricPosition, true);
        controlsStateMachine.onInteraction();
    }

    private void handlePointerMotion(MotionEvent event) {
        if (!event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            return;
        }
        int action = event.getActionMasked();
        if (action != MotionEvent.ACTION_MOVE && action != MotionEvent.ACTION_HOVER_MOVE) {
            if (action == MotionEvent.ACTION_HOVER_EXIT || action == MotionEvent.ACTION_CANCEL) {
                lastPointerY = Float.NaN;
            }
            return;
        }
        float y = event.getY();
        if (!Float.isNaN(lastPointerY)) {
            float threshold = 4f * getResources().getDisplayMetrics().density;
            if (y - lastPointerY > threshold) {
                controlsStateMachine.onPointerMoved(y - lastPointerY);
            }
        }
        lastPointerY = y;
    }

    private List<LyricTimeline.Line> createMockLyrics() {
        List<LyricParser.Line> parsed = LyricParser.parse(ORIGINAL_LRC, TRANSLATED_LRC);
        List<LyricTimeline.Line> result = new ArrayList<>(parsed.size());
        for (LyricParser.Line line : parsed) {
            result.add(new LyricTimeline.Line(line.timeMs, line.text, line.rawLine));
        }
        return result;
    }

    private Bitmap createArtwork(int size) {
        Bitmap result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new LinearGradient(
                0f,
                0f,
                size,
                size,
                new int[]{0xFF17171B, 0xFF8B5044, 0xFFD6A77F},
                null,
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0f, 0f, size, size, paint);

        paint.setShader(new RadialGradient(
                size * 0.30f,
                size * 0.26f,
                size * 0.62f,
                new int[]{0xC0F5D6B5, 0x288C433B, 0x00101014},
                null,
                Shader.TileMode.CLAMP
        ));
        canvas.drawCircle(size * 0.30f, size * 0.26f, size * 0.62f, paint);

        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(size * 0.018f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xD9FFF1E4);
        Path path = new Path();
        path.moveTo(-size * 0.05f, size * 0.68f);
        path.cubicTo(
                size * 0.18f,
                size * 0.36f,
                size * 0.50f,
                size * 0.96f,
                size * 1.05f,
                size * 0.50f
        );
        canvas.drawPath(path, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        paint.setTextSize(size * 0.073f);
        canvas.drawText("AFTERIMAGE", size * 0.09f, size * 0.15f, paint);
        paint.setColor(0xB8FFFFFF);
        paint.setTextSize(size * 0.031f);
        canvas.drawText("NIGHT SIGNAL / 02", size * 0.095f, size * 0.205f, paint);
        return result;
    }

    private void setImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }
}
