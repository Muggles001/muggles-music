package top.boluofan.musictv.ui.background;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FluidPingPongStateTest {
    @Test
    public void swapAlternatesReadAndWriteIndices() {
        FluidPingPongState state = new FluidPingPongState();

        assertEquals(0, state.readIndex());
        assertEquals(1, state.writeIndex());
        state.swap();
        assertEquals(1, state.readIndex());
        assertEquals(0, state.writeIndex());
        state.swap();
        assertEquals(0, state.readIndex());
        assertEquals(1, state.writeIndex());
    }

    @Test
    public void resetReturnsToInitialPair() {
        FluidPingPongState state = new FluidPingPongState();
        state.swap();

        state.reset();

        assertEquals(0, state.readIndex());
        assertEquals(1, state.writeIndex());
    }
}
