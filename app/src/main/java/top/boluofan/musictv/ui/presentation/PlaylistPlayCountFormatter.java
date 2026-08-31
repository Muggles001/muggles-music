package top.boluofan.musictv.ui.presentation;

import top.boluofan.musictv.catalog.model.Playlist;

public final class PlaylistPlayCountFormatter {
    private PlaylistPlayCountFormatter() {
    }

    public static String format(Playlist playlist) {
        if (playlist.getInfo() != null && playlist.getInfo().getPlayCount() != null
                && !playlist.getInfo().getPlayCount().isEmpty()) {
            return playlist.getInfo().getPlayCount();
        }
        if (playlist.getPlayCountStr() != null && !playlist.getPlayCountStr().isEmpty()) {
            return playlist.getPlayCountStr();
        }
        Long playCount = playlist.getPlayCount();
        if (playCount == null || playCount == 0) return "";
        if (playCount >= 100000000) return String.format("%.1f亿", playCount / 100000000.0);
        if (playCount >= 10000) return String.format("%.1f万", playCount / 10000.0);
        return String.valueOf(playCount);
    }
}
