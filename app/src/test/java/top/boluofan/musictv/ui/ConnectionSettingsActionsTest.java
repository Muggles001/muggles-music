package top.boluofan.musictv.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import top.boluofan.musictv.backend.BackendMode;

public class ConnectionSettingsActionsTest {
    @Test
    public void setupKeepsCurrentModeWhenNotSwitching() {
        assertEquals(BackendMode.NONE,
                ConnectionSettingsActions.selectedMode(BackendMode.NONE, false));
        assertEquals(BackendMode.LXSERVER,
                ConnectionSettingsActions.selectedMode(BackendMode.LXSERVER, false));
        assertEquals(BackendMode.DIRECT_SOURCE,
                ConnectionSettingsActions.selectedMode(BackendMode.DIRECT_SOURCE, false));
    }

    @Test
    public void setupSwitchesBetweenConnectionModes() {
        assertEquals(BackendMode.DIRECT_SOURCE,
                ConnectionSettingsActions.selectedMode(BackendMode.LXSERVER, true));
        assertEquals(BackendMode.LXSERVER,
                ConnectionSettingsActions.selectedMode(BackendMode.DIRECT_SOURCE, true));
        assertEquals(BackendMode.LXSERVER,
                ConnectionSettingsActions.selectedMode(BackendMode.NONE, true));
    }
}
