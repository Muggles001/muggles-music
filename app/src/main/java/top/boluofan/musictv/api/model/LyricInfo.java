package top.boluofan.musictv.api.model;

import com.google.gson.annotations.SerializedName;

public class LyricInfo {
    @SerializedName("lyric")
    private String lyric;

    @SerializedName("tlyric")
    private String tlyric;

    @SerializedName("rlyric")
    private String rlyric;

    @SerializedName("lxlyric")
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
}
