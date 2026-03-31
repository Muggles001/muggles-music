package top.boluofan.musictv.api.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class HotListResponse {
    @SerializedName("list")
    private List<HotListItem> list;

    @SerializedName("source")
    private String source;

    @SerializedName("total")
    private int total;

    public List<HotListItem> getList() {
        return list;
    }

    public void setList(List<HotListItem> list) {
        this.list = list;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public static class HotListItem {
        @SerializedName("id")
        private String id;

        @SerializedName("name")
        private String name;

        @SerializedName("desc")
        private String description;

        @SerializedName("pic")
        private String pic;

        @SerializedName("songCount")
        private int songCount;

        @SerializedName("source")
        private String source;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getPic() {
            return pic;
        }

        public void setPic(String pic) {
            this.pic = pic;
        }

        public int getSongCount() {
            return songCount;
        }

        public void setSongCount(int songCount) {
            this.songCount = songCount;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getCoverUrl() {
            return pic;
        }
    }
}
