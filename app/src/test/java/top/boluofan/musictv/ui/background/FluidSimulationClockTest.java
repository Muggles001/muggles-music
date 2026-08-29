package top.boluofan.musictv.ui.background;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FluidSimulationClockTest {
    private static final long STEP_NANOS = 10_000_000L;

    @Test
    public void approvedCadenceAdvancesAtSixtyHertz() {
        FluidSimulationClock clock = new FluidSimulationClock(16_666_667L, 2);
        clock.resetWallTime(0L);

        assertEquals(0, clock.advance(16_666_666L));
        assertEquals(1, clock.advance(16_666_667L));
        assertEquals(1f / 60f, clock.stepSeconds(), 0.000_001f);
    }

    @Test
    public void advancesOnlyWhenAccumulatedTimeReachesFixedStep() {
        FluidSimulationClock clock = new FluidSimulationClock(STEP_NANOS, 2);
        clock.resetWallTime(1_000_000_000L);

        assertEquals(0, clock.advance(1_006_000_000L));
        assertEquals(1, clock.advance(1_011_000_000L));
        assertEquals(0.01, clock.simulationTimeSeconds(), 0.000_000_1);
        assertEquals(0.01f, clock.stepSeconds(), 0.000_000_1f);
    }

    @Test
    public void capsCatchUpAndDropsStallBacklog() {
        FluidSimulationClock clock = new FluidSimulationClock(STEP_NANOS, 2);
        clock.resetWallTime(0L);

        assertEquals(2, clock.advance(1_000_000_000L));
        assertEquals(0, clock.advance(1_001_000_000L));
        assertEquals(0.02, clock.simulationTimeSeconds(), 0.000_000_1);
    }

    @Test
    public void resetWallTimeDropsPartialFrameButPreservesSimulationTime() {
        FluidSimulationClock clock = new FluidSimulationClock(STEP_NANOS, 2);
        clock.resetWallTime(0L);
        assertEquals(1, clock.advance(STEP_NANOS));
        assertEquals(0, clock.advance(STEP_NANOS + 6_000_000L));

        clock.resetWallTime(5_000_000_000L);

        assertEquals(0, clock.advance(5_004_000_000L));
        assertEquals(0.01, clock.simulationTimeSeconds(), 0.000_000_1);
    }

    @Test
    public void firstAdvanceEstablishesWallTime() {
        FluidSimulationClock clock = new FluidSimulationClock(STEP_NANOS, 1);

        assertEquals(0, clock.advance(100L));
        assertEquals(1, clock.advance(100L + STEP_NANOS));
    }

    @Test
    public void repeatedWallTimePreservesPartialStep() {
        FluidSimulationClock clock = new FluidSimulationClock(STEP_NANOS, 1);
        clock.resetWallTime(0L);

        assertEquals(0, clock.advance(6_000_000L));
        assertEquals(0, clock.advance(6_000_000L));
        assertEquals(1, clock.advance(10_000_000L));
    }
}
