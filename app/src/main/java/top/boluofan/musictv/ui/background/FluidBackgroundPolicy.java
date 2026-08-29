package top.boluofan.musictv.ui.background;

import top.boluofan.musictv.settings.AppSettingsPreferences;

public final class FluidBackgroundPolicy {
    public static final Profile QUALITY = new Profile(
            384, 216, 960, 540, 65_536, 60, 16_666_667L, 2, 16);
    public static final Profile REDUCED = new Profile(
            256, 144, 640, 360, 32_768, 30, 33_333_333L, 1, 10);

    private FluidBackgroundPolicy() {
    }

    static boolean supportsGlesVersion(int reqGlEsVersion) {
        return reqGlEsVersion >= 0x00030000;
    }

    static Profile profileFor(AppSettingsPreferences.BrowseMotion motion) {
        return motion == AppSettingsPreferences.BrowseMotion.REDUCED ? REDUCED : QUALITY;
    }

    public static final class Profile {
        public final int carrierWidth;
        public final int carrierHeight;
        public final int depositWidth;
        public final int depositHeight;
        public final int particleCount;
        public final int framesPerSecond;
        public final long simulationStepNanos;
        public final int maxSimulationStepsPerFrame;
        public final int pressureIterations;

        Profile(
                int carrierWidth,
                int carrierHeight,
                int depositWidth,
                int depositHeight,
                int particleCount,
                int framesPerSecond,
                long simulationStepNanos,
                int maxSimulationStepsPerFrame,
                int pressureIterations) {
            this.carrierWidth = carrierWidth;
            this.carrierHeight = carrierHeight;
            this.depositWidth = depositWidth;
            this.depositHeight = depositHeight;
            this.particleCount = particleCount;
            this.framesPerSecond = framesPerSecond;
            this.simulationStepNanos = simulationStepNanos;
            this.maxSimulationStepsPerFrame = maxSimulationStepsPerFrame;
            this.pressureIterations = pressureIterations;
        }

        public float simulationStepSeconds() {
            return simulationStepNanos / 1_000_000_000f;
        }
    }
}
