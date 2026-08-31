package top.boluofan.musictv.player.options;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class PlayerOptionActions {
    public static final String SAVE_LYRICS_LABEL = "保存歌词（暂不支持）";
    private static final List<String> LABELS = Collections.unmodifiableList(Arrays.asList(
            "修改歌手名",
            "重新刮削歌词",
            "歌词提前 0.25 秒",
            "歌词延后 0.25 秒",
            "重置歌词时间",
            SAVE_LYRICS_LABEL
    ));

    private PlayerOptionActions() {
    }

    public static List<String> labels() {
        return LABELS;
    }

    public static Action actionFor(String label) {
        int index = LABELS.indexOf(label);
        return index >= 0 ? Action.values()[index] : null;
    }

    public static String editableArtist(String displayedArtist) {
        if (displayedArtist == null
                || "加载中...".equals(displayedArtist)
                || "未知艺术家".equals(displayedArtist)) {
            return "";
        }
        return displayedArtist;
    }

    public enum Action {
        EDIT_ARTIST,
        FORCE_SCRAPE,
        ADVANCE_LYRICS,
        DELAY_LYRICS,
        RESET_LYRIC_TIMING,
        SAVE_LYRICS
    }

}
