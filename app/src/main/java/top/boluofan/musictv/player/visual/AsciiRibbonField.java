package top.boluofan.musictv.player.visual;

import java.util.Arrays;

public final class AsciiRibbonField {
    public static final int BUCKET_DIM = 0;
    public static final int BUCKET_MID = 1;
    public static final int BUCKET_BRIGHT = 2;
    public static final int BUCKET_COUNT = 3;
    private static final String RAMP = " .:-=+*#%@";
    private static final float[] BUCKET_WIDTH = {0.48f, 0.76f, 1.02f};
    private static final float[] BUCKET_HEIGHT = {0.52f, 0.8f, 1.08f};
    private static final int[] BAYER_8X8 = {
            0, 32, 8, 40, 2, 34, 10, 42, 48, 16, 56, 24, 50, 18, 58, 26,
            12, 44, 4, 36, 14, 46, 6, 38, 60, 28, 52, 20, 62, 30, 54, 22,
            3, 35, 11, 43, 1, 33, 9, 41, 51, 19, 59, 27, 49, 17, 57, 25,
            15, 47, 7, 39, 13, 45, 5, 37, 63, 31, 55, 23, 61, 29, 53, 21
    };
    private static final float LIGHT_X = 0.363f;
    private static final float LIGHT_Y = 0.727f;
    private static final float LIGHT_Z = 0.582f;
    private static final float HALF_X = 0.205f;
    private static final float HALF_Y = 0.411f;
    private static final float HALF_Z = 0.888f;

    private int columns;
    private int rows;
    private int cells;
    private char[] composite = new char[0];
    private char[] buckets = new char[0];
    private float[] depth = new float[0];
    private float[] luminance = new float[0];
    private float[] feather = new float[0];
    private float[] parameterU = new float[0];
    private float[] parameterV = new float[0];
    private float[] glyphScale = new float[0];
    private int[] cellAlpha = new int[0];
    private int[] rowAlpha = new int[0];
    private int[] rowFirst = new int[0];
    private int[] rowLast = new int[0];
    private int[] rowCount = new int[0];
    private float[] surfaceX = new float[0];
    private float[] surfaceY = new float[0];
    private float[] surfaceZ = new float[0];
    private float evaluatedX;
    private float evaluatedY;
    private float evaluatedZ;
    private float smoothedBass;

    public static int recommendedColumnCount(int widthPixels) {
        return Math.max(240, Math.min(300, Math.round(widthPixels / 6.4f)));
    }

    public static float glyphWidthFraction(int bucket) {
        return BUCKET_WIDTH[bucket];
    }

    public static float glyphHeightFraction(int bucket) {
        return BUCKET_HEIGHT[bucket];
    }

    public void resize(int newColumns, int newRows) {
        columns = Math.max(1, newColumns);
        rows = Math.max(1, newRows);
        cells = columns * rows;
        composite = new char[cells];
        buckets = new char[cells * BUCKET_COUNT];
        depth = new float[cells];
        luminance = new float[cells];
        feather = new float[cells];
        parameterU = new float[cells];
        parameterV = new float[cells];
        glyphScale = new float[cells];
        cellAlpha = new int[cells];
        rowAlpha = new int[rows * BUCKET_COUNT];
        rowFirst = new int[rows * BUCKET_COUNT];
        rowLast = new int[rows * BUCKET_COUNT];
        rowCount = new int[rows * BUCKET_COUNT];
    }

    public void render(float time, float[] spectrum, float energy, float motion) {
        Arrays.fill(composite, ' ');
        Arrays.fill(buckets, ' ');
        Arrays.fill(depth, Float.POSITIVE_INFINITY);
        Arrays.fill(luminance, 0f);
        Arrays.fill(feather, 0f);
        float bassTarget = average(spectrum, 0, 3);
        if (spectrum == null || spectrum.length == 0) bassTarget = clamp01(energy);
        float response = bassTarget > smoothedBass ? 0.34f : 0.08f;
        smoothedBass += (bassTarget - smoothedBass) * response;
        float treble = average(spectrum, spectrum == null ? 0 : Math.max(0, spectrum.length - 6),
                spectrum == null ? 0 : spectrum.length);
        float animatedTime = time * (0.2f + motion * 0.8f);
        int uSteps = Math.max(200, Math.round(columns * 0.67f));
        int vSteps = Math.max(112, Math.round(rows * 0.67f));
        rasterize(uSteps, vSteps, animatedTime, smoothedBass);
        resolve(animatedTime, treble);
    }

    public char[] getCharacters() {
        return composite;
    }

    public char[] getBucketCharacters(int bucket) {
        return buckets;
    }

    public int getBucketOffset(int bucket) {
        return bucket * cells;
    }

    public int getRowAlpha(int bucket, int row) {
        return rowAlpha[bucket * rows + row];
    }

    public int getRowFirstColumn(int bucket, int row) {
        return rowFirst[bucket * rows + row];
    }

    public int getRowLastColumn(int bucket, int row) {
        return rowLast[bucket * rows + row];
    }

    public int getColumns() {
        return columns;
    }

    public int getRows() {
        return rows;
    }

    public int getCellAlpha(int cell) {
        return cellAlpha[cell];
    }

    public float getCellGlyphScale(int cell) {
        return glyphScale[cell];
    }

    float getCellFeather(int cell) {
        return feather[cell];
    }

    float getCellLuminance(int cell) {
        return luminance[cell];
    }

    float getCellDepth(int cell) {
        return depth[cell];
    }

    float getCellParameterU(int cell) {
        return parameterU[cell];
    }

    float getCellParameterV(int cell) {
        return parameterV[cell];
    }

    float getSmoothedBass() {
        return smoothedBass;
    }

    static float edgeFeather(float v) {
        return smoothStep(0f, 0.28f, 1f - Math.abs(v));
    }

    static float shade(float nx, float ny, float nz, float bass) {
        float inv = inverseLength(nx, ny, nz);
        nx *= inv;
        ny *= inv;
        nz *= inv;
        float diffuse = Math.max(0f, nx * LIGHT_X + ny * LIGHT_Y + nz * LIGHT_Z);
        float halfDot = Math.max(0f, nx * HALF_X + ny * HALF_Y + nz * HALF_Z);
        float specular = (float) Math.pow(halfDot, 16.0);
        float fresnel = (float) Math.pow(1f - Math.abs(nz), 2.5);
        return clamp01(diffuse * 0.6f + specular * 0.5f + fresnel * 0.7f
                + clamp01(bass) * 0.35f);
    }

    private void rasterize(int uSteps, int vSteps, float time, float bass) {
        int sampleCount = uSteps * vSteps;
        ensureSurfaceCapacity(sampleCount);
        float du = 1f / (uSteps - 1);
        float dv = 2f / (vSteps - 1);
        for (int iu = 0; iu < uSteps; iu++) {
            float u = iu * du;
            for (int iv = 0; iv < vSteps; iv++) {
                float v = -1f + iv * dv;
                evaluate(u, v, time, bass);
                int sample = iu * vSteps + iv;
                surfaceX[sample] = evaluatedX;
                surfaceY[sample] = evaluatedY;
                surfaceZ[sample] = evaluatedZ;
            }
        }

        for (int iu = 0; iu < uSteps; iu++) {
            float u = iu * du;
            int neighborU = iu < uSteps - 1 ? iu + 1 : iu - 1;
            float signU = iu < uSteps - 1 ? 1f : -1f;
            for (int iv = 0; iv < vSteps; iv++) {
                float v = -1f + iv * dv;
                int neighborV = iv < vSteps - 1 ? iv + 1 : iv - 1;
                float signV = iv < vSteps - 1 ? 1f : -1f;
                int sample = iu * vSteps + iv;
                int uSample = neighborU * vSteps + iv;
                int vSample = iu * vSteps + neighborV;
                float x = surfaceX[sample];
                float y = surfaceY[sample];
                float z = surfaceZ[sample];
                float ux = (surfaceX[uSample] - x) / (du * signU);
                float uy = (surfaceY[uSample] - y) / (du * signU);
                float uz = (surfaceZ[uSample] - z) / (du * signU);
                float vx = (surfaceX[vSample] - x) / (dv * signV);
                float vy = (surfaceY[vSample] - y) / (dv * signV);
                float vz = (surfaceZ[vSample] - z) / (dv * signV);
                float nx = uy * vz - uz * vy;
                float ny = uz * vx - ux * vz;
                float nz = ux * vy - uy * vx;
                if (nz < 0f) {
                    nx = -nx;
                    ny = -ny;
                    nz = -nz;
                }
                int cx = (int) ((0.5f + x * 0.47f) * columns);
                int cy = (int) ((0.5f + y * 0.47f) * rows);
                if (cx < 0 || cx >= columns || cy < 0 || cy >= rows) continue;
                float light = shade(nx, ny, nz, bass);
                float edge = edgeFeather(v);
                float d = 2.6f - z;
                deposit(cx, cy, d, light, edge, u, v);
                deposit(cx + 1, cy, d, light * 0.98f, edge, u, v);
                deposit(cx, cy + 1, d, light * 0.98f, edge, u, v);
                deposit(cx + 1, cy + 1, d, light * 0.95f, edge, u, v);
            }
        }
    }

    private void ensureSurfaceCapacity(int sampleCount) {
        if (surfaceX.length >= sampleCount) return;
        surfaceX = new float[sampleCount];
        surfaceY = new float[sampleCount];
        surfaceZ = new float[sampleCount];
    }

    private void evaluate(float u, float v, float time, float bass) {
        float noise = valueNoise3(u * 1.8f, v * 1.8f, time * 0.3f) * 2f - 1f;
        float boost = 1f + smoothedBass * 0.4f;
        float leftWindow = 1f - smoothStep(0.28f, 0.4f, u);
        float middleWindow = smoothStep(0.27f, 0.4f, u)
                * (1f - smoothStep(0.62f, 0.74f, u));
        float rightWindow = smoothStep(0.62f, 0.76f, u);
        float progress = clamp01((u - 0.62f) / 0.38f);
        float harmonics = ((float) Math.sin(u * 2.5f * Math.PI + time * 1.2f) * 1.5f
                + (float) Math.cos(v * 4f - time * 0.8f) * 0.8f
                + noise * 1.2f) / 3.5f;
        float middleFold = middleWindow
                * (float) Math.sin(v * 2.7f + u * 8.2f + time * 0.55f) * 0.46f;
        float rightFold = rightWindow
                * (float) Math.sin(progress * 3.4f + v * 2.1f - time * 0.32f) * 0.28f;
        evaluatedZ = (harmonics * 0.82f + middleFold + rightFold) * boost;

        float baseX = -1.12f + u * 2.24f;
        float baseY = 1.16f - u * 2.24f;
        float leftY = baseY + leftWindow * (float) Math.sin(u * 7f + time * 0.42f) * 0.08f;
        float middleY = baseY + middleWindow
                * ((float) Math.sin(u * 1.5f * Math.PI + time * 0.6f) * 0.12f
                + (float) Math.cos(v * 3f + time) * 0.08f);
        float theta = 2.65f - progress * 3.65f;
        float vortexX = 0.48f + (0.43f + v * 0.06f) * (float) Math.cos(theta);
        float vortexY = -0.32f + (0.4f + v * 0.05f) * (float) Math.sin(theta);
        float vortexBlend = rightWindow * rightWindow * (3f - 2f * rightWindow);
        evaluatedX = lerp(baseX, vortexX, vortexBlend) + v * 0.29f
                + middleWindow * v * (float) Math.sin(u * 7.1f + time * 0.4f) * 0.13f
                + noise * 0.018f;
        evaluatedY = lerp(lerp(leftY, middleY, middleWindow), vortexY, vortexBlend)
                + v * lerp(0.28f, 0.17f, vortexBlend) + noise * 0.018f;
    }

    private void deposit(int column, int row, float sampleDepth, float light, float edge,
            float u, float v) {
        if (column < 0 || column >= columns || row < 0 || row >= rows) return;
        int cell = row * columns + column;
        if (sampleDepth < depth[cell] - 0.01f
                || Math.abs(sampleDepth - depth[cell]) <= 0.01f && light > luminance[cell]) {
            depth[cell] = sampleDepth;
            luminance[cell] = light;
            feather[cell] = edge;
            parameterU[cell] = u;
            parameterV[cell] = v;
            float zNormalized = clamp01((2.6f - sampleDepth + 1f) * 0.5f);
            glyphScale[cell] = 0.6f + zNormalized * 0.5f;
        }
    }

    private void resolve(float time, float treble) {
        int shimmerFrame = (int) (time * 12f);
        Arrays.fill(rowFirst, columns);
        Arrays.fill(rowLast, -1);
        Arrays.fill(rowAlpha, 0);
        Arrays.fill(rowCount, 0);
        Arrays.fill(cellAlpha, 0);
        for (int cell = 0; cell < cells; cell++) {
            float styled = luminance[cell];
            if (styled < 0.1f) continue;
            int rampIndex;
            if (styled < 0.35f) {
                rampIndex = 1 + Math.min(1, (int) ((styled - 0.1f) / 0.25f * 2f));
            } else if (styled < 0.75f) {
                rampIndex = 3 + Math.min(3, (int) ((styled - 0.35f) / 0.4f * 4f));
            } else {
                rampIndex = 7 + Math.min(2, (int) ((styled - 0.75f) / 0.25f * 3f));
            }
            int bucket = rampIndex >= 7 ? BUCKET_BRIGHT
                    : rampIndex >= 4 ? BUCKET_MID : BUCKET_DIM;
            if (bucket == BUCKET_BRIGHT && feather[cell] < 0.82f) continue;
            int qu = ((int) (parameterU[cell] * 255f)) & 7;
            int qv = ((int) ((parameterV[cell] + 1f) * 63.5f)) & 7;
            float threshold = (BAYER_8X8[qv * 8 + qu] + 0.5f) / 64f;
            float density = (0.42f + styled * 0.5f) * (0.72f + feather[cell] * 0.28f);
            if (feather[cell] < 0.15f && density <= threshold) continue;
            if (bucket == BUCKET_BRIGHT && treble > 0.05f) {
                int h = hash(cell, shimmerFrame, 91);
                if ((h & 255) < treble * 72f) rampIndex = 7 + Math.abs(h % 3);
            }
            char glyph = RAMP.charAt(rampIndex);
            int alpha = bucket == BUCKET_BRIGHT
                    ? (int) (220f + styled * 35f)
                    : bucket == BUCKET_MID
                    ? (int) (100f + styled * 80f)
                    : (int) (25f + styled * 65f);
            alpha = (int) (alpha * feather[cell]);
            if (alpha <= 0) continue;
            buckets[bucket * cells + cell] = glyph;
            composite[cell] = glyph;
            cellAlpha[cell] = Math.min(255, alpha);
            int row = cell / columns;
            int column = cell - row * columns;
            int ri = bucket * rows + row;
            rowFirst[ri] = Math.min(rowFirst[ri], column);
            rowLast[ri] = Math.max(rowLast[ri], column);
            rowCount[ri]++;
            rowAlpha[ri] += alpha;
        }
        for (int index = 0; index < rowAlpha.length; index++) {
            if (rowCount[index] > 0) rowAlpha[index] /= rowCount[index];
        }
    }

    private static float average(float[] values, int start, int end) {
        if (values == null || start >= end || start >= values.length) return 0f;
        int safeEnd = Math.min(end, values.length);
        float sum = 0f;
        for (int i = start; i < safeEnd; i++) sum += clamp01(values[i]);
        return sum / (safeEnd - start);
    }

    private static float valueNoise3(float x, float y, float z) {
        int ix = floor(x);
        int iy = floor(y);
        int iz = floor(z);
        float fx = x - ix;
        float fy = y - iy;
        float fz = z - iz;
        float sx = fx * fx * (3f - 2f * fx);
        float sy = fy * fy * (3f - 2f * fy);
        float sz = fz * fz * (3f - 2f * fz);
        float a = lerp(hashUnit(ix, iy, iz), hashUnit(ix + 1, iy, iz), sx);
        float b = lerp(hashUnit(ix, iy + 1, iz), hashUnit(ix + 1, iy + 1, iz), sx);
        float c = lerp(hashUnit(ix, iy, iz + 1), hashUnit(ix + 1, iy, iz + 1), sx);
        float d = lerp(hashUnit(ix, iy + 1, iz + 1), hashUnit(ix + 1, iy + 1, iz + 1), sx);
        return lerp(lerp(a, b, sy), lerp(c, d, sy), sz);
    }

    private static int hash(int x, int y, int z) {
        int h = x * 0x1f123bb5 ^ y * 0x5f356495 ^ z * 0x6c8e9cf5;
        h ^= h >>> 16;
        h *= 0x7feb352d;
        h ^= h >>> 15;
        h *= 0x846ca68b;
        return h ^ h >>> 16;
    }

    private static float hashUnit(int x, int y, int z) {
        return (hash(x, y, z) & 0x7fffffff) / 2147483647f;
    }

    private static int floor(float x) {
        int integer = (int) x;
        return x < integer ? integer - 1 : integer;
    }

    private static float lerp(float start, float end, float amount) {
        return start + (end - start) * amount;
    }

    private static float smoothStep(float start, float end, float value) {
        float amount = clamp01((value - start) / (end - start));
        return amount * amount * (3f - 2f * amount);
    }

    private static float inverseLength(float x, float y, float z) {
        return 1f / (float) Math.sqrt(x * x + y * y + z * z + 0.000001f);
    }

    private static float clamp01(float value) {
        return value < 0f ? 0f : Math.min(value, 1f);
    }
}
