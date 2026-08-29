package top.boluofan.musictv.player.audio;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import java.nio.ByteBuffer;
import org.junit.Test;

public class PlaybackSpectrumAnalyzerTest {
    @Test
    public void partiallyConsumedOutputCanBeQueuedAgainWithoutSelfCopyFailure() throws Exception {
        PlaybackSpectrumAnalyzer analyzer = new PlaybackSpectrumAnalyzer();
        analyzer.configure(new AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT));
        analyzer.flush();

        byte[] samples = {1, 2, 3, 4, 5, 6, 7, 8};
        ByteBuffer input = ByteBuffer.allocateDirect(samples.length);
        input.put(samples).flip();
        analyzer.queueInput(input);
        assertFalse(input.hasRemaining());

        ByteBuffer recycledOutput = analyzer.getOutput();
        ByteBuffer initialCopy = recycledOutput.duplicate();
        byte[] initialActual = new byte[initialCopy.remaining()];
        initialCopy.get(initialActual);
        assertArrayEquals(samples, initialActual);

        recycledOutput.position(4);
        analyzer.queueInput(recycledOutput);
        ByteBuffer secondOutput = analyzer.getOutput();
        byte[] actual = new byte[secondOutput.remaining()];
        secondOutput.get(actual);

        assertArrayEquals(new byte[]{5, 6, 7, 8}, actual);
        assertFalse(recycledOutput.hasRemaining());
    }
}
