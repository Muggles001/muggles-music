package top.boluofan.musictv.ui.background;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
    public void passPlanMatchesDirectSceneOutput() {
        assertEquals(13, FluidRendererPlan.flowStepPassCount(10));
        assertEquals(4, FluidRendererPlan.fastStepPassCount());
        assertEquals(1, FluidRendererPlan.surfacePassCount());
        assertEquals(23, FluidRendererPlan.twoFastStepPassCount(10));
    }

    @Test
    public void flowCadenceRunsQualityAtHalfTheFastStepRate() {
        FluidRendererPlan.FlowCadence cadence = new FluidRendererPlan.FlowCadence(2);
        int flowSteps = 0;

        for (int fastStep = 0; fastStep < 120; fastStep++) {
            if (cadence.shouldRunFlowStep()) flowSteps++;
        }

        assertEquals(60, flowSteps);
    }

    @Test
    public void flowCadenceRunsReducedEveryStepAndResetsWithoutBacklog() {
        FluidRendererPlan.FlowCadence reduced = new FluidRendererPlan.FlowCadence(1);
        for (int fastStep = 0; fastStep < 30; fastStep++) {
            assertTrue(reduced.shouldRunFlowStep());
        }

        FluidRendererPlan.FlowCadence quality = new FluidRendererPlan.FlowCadence(2);
        assertTrue(quality.shouldRunFlowStep());
        assertFalse(quality.shouldRunFlowStep());
        quality.reset();
        assertTrue(quality.shouldRunFlowStep());
        assertFalse(quality.shouldRunFlowStep());
    }

    @Test
    public void simulationFormatsFallBackIndependently() {
        FluidRendererPlan.SimulationFormatPlan narrow =
                FluidRendererPlan.simulationFormatPlan(true, true);
        assertTrue(narrow.narrowVelocity);
        assertTrue(narrow.narrowScalar);

        FluidRendererPlan.SimulationFormatPlan scalarOnly =
                FluidRendererPlan.simulationFormatPlan(false, true);
        assertFalse(scalarOnly.narrowVelocity);
        assertTrue(scalarOnly.narrowScalar);

        FluidRendererPlan.SimulationFormatPlan wide =
                FluidRendererPlan.simulationFormatPlan(false, false);
        assertFalse(wide.narrowVelocity);
        assertFalse(wide.narrowScalar);
    }

    @Test
    public void surfaceTargetsStayWithinProfileLimitWithoutChangingAspectRatio() {
        FluidRendererPlan.RenderSize quality4k = FluidRendererPlan.cappedRenderSize(
                3840, 2160, 960, 540);
        assertEquals(960, quality4k.width);
        assertEquals(540, quality4k.height);

        FluidRendererPlan.RenderSize reduced1080 = FluidRendererPlan.cappedRenderSize(
                1920, 1080, 640, 360);
        assertEquals(640, reduced1080.width);
        assertEquals(360, reduced1080.height);

        FluidRendererPlan.RenderSize smallerSurface = FluidRendererPlan.cappedRenderSize(
                640, 360, 960, 540);
        assertEquals(640, smallerSurface.width);
        assertEquals(360, smallerSurface.height);

        FluidRendererPlan.RenderSize portraitSurface = FluidRendererPlan.cappedRenderSize(
                1080, 1920, 960, 540);
        assertEquals(304, portraitSurface.width);
        assertEquals(540, portraitSurface.height);
    }

    @Test(expected = IllegalArgumentException.class)
    public void surfaceTargetPlanRejectsInvalidDimensions() {
        FluidRendererPlan.cappedRenderSize(1920, 1080, 0, 540);
    }

    @Test
    public void frameCadenceHonorsSixtyAndThirtyFramesPerSecond() {
        FluidRendererPlan.FrameCadence quality = new FluidRendererPlan.FrameCadence(60);
        assertTrue(quality.shouldRender(0L));
        assertFalse(quality.shouldRender(8_333_333L));
        assertTrue(quality.shouldRender(16_666_666L));

        FluidRendererPlan.FrameCadence reduced = new FluidRendererPlan.FrameCadence(30);
        assertTrue(reduced.shouldRender(0L));
        assertFalse(reduced.shouldRender(16_666_666L));
        assertTrue(reduced.shouldRender(33_333_332L));
    }

    @Test
    public void frameCadenceDropsStallBacklogInsteadOfBursting() {
        FluidRendererPlan.FrameCadence cadence = new FluidRendererPlan.FrameCadence(60);
        assertTrue(cadence.shouldRender(0L));
        assertTrue(cadence.shouldRender(1_000_000_000L));
        assertFalse(cadence.shouldRender(1_008_000_000L));
        assertTrue(cadence.shouldRender(1_016_666_666L));
    }
}
