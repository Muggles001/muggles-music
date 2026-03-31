package top.boluofan.musictv.api.model;

import com.google.gson.annotations.SerializedName;

public class MusicUrlResponse {
    @SerializedName("url")
    private String url;

    @SerializedName("type")
    private String type;

    @SerializedName("source")
    private String source;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public boolean isValid() {
        return url != null && !url.isEmpty();
    }
}
