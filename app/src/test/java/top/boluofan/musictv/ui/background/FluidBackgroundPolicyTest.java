package top.boluofan.musictv.ui.background;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import top.boluofan.musictv.settings.AppSettingsPreferences;

public class FluidBackgroundPolicyTest {
    @Test
    public void qualityProfileUsesApprovedReferenceConfiguration() {
        FluidBackgroundPolicy.Profile profile = FluidBackgroundPolicy.QUALITY;

        assertEquals(384, profile.carrierWidth);
        assertEquals(216, profile.carrierHeight);
        assertEquals(960, profile.depositWidth);
        assertEquals(540, profile.depositHeight);
        assertEquals(65_536, profile.particleCount);
        assertEquals(60, profile.framesPerSecond);
        assertEquals(16_666_667L, profile.simulationStepNanos);
        assertEquals(1f / 60f, profile.simulationStepSeconds(), 0.000_001f);
        assertEquals(2, profile.maxSimulationStepsPerFrame);
        assertEquals(16, profile.pressureIterations);
    }

    @Test
    public void qualityPathRequiresGlesThree() {
        assertFalse(FluidBackgroundPolicy.supportsGlesVersion(0x00020000));
        assertTrue(FluidBackgroundPolicy.supportsGlesVersion(0x00030000));
        assertTrue(FluidBackgroundPolicy.supportsGlesVersion(0x00030002));
    }

    @Test
    public void motionPreferenceSelectsARealReducedProfile() {
        assertEquals(FluidBackgroundPolicy.QUALITY,
                FluidBackgroundPolicy.profileFor(AppSettingsPreferences.BrowseMotion.STANDARD));
        FluidBackgroundPolicy.Profile reduced = FluidBackgroundPolicy.profileFor(
                AppSettingsPreferences.BrowseMotion.REDUCED);
        assertEquals(30, reduced.framesPerSecond);
        assertEquals(256, reduced.carrierWidth);
        assertEquals(640, reduced.depositWidth);
        assertEquals(32_768, reduced.particleCount);
        assertEquals(FluidBackgroundPolicy.QUALITY,
                FluidBackgroundPolicy.profileFor(AppSettingsPreferences.BrowseMotion.STILL));
    }
}
