package top.boluofan.musictv.player.preview;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import java.util.List;
import top.boluofan.musictv.player.lyrics.LyricParser;
import top.boluofan.musictv.player.visual.VinylTurntableView;

public final class PlayerPreviewFixture {
    public static final String SONG_TITLE = "Land, Sea and Sky";
    public static final String ARTIST = "Midnight Signal";
    public static final long DURATION_MS = 28_000L;
    public static final String ORIGINAL_LRC =
            "[00:00.00]Midnight wakes the silent wire\n"
                    + "[00:04.00]Signals bloom in amber fire\n"
                    + "[00:08.00]On the land, the sea, the sky\n"
                    + "[00:12.00]Every echo learns to fly\n"
                    + "[00:16.00]Turn the dark into a road\n"
                    + "[00:20.00]Let the rhythm bear the load\n"
                    + "[00:24.00]We are home before goodbye";
    public static final String TRANSLATED_LRC =
            "[00:00.12]午夜唤醒沉默的线路\n"
                    + "[00:04.10]信号在琥珀色火光中绽放\n"
                    + "[00:08.08]在陆地、海洋与天空之上\n"
                    + "[00:12.14]每一声回响都学会飞翔\n"
                    + "[00:16.11]把黑暗变成前行的路\n"
                    + "[00:20.09]让节奏承载所有重量\n"
                    + "[00:24.13]道别之前我们已经到家";

    private PlayerPreviewFixture() {
    }

    public static List<LyricParser.Line> parseLyrics() {
        return LyricParser.parse(ORIGINAL_LRC, TRANSLATED_LRC);
    }

    public static Bitmap createArtwork(int requestedSize) {
        int size = Math.max(1, requestedSize);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        paint.setShader(new LinearGradient(
                0f,
                0f,
                size,
                size,
                new int[]{0xFF151019, 0xFF8C4E35, 0xFFE3A36B},
                new float[]{0f, 0.58f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0f, 0f, size, size, paint);

        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(size * 0.018f);
        paint.setColor(0xB8FFF1DC);
        canvas.drawCircle(size * 0.34f, size * 0.42f, size * 0.27f, paint);
        canvas.drawCircle(size * 0.66f, size * 0.58f, size * 0.31f, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xCC241722);
        canvas.drawCircle(size * 0.50f, size * 0.50f, size * 0.13f, paint);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(size * 0.50f, size * 0.50f, size * 0.025f, paint);
        return bitmap;
    }

    public static Bitmap applyArtwork(VinylTurntableView turntableView, int size) {
        Bitmap artwork = createArtwork(size);
        turntableView.setArtwork(artwork);
        return artwork;
    }
}
