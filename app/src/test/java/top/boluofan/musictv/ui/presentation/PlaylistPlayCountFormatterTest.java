package top.boluofan.musictv.ui.presentation;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import top.boluofan.musictv.catalog.model.Playlist;

public class PlaylistPlayCountFormatterTest {
    @Test
    public void formatsNumericThresholdsAndEmptyValues() {
        Playlist playlist = new Playlist();
        assertEquals("", PlaylistPlayCountFormatter.format(playlist));

        playlist.setPlayCount(9999L);
        assertEquals("9999", PlaylistPlayCountFormatter.format(playlist));
        playlist.setPlayCount(10000L);
        assertEquals("1.0万", PlaylistPlayCountFormatter.format(playlist));
        playlist.setPlayCount(100000000L);
        assertEquals("1.0亿", PlaylistPlayCountFormatter.format(playlist));
    }
}
