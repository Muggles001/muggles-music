package top.boluofan.musictv.api.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class SearchResult {
    @SerializedName("list")
    private List<MusicInfo> list;

    @SerializedName("source")
    private String source;

    @SerializedName("total")
    private int total;

    @SerializedName("page")
    private int page;

    @SerializedName("limit")
    private int limit;

    public List<MusicInfo> getList() {
        return list;
    }

    public void setList(List<MusicInfo> list) {
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

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public boolean hasMore() {
        if (list == null) return false;
        return list.size() >= limit;
    }
}
