package top.boluofan.musictv.player.visual;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AsciiRibbonFieldTest {
    private static final int COLUMNS = 300;
    private static final int ROWS = 169;

    @Test
    public void singleRibbonIsBroadAndConnected() {
        AsciiRibbonField field = render(spectrum());
        int visible = 0;
        for (char character : field.getCharacters()) {
            if (character != ' ') visible++;
        }

        int cells = COLUMNS * ROWS;
        assertTrue(visible >= cells * 25 / 100);
        assertTrue(visible <= cells * 38 / 100);
        Bounds largest = largestComponent(field);
        assertTrue(largest.count >= visible * 90 / 100);
        assertTrue(largest.width() >= COLUMNS * 85 / 100);
        assertTrue(largest.height() >= ROWS * 85 / 100);
    }

    @Test
    public void edgeFeatherIsSmoothAndTransparent() {
        float previous = 0f;
        for (int index = 0; index <= 20; index++) {
            float coordinate = 1f - index * 0.014f;
            float alpha = AsciiRibbonField.edgeFeather(coordinate);
            assertTrue(alpha >= previous);
            assertTrue(alpha - previous <= 0.09f);
            previous = alpha;
        }
        assertEquals(0f, AsciiRibbonField.edgeFeather(1f), 0f);
        assertEquals(1f, AsciiRibbonField.edgeFeather(0f), 0f);
    }

    @Test
    public void luminanceRampAlphaAndDepthScaleHaveRange() {
        AsciiRibbonField field = render(spectrum());
        String[] allowedGlyphs = {".:-", "=+*", "#%@"};
        int[] counts = new int[3];
        float minimumScale = 2f;
        float maximumScale = 0f;
        boolean[] alphaLevels = new boolean[256];

        for (int cell = 0; cell < COLUMNS * ROWS; cell++) {
            for (int bucket = 0; bucket < 3; bucket++) {
                char character = field.getBucketCharacters(bucket)[
                        field.getBucketOffset(bucket) + cell
                ];
                if (character == ' ') continue;
                assertTrue(allowedGlyphs[bucket].indexOf(character) >= 0);
                counts[bucket]++;
                alphaLevels[field.getCellAlpha(cell)] = true;
                minimumScale = Math.min(minimumScale, field.getCellGlyphScale(cell));
                maximumScale = Math.max(maximumScale, field.getCellGlyphScale(cell));
            }
        }

        assertTrue(counts[0] > 0 && counts[1] > 0 && counts[2] > 0);
        int distinctAlphaLevels = 0;
        for (boolean present : alphaLevels) {
            if (present) distinctAlphaLevels++;
        }
        assertTrue(distinctAlphaLevels >= 12);
        assertTrue(minimumScale <= 0.7f);
        assertTrue(maximumScale >= 0.98f);
        assertTrue(maximumScale - minimumScale >= 0.3f);
    }

    @Test
    public void normalUsesDiffuseFresnelAndBass() {
        float front = AsciiRibbonField.shade(0f, 0f, 1f, 0f);
        float back = AsciiRibbonField.shade(0f, 0f, -1f, 0f);
        float edge = AsciiRibbonField.shade(1f, 0f, 0f, 0f);

        assertTrue(front > back);
        assertTrue(edge > back);
        assertTrue(AsciiRibbonField.shade(0f, 0f, 1f, 1f) > front);
    }

    @Test
    public void bassIsSmoothedAndDeformsRibbon() {
        float[] silence = new float[24];
        float[] bass = new float[24];
        bass[0] = 1f;
        bass[1] = 1f;
        bass[2] = 1f;
        AsciiRibbonField field = render(silence);
        char[] before = field.getCharacters().clone();

        field.render(1.75f, bass, 0.5f, 1f);
        float firstResponse = field.getSmoothedBass();
        field.render(1.75f, bass, 0.5f, 1f);

        assertTrue(firstResponse > 0f && firstResponse < 1f);
        assertTrue(field.getSmoothedBass() > firstResponse);
        int changed = 0;
        for (int index = 0; index < before.length; index++) {
            if (before[index] != field.getCharacters()[index]) changed++;
        }
        assertTrue(changed > before.length / 30);
    }

    @Test
    public void trebleOnlyShimmersGlyphs() {
        float[] quietTreble = spectrum();
        for (int index = 18; index < 24; index++) quietTreble[index] = 0f;
        AsciiRibbonField base = render(quietTreble);
        float[] loudTreble = quietTreble.clone();
        for (int index = 18; index < 24; index++) loudTreble[index] = 1f;
        AsciiRibbonField shimmer = render(loudTreble);

        int changes = 0;
        for (int cell = 0; cell < base.getCharacters().length; cell++) {
            assertEquals(base.getCharacters()[cell] == ' ', shimmer.getCharacters()[cell] == ' ');
            assertEquals(base.getCellGlyphScale(cell), shimmer.getCellGlyphScale(cell), 0f);
            assertEquals(base.getCellAlpha(cell), shimmer.getCellAlpha(cell));
            if (base.getCharacters()[cell] != shimmer.getCharacters()[cell]) changes++;
        }
        assertTrue(changes > 10);
    }

    @Test
    public void spansEnvelopeGlyphs() {
        AsciiRibbonField field = render(spectrum());
        int slicedCharacters = 0;
        int fullRowCharacters = 0;

        for (int bucket = 0; bucket < 3; bucket++) {
            for (int row = 0; row < ROWS; row++) {
                int first = COLUMNS;
                int last = -1;
                int offset = field.getBucketOffset(bucket) + row * COLUMNS;
                for (int column = 0; column < COLUMNS; column++) {
                    if (field.getBucketCharacters(bucket)[offset + column] == ' ') continue;
                    first = Math.min(first, column);
                    last = column;
                }
                assertEquals(first, field.getRowFirstColumn(bucket, row));
                assertEquals(last, field.getRowLastColumn(bucket, row));
                if (last >= first) {
                    slicedCharacters += last - first + 1;
                    fullRowCharacters += COLUMNS;
                }
            }
        }
        assertTrue(slicedCharacters < fullRowCharacters * 60 / 100);
    }

    @Test
    public void brightGlyphsAreLargeWhiteAndConnected() {
        AsciiRibbonField field = render(spectrum());
        int[] glyphCounts = new int[3];
        int brightMinimumAlpha = 255;
        int brightMaximumAlpha = 0;
        int brightCells = 0;
        for (int cell = 0; cell < COLUMNS * ROWS; cell++) {
            char glyph = field.getCharacters()[cell];
            if (glyph != '#' && glyph != '%' && glyph != '@') continue;
            brightCells++;
            if (glyph == '#') glyphCounts[0]++;
            if (glyph == '%') glyphCounts[1]++;
            if (glyph == '@') glyphCounts[2]++;
            brightMinimumAlpha = Math.min(brightMinimumAlpha, field.getCellAlpha(cell));
            brightMaximumAlpha = Math.max(brightMaximumAlpha, field.getCellAlpha(cell));
        }
        assertTrue(glyphCounts[0] >= brightCells / 10);
        assertTrue(glyphCounts[1] >= brightCells / 10);
        assertTrue(glyphCounts[2] >= brightCells / 10);
        assertTrue(brightMinimumAlpha >= 180);
        assertTrue(brightMaximumAlpha >= 220);
        assertTrue(brightConnectedNeighbors(field) >= brightCells * 70 / 100);
        assertTrue(AsciiRibbonField.glyphWidthFraction(AsciiRibbonField.BUCKET_BRIGHT)
                >= AsciiRibbonField.glyphWidthFraction(AsciiRibbonField.BUCKET_DIM) * 1.7f);
    }

    private static int brightConnectedNeighbors(AsciiRibbonField field) {
        int connected = 0;
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                int cell = row * COLUMNS + column;
                char glyph = field.getCharacters()[cell];
                if (glyph != '#' && glyph != '%' && glyph != '@') continue;
                boolean neighbor = false;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        int x = column + dx;
                        int y = row + dy;
                        if (x < 0 || x >= COLUMNS || y < 0 || y >= ROWS) continue;
                        char next = field.getCharacters()[y * COLUMNS + x];
                        if (next == '#' || next == '%' || next == '@') neighbor = true;
                    }
                }
                if (neighbor) connected++;
            }
        }
        return connected;
    }

    private static AsciiRibbonField render(float[] spectrum) {
        AsciiRibbonField field = new AsciiRibbonField();
        field.resize(COLUMNS, ROWS);
        field.render(1.75f, spectrum, 0.5f, 1f);
        return field;
    }

    private static float[] spectrum() {
        float[] spectrum = new float[24];
        for (int index = 0; index < spectrum.length; index++) {
            spectrum[index] = 0.2f + index / 30f;
        }
        return spectrum;
    }

    private static Bounds largestComponent(AsciiRibbonField field) {
        int cellCount = COLUMNS * ROWS;
        boolean[] visited = new boolean[cellCount];
        int[] queue = new int[cellCount];
        Bounds largest = new Bounds();

        for (int start = 0; start < cellCount; start++) {
            if (visited[start] || field.getCharacters()[start] == ' ') continue;
            Bounds bounds = new Bounds();
            int head = 0;
            int tail = 0;
            queue[tail++] = start;
            visited[start] = true;
            while (head < tail) {
                int cell = queue[head++];
                int row = cell / COLUMNS;
                int column = cell - row * COLUMNS;
                bounds.include(column, row);
                for (int rowOffset = -1; rowOffset <= 1; rowOffset++) {
                    for (int columnOffset = -1; columnOffset <= 1; columnOffset++) {
                        int neighborColumn = column + columnOffset;
                        int neighborRow = row + rowOffset;
                        if (neighborColumn < 0 || neighborColumn >= COLUMNS
                                || neighborRow < 0 || neighborRow >= ROWS) {
                            continue;
                        }
                        int neighbor = neighborRow * COLUMNS + neighborColumn;
                        if (!visited[neighbor] && field.getCharacters()[neighbor] != ' ') {
                            visited[neighbor] = true;
                            queue[tail++] = neighbor;
                        }
                    }
                }
            }
            if (bounds.count > largest.count) largest = bounds;
        }
        return largest;
    }

    private static final class Bounds {
        int minimumX = COLUMNS;
        int maximumX = -1;
        int minimumY = ROWS;
        int maximumY = -1;
        int count;

        void include(int x, int y) {
            minimumX = Math.min(minimumX, x);
            maximumX = Math.max(maximumX, x);
            minimumY = Math.min(minimumY, y);
            maximumY = Math.max(maximumY, y);
            count++;
        }

        int width() {
            return maximumX - minimumX + 1;
        }

        int height() {
            return maximumY - minimumY + 1;
        }
    }
}
