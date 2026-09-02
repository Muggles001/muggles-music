package top.boluofan.musictv.catalog;

import java.util.Objects;

public final class PlaylistReference {
    private final String source;
    private final String sourceListId;
    private final String canonicalUrl;
    private final String detailRequestId;

    public PlaylistReference(String source, String sourceListId, String canonicalUrl) {
        this(source, sourceListId, canonicalUrl, sourceListId);
    }

    public PlaylistReference(String source, String sourceListId, String canonicalUrl,
                             String detailRequestId) {
        this.source = Objects.requireNonNull(source, "source");
        this.sourceListId = Objects.requireNonNull(sourceListId, "sourceListId");
        this.canonicalUrl = canonicalUrl;
        this.detailRequestId = Objects.requireNonNull(detailRequestId, "detailRequestId");
    }

    public String getSource() {
        return source;
    }

    public String getSourceListId() {
        return sourceListId;
    }

    public String getCanonicalUrl() {
        return canonicalUrl;
    }

    public String getDetailRequestId() {
        return detailRequestId;
    }

    public String identityKey() {
        return source + ':' + sourceListId;
    }
}
