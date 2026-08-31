package top.boluofan.musictv.player.session;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MediaControllerAccessPolicyTest {
    @Test
    public void applicationControllerReceivesPrivateCommands() {
        assertTrue(MediaControllerAccessPolicy.grantsPrivateCommands(
                "io.github.muggles001.mugglesmusic",
                "io.github.muggles001.mugglesmusic"));
    }

    @Test
    public void externalControllerDoesNotReceivePrivateCommands() {
        assertFalse(MediaControllerAccessPolicy.grantsPrivateCommands(
                "io.github.muggles001.mugglesmusic",
                "com.example.remote"));
    }

    @Test
    public void missingApplicationIdentityFailsClosed() {
        assertFalse(MediaControllerAccessPolicy.grantsPrivateCommands(
                null,
                "io.github.muggles001.mugglesmusic"));
    }
}
