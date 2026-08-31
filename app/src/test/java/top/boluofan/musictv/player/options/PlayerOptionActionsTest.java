package top.boluofan.musictv.player.options;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class PlayerOptionActionsTest {
    @Test
    public void labelsMapToSixActionsInAdapterOrder() {
        assertEquals(6, PlayerOptionActions.labels().size());
        for (int index = 0; index < PlayerOptionActions.labels().size(); index++) {
            assertEquals(PlayerOptionActions.Action.values()[index],
                    PlayerOptionActions.actionFor(PlayerOptionActions.labels().get(index)));
        }
        assertNull(PlayerOptionActions.actionFor("unknown"));
    }

    @Test
    public void editableArtistClearsOnlyCurrentPlaceholderValues() {
        assertEquals("", PlayerOptionActions.editableArtist(null));
        assertEquals("", PlayerOptionActions.editableArtist("加载中..."));
        assertEquals("", PlayerOptionActions.editableArtist("未知艺术家"));
        assertEquals(" Artist ", PlayerOptionActions.editableArtist(" Artist "));
    }

}
