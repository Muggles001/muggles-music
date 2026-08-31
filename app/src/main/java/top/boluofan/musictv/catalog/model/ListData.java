package top.boluofan.musictv.catalog.model;

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
}
