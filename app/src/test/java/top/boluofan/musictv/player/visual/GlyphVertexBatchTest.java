package top.boluofan.musictv.player.visual;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GlyphVertexBatchTest {
    @Test
    public void glyphMapsToAtlasSlotAndCellScaledQuad() {
        GlyphVertexBatch batch = new GlyphVertexBatch(4, 32, 3);

        assertTrue(batch.add('@', 2, 3, 10f, 8f, 0.75f, 0.5f,
                0x00a0b0c0, 220));

        assertEquals(1, batch.getGlyphCount());
        assertEquals(12, batch.getVertexValueCount());
        assertEquals(6, batch.getColorCount());
        float[] vertices = batch.getVertices();
        assertEquals(21.25f, vertices[0], 0.0001f);
        assertEquals(26f, vertices[1], 0.0001f);
        assertEquals(28.75f, vertices[10], 0.0001f);
        assertEquals(30f, vertices[11], 0.0001f);
        float[] texture = batch.getTextureCoordinates();
        assertEquals(64f, texture[0], 0.0001f);
        assertEquals(64f, texture[1], 0.0001f);
        assertEquals(96f, texture[10], 0.0001f);
        assertEquals(96f, texture[11], 0.0001f);
        for (int index = 0; index < 6; index++) {
            assertEquals(0xdca0b0c0, batch.getColors()[index]);
        }
    }

    @Test
    public void resetReusesCapacityAndArrays() {
        GlyphVertexBatch batch = new GlyphVertexBatch(2, 32, 3);
        float[] vertices = batch.getVertices();
        float[] texture = batch.getTextureCoordinates();
        int[] colors = batch.getColors();
        assertTrue(batch.add('.', 0, 0, 4f, 4f, 0.5f, 0.5f, 0, 10));
        assertTrue(batch.add('#', 1, 0, 4f, 4f, 0.5f, 0.5f, 0, 20));
        assertFalse(batch.add('@', 2, 0, 4f, 4f, 0.5f, 0.5f, 0, 30));

        batch.reset();

        assertEquals(0, batch.getGlyphCount());
        assertEquals(2, batch.getCapacity());
        assertSame(vertices, batch.getVertices());
        assertSame(texture, batch.getTextureCoordinates());
        assertSame(colors, batch.getColors());
        assertTrue(batch.add(':', 3, 2, 4f, 4f, 0.5f, 0.5f, 0, 255));
    }

    @Test
    public void atlasContainsEveryFieldGlyphExactlyOnce() {
        String expected = ".:-=+*#%@";
        assertEquals(expected.length(), GlyphVertexBatch.glyphCountInAtlas());
        for (int index = 0; index < expected.length(); index++) {
            assertEquals(expected.charAt(index), GlyphVertexBatch.glyphAt(index));
            assertEquals(index, GlyphVertexBatch.glyphIndex(expected.charAt(index)));
        }
        assertEquals(-1, GlyphVertexBatch.glyphIndex('x'));
    }
}
