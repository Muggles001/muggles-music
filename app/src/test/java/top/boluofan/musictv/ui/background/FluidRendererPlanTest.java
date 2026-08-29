package top.boluofan.musictv.ui.background;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FluidRendererPlanTest {
    @Test
    public void particleLayoutMatchesTransformFeedbackAbi() {
        assertEquals(3, FluidRendererPlan.PARTICLE_VEC4_COUNT);
        assertEquals(12, FluidRendererPlan.PARTICLE_FLOAT_COUNT);
        assertEquals(48, FluidRendererPlan.PARTICLE_STRIDE_BYTES);
    }

    @Test
    public void particleInitializationIsDeterministicAndBounded() {
        float[] first = FluidRendererPlan.createInitialParticles(65_536);
        float[] second = FluidRendererPlan.createInitialParticles(65_536);

        int[] horizontalBands = new int[8];
        float[] orderedSectionX = new float[8];
        float[] orderedSectionY = new float[8];

        assertArrayEquals(first, second, 0f);
        assertEquals(65_536 * FluidRendererPlan.PARTICLE_FLOAT_COUNT, first.length);
        for (int particle = 0; particle < 65_536; particle++) {
            int offset = particle * FluidRendererPlan.PARTICLE_FLOAT_COUNT;
            assertTrue(first[offset] >= 0f && first[offset] <= 1f);
            assertTrue(first[offset + 1] >= 0f && first[offset + 1] <= 1f);
            assertTrue(first[offset + 4] > 0f && first[offset + 4] <= 1f);
            assertEquals(0f, first[offset + 5], 0f);
            assertEquals(0f, first[offset + 6], 0f);
            assertTrue(first[offset + 7] > 0f);
            assertTrue(first[offset + 8] > 0f && first[offset + 8] < 1f);
            assertEquals(0f, first[offset + 9], 0f);
            assertEquals(0f, first[offset + 10], 0f);
            assertEquals(0f, first[offset + 11], 0f);

            int horizontalBand = Math.min(horizontalBands.length - 1,
                    (int) (first[offset] * horizontalBands.length));
            horizontalBands[horizontalBand]++;
            int orderedSection = particle * orderedSectionX.length / 65_536;
            orderedSectionX[orderedSection] += first[offset];
            orderedSectionY[orderedSection] += first[offset + 1];
        }
        assertNotEquals(first[8], first[FluidRendererPlan.PARTICLE_FLOAT_COUNT + 8], 0f);
        for (int bandPopulation : horizontalBands) {
            assertTrue(bandPopulation > 2_500);
        }
        for (int section = 0; section < orderedSectionX.length; section++) {
            orderedSectionX[section] /= 8_192f;
            orderedSectionY[section] /= 8_192f;
            if (section == 0) continue;
            assertTrue(orderedSectionX[section] > orderedSectionX[section - 1]);
            assertTrue(Math.abs(orderedSectionY[section] - orderedSectionY[section - 1]) < 0.18f);
        }
        assertTrue(orderedSectionX[0] < 0.16f);
        assertTrue(orderedSectionX[7] > 0.84f);
        assertTrue(Math.abs(orderedSectionY[2] - orderedSectionY[5]) > 0.08f);
    }

    @Test
    public void passAndBloomPlansMatchReferenceQuality() {
        assertEquals(23, FluidRendererPlan.fixedStepPassCount(16));
        assertEquals(480, FluidRendererPlan.bloomDimension(1920, 4));
        assertEquals(270, FluidRendererPlan.bloomDimension(1080, 4));
        assertEquals(240, FluidRendererPlan.bloomDimension(1920, 8));
        assertEquals(135, FluidRendererPlan.bloomDimension(1080, 8));
        assertEquals(1, FluidRendererPlan.bloomDimension(3, 8));
    }
}
