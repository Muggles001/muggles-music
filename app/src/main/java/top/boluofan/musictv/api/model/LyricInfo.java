package top.boluofan.musictv.api.model;

import com.google.gson.annotations.SerializedName;

public class LyricInfo {
    @SerializedName(value = "lyric", alternate = {"lrc"})
    private String lyric;

    @SerializedName("tlyric")
    private String tlyric;

    @SerializedName("rlyric")
    private String rlyric;

    @SerializedName(value = "lxlyric", alternate = {"klyric"})
    private String lxlyric;

    public String getLyric() {
        return lyric;
    }

    public void setLyric(String lyric) {
        this.lyric = lyric;
    }

    public String getTlyric() {
        return tlyric;
    }

    public void setTlyric(String tlyric) {
        this.tlyric = tlyric;
    }

    public String getRlyric() {
        return rlyric;
    }

    public void setRlyric(String rlyric) {
        this.rlyric = rlyric;
    }

    public String getLxlyric() {
        return lxlyric;
    }

    public void setLxlyric(String lxlyric) {
        this.lxlyric = lxlyric;
    }

    public boolean hasLyric() {
        return lyric != null && !lyric.isEmpty();
    }

    public boolean hasTlyric() {
        return tlyric != null && !tlyric.isEmpty();
    }

    public boolean hasLxlyric() {
        return lxlyric != null && !lxlyric.isEmpty();
    }

    public String getBestLyric() {
        if (hasText(lyric)) return lyric;
        if (hasText(lxlyric)) return lxlyric.replaceAll("<-?\\d+,-?\\d+>", "");
        if (hasText(tlyric)) return tlyric;
        if (hasText(rlyric)) return rlyric;
        return "";
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
