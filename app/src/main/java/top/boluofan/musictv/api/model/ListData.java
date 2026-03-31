package top.boluofan.musictv.api.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class ListData {
    @SerializedName("defaultList")
    private List<MusicInfo> defaultList;

    @SerializedName("loveList")
    private List<MusicInfo> loveList;

    @SerializedName("userList")
    private List<Playlist> userList;

    @SerializedName("tempList")
    private List<MusicInfo> tempList;

    public List<MusicInfo> getDefaultList() {
        return defaultList;
    }

    public void setDefaultList(List<MusicInfo> defaultList) {
        this.defaultList = defaultList;
    }

    public List<MusicInfo> getLoveList() {
        return loveList;
    }

    public void setLoveList(List<MusicInfo> loveList) {
        this.loveList = loveList;
    }

    public List<Playlist> getUserList() {
        return userList;
    }

    public void setUserList(List<Playlist> userList) {
        this.userList = userList;
    }

    public List<MusicInfo> getTempList() {
        return tempList;
    }

    public void setTempList(List<MusicInfo> tempList) {
        this.tempList = tempList;
    }

    public Playlist getDefaultPlaylist() {
        Playlist playlist = new Playlist();
        playlist.setId("default");
        playlist.setName("试听列表");
        playlist.setSongs(defaultList);
        playlist.setDefault(true);
        return playlist;
    }

    public Playlist getLovePlaylist() {
        Playlist playlist = new Playlist();
        playlist.setId("love");
        playlist.setName("我的收藏");
        playlist.setSongs(loveList);
        playlist.setLove(true);
        return playlist;
    }

    public Playlist getTempPlaylist() {
        Playlist playlist = new Playlist();
        playlist.setId("temp");
        playlist.setName("临时列表");
        playlist.setSongs(tempList);
        return playlist;
    }
}
