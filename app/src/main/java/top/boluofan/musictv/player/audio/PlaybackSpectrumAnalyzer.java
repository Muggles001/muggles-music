package top.boluofan.musictv.player.audio;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.BaseAudioProcessor;
import androidx.media3.common.util.UnstableApi;

import java.nio.ByteBuffer;
import java.util.Arrays;

@OptIn(markerClass = UnstableApi.class)
public final class PlaybackSpectrumAnalyzer extends BaseAudioProcessor {
    private static final int WINDOW_SIZE = 1024;

    private final PlaybackSpectrumBus spectrumBus;
    private final float[] sampleWindow = new float[WINDOW_SIZE];
    private final float[] coefficients = new float[PlaybackSpectrumBus.BAND_COUNT];
    private final float[] previousBands = new float[PlaybackSpectrumBus.BAND_COUNT];
    private final float[] outputBands = new float[PlaybackSpectrumBus.BAND_COUNT];

    private int windowPosition;
    private int bytesPerSample;
    private int channelCount;
    private int encoding;
    private boolean analysisSupported;

    public PlaybackSpectrumAnalyzer() {
        this(PlaybackSpectrumBus.getShared());
    }

    public PlaybackSpectrumAnalyzer(PlaybackSpectrumBus spectrumBus) {
        this.spectrumBus = spectrumBus;
    }

    @Override
    protected AudioProcessor.AudioFormat onConfigure(AudioProcessor.AudioFormat inputAudioFormat) {
        channelCount = Math.max(1, inputAudioFormat.channelCount);
        encoding = inputAudioFormat.encoding;
        bytesPerSample = inputAudioFormat.bytesPerFrame > 0
                ? inputAudioFormat.bytesPerFrame / channelCount : 0;
        analysisSupported = inputAudioFormat.sampleRate > 0
                && isSupportedEncoding(encoding, bytesPerSample);
        if (analysisSupported) {
            try {
                SpectrumMath.prepareCoefficients(inputAudioFormat.sampleRate, coefficients);
            } catch (RuntimeException ignored) {
                analysisSupported = false;
            }
        }
        return inputAudioFormat;
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        int inputPosition = inputBuffer.position();
        int inputSize = inputBuffer.remaining();
        if (analysisSupported && inputSize > 0) {
            try {
                analyze(inputBuffer, inputPosition, inputBuffer.limit());
            } catch (RuntimeException ignored) {
                analysisSupported = false;
            }
        }

        ByteBuffer source = inputBuffer.duplicate();
        inputBuffer.position(inputBuffer.limit());
        ByteBuffer output = replaceOutputBuffer(inputSize);
        output.put(source);
        output.flip();
    }

    @Override
    protected void onFlush() {
        windowPosition = 0;
        Arrays.fill(previousBands, 0f);
        Arrays.fill(outputBands, 0f);
    }

    @Override
    protected void onReset() {
        analysisSupported = false;
        spectrumBus.clear();
    }

    private void analyze(ByteBuffer buffer, int start, int limit) {
        int frameSize = bytesPerSample * channelCount;
        for (int frame = start; frame + frameSize <= limit; frame += frameSize) {
            float mono = 0f;
            for (int channel = 0; channel < channelCount; channel++) {
                mono += readSample(buffer, frame + channel * bytesPerSample);
            }
            sampleWindow[windowPosition++] = mono / channelCount;
            if (windowPosition == WINDOW_SIZE) {
                float level = SpectrumMath.analyze(sampleWindow, WINDOW_SIZE, coefficients,
                        previousBands, outputBands);
                spectrumBus.publish(outputBands, level, System.nanoTime());
                windowPosition = 0;
            }
        }
    }

    private float readSample(ByteBuffer buffer, int index) {
        switch (encoding) {
            case C.ENCODING_PCM_8BIT:
                return ((buffer.get(index) & 0xff) - 128) / 128f;
            case C.ENCODING_PCM_16BIT:
                return readSigned16(buffer, index, false) / 32768f;
            case C.ENCODING_PCM_16BIT_BIG_ENDIAN:
                return readSigned16(buffer, index, true) / 32768f;
            case C.ENCODING_PCM_24BIT:
                return readSigned24(buffer, index, false) / 8388608f;
            case C.ENCODING_PCM_24BIT_BIG_ENDIAN:
                return readSigned24(buffer, index, true) / 8388608f;
            case C.ENCODING_PCM_32BIT:
                return readSigned32(buffer, index, false) / 2147483648f;
            case C.ENCODING_PCM_32BIT_BIG_ENDIAN:
                return readSigned32(buffer, index, true) / 2147483648f;
            case C.ENCODING_PCM_FLOAT:
                float value = Float.intBitsToFloat(readSigned32(buffer, index, false));
                return !Float.isNaN(value) && !Float.isInfinite(value)
                        ? Math.max(-1f, Math.min(1f, value)) : 0f;
            default:
                return 0f;
        }
    }

    private static int readSigned16(ByteBuffer buffer, int index, boolean bigEndian) {
        int first = buffer.get(index) & 0xff;
        int second = buffer.get(index + 1) & 0xff;
        int value = bigEndian ? (first << 8) | second : first | (second << 8);
        return (short) value;
    }

    private static int readSigned24(ByteBuffer buffer, int index, boolean bigEndian) {
        int first = buffer.get(index) & 0xff;
        int second = buffer.get(index + 1) & 0xff;
        int third = buffer.get(index + 2) & 0xff;
        int value = bigEndian
                ? (first << 16) | (second << 8) | third
                : first | (second << 8) | (third << 16);
        return (value << 8) >> 8;
    }

    private static int readSigned32(ByteBuffer buffer, int index, boolean bigEndian) {
        int first = buffer.get(index) & 0xff;
        int second = buffer.get(index + 1) & 0xff;
        int third = buffer.get(index + 2) & 0xff;
        int fourth = buffer.get(index + 3) & 0xff;
        return bigEndian
                ? (first << 24) | (second << 16) | (third << 8) | fourth
                : first | (second << 8) | (third << 16) | (fourth << 24);
    }

    private static boolean isSupportedEncoding(int encoding, int bytesPerSample) {
        switch (encoding) {
            case C.ENCODING_PCM_8BIT:
                return bytesPerSample == 1;
            case C.ENCODING_PCM_16BIT:
            case C.ENCODING_PCM_16BIT_BIG_ENDIAN:
                return bytesPerSample == 2;
            case C.ENCODING_PCM_24BIT:
            case C.ENCODING_PCM_24BIT_BIG_ENDIAN:
                return bytesPerSample == 3;
            case C.ENCODING_PCM_32BIT:
            case C.ENCODING_PCM_32BIT_BIG_ENDIAN:
            case C.ENCODING_PCM_FLOAT:
                return bytesPerSample == 4;
            default:
                return false;
        }
    }
}
