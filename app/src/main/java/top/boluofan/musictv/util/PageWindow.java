package top.boluofan.musictv.util;

import java.util.ArrayList;
import java.util.List;

public final class PageWindow<T> {
    private final List<T> items;
    private final int pageIndex;
    private final int pageCount;
    private final int startIndex;

    private PageWindow(List<T> items, int pageIndex, int pageCount, int startIndex) {
        this.items = items;
        this.pageIndex = pageIndex;
        this.pageCount = pageCount;
        this.startIndex = startIndex;
    }

    public static <T> PageWindow<T> of(List<T> source, int requestedPage, int pageSize) {
        if (pageSize <= 0) throw new IllegalArgumentException("pageSize must be positive");
        int pageCount = Math.max(1, (source.size() + pageSize - 1) / pageSize);
        int pageIndex = Math.max(0, Math.min(requestedPage, pageCount - 1));
        int startIndex = Math.min(pageIndex * pageSize, source.size());
        int endIndex = Math.min(startIndex + pageSize, source.size());
        return new PageWindow<>(new ArrayList<>(source.subList(startIndex, endIndex)),
                pageIndex, pageCount, startIndex);
    }

    public List<T> getItems() {
        return items;
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public int getPageCount() {
        return pageCount;
    }

    public int getStartIndex() {
        return startIndex;
    }

    public boolean hasPrevious() {
        return pageIndex > 0;
    }

    public boolean hasNext() {
        return pageIndex + 1 < pageCount;
    }

    public int pageAfter(int delta) {
        return Math.max(0, Math.min(pageIndex + delta, pageCount - 1));
    }
}
