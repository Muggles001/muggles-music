package top.boluofan.musictv.ui.background;

final class FluidPingPongState {
    private int readIndex;

    int readIndex() {
        return readIndex;
    }

    int writeIndex() {
        return readIndex ^ 1;
    }

    void swap() {
        readIndex ^= 1;
    }

    void reset() {
        readIndex = 0;
    }
}
