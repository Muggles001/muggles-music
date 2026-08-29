package top.boluofan.musictv.player.visual;

public final class GlyphVertexBatch {
    private static final String GLYPHS = ".:-=+*#%@";
    private static final int VERTICES_PER_GLYPH = 6;
    private static final int FLOAT_VALUES_PER_GLYPH = VERTICES_PER_GLYPH * 2;

    private final int capacity;
    private final int atlasSlotSize;
    private final int atlasColumns;
    private final float[] vertices;
    private final float[] textureCoordinates;
    private final int[] colors;
    private int glyphCount;

    public GlyphVertexBatch(int capacity, int atlasSlotSize, int atlasColumns) {
        this.capacity = capacity;
        this.atlasSlotSize = atlasSlotSize;
        this.atlasColumns = atlasColumns;
        vertices = new float[capacity * FLOAT_VALUES_PER_GLYPH];
        textureCoordinates = new float[capacity * FLOAT_VALUES_PER_GLYPH];
        colors = new int[capacity * VERTICES_PER_GLYPH];
    }

    public void reset() {
        glyphCount = 0;
    }

    public boolean add(char glyph, int column, int row, float cellWidth, float cellHeight,
            float widthFraction, float heightFraction, int rgb, int alpha) {
        int glyphIndex = glyphIndex(glyph);
        if (glyphIndex < 0 || glyphCount >= capacity) return false;

        float width = cellWidth * widthFraction;
        float height = cellHeight * heightFraction;
        float left = column * cellWidth + (cellWidth - width) * 0.5f;
        float top = row * cellHeight + (cellHeight - height) * 0.5f;
        float right = left + width;
        float bottom = top + height;
        int atlasColumn = glyphIndex % atlasColumns;
        int atlasRow = glyphIndex / atlasColumns;
        float textureLeft = atlasColumn * atlasSlotSize;
        float textureTop = atlasRow * atlasSlotSize;
        float textureRight = textureLeft + atlasSlotSize;
        float textureBottom = textureTop + atlasSlotSize;

        int floatOffset = glyphCount * FLOAT_VALUES_PER_GLYPH;
        writeQuad(vertices, floatOffset, left, top, right, bottom);
        writeQuad(textureCoordinates, floatOffset,
                textureLeft, textureTop, textureRight, textureBottom);
        int color = (clampByte(alpha) << 24) | (rgb & 0x00ffffff);
        int colorOffset = glyphCount * VERTICES_PER_GLYPH;
        for (int vertex = 0; vertex < VERTICES_PER_GLYPH; vertex++) {
            colors[colorOffset + vertex] = color;
        }
        glyphCount++;
        return true;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getGlyphCount() {
        return glyphCount;
    }

    public int getVertexValueCount() {
        return glyphCount * FLOAT_VALUES_PER_GLYPH;
    }

    public int getColorCount() {
        return glyphCount * VERTICES_PER_GLYPH;
    }

    public float[] getVertices() {
        return vertices;
    }

    public float[] getTextureCoordinates() {
        return textureCoordinates;
    }

    public int[] getColors() {
        return colors;
    }

    public static int glyphIndex(char glyph) {
        return GLYPHS.indexOf(glyph);
    }

    public static int glyphCountInAtlas() {
        return GLYPHS.length();
    }

    public static char glyphAt(int index) {
        return GLYPHS.charAt(index);
    }

    private static void writeQuad(float[] destination, int offset, float left, float top,
            float right, float bottom) {
        destination[offset] = left;
        destination[offset + 1] = top;
        destination[offset + 2] = left;
        destination[offset + 3] = bottom;
        destination[offset + 4] = right;
        destination[offset + 5] = top;
        destination[offset + 6] = right;
        destination[offset + 7] = top;
        destination[offset + 8] = left;
        destination[offset + 9] = bottom;
        destination[offset + 10] = right;
        destination[offset + 11] = bottom;
    }

    private static int clampByte(int value) {
        return value < 0 ? 0 : Math.min(value, 255);
    }
}
