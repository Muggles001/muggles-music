package top.boluofan.musictv.api.model;

import android.os.Bundle;
import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

public class MusicInfo {
    @SerializedName("id")
    private String id;

    @SerializedName(value = "name", alternate = {"songName", "songname"})
    private String name;

    @SerializedName(value = "singer", alternate = {"singerName", "singername", "artist"})
    private String singer;

    @SerializedName("source")
    private String source;

    @SerializedName("interval")
    private String interval;

    @SerializedName(value = "img", alternate = {"picUrl"})
    private String img;

    @SerializedName("albumId")
    private String albumId;

    @SerializedName(value = "albumName", alternate = {"album"})
    private String albumName;

    @SerializedName("songmid")
    private String songmid;

    @SerializedName("songId")
    private String songId;

    @SerializedName("albumMid")
    private String albumMid;

    @SerializedName("strMediaMid")
    private String strMediaMid;

    @SerializedName("_interval")
    private Long rawInterval;

    @SerializedName("hash")
    private String hash;

    @SerializedName("copyrightId")
    private String copyrightId;

    @SerializedName(value = "lrcUrl", alternate = {"lyricUrl"})
    private String lrcUrl;

    @SerializedName(value = "mrcUrl", alternate = {"mrcurl"})
    private String mrcUrl;

    @SerializedName("trcUrl")
    private String trcUrl;

    @SerializedName("types")
    private List<QualityInfo> types;

    @SerializedName("_types")
    private Map<String, QualityDetail> _types;

    @SerializedName("meta")
    private MusicMeta meta;
    
    private String searchSource;
    
    public String getSearchSource() {
        return searchSource;
    }
    
    public void setSearchSource(String searchSource) {
        this.searchSource = searchSource;
    }
    
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        if (hasText(name)) return name;
        if (meta != null && hasText(meta.name)) return meta.name;
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSinger() {
        if (hasText(singer)) return singer;
        if (meta != null && hasText(meta.singer)) return meta.singer;
        return singer;
    }

    public void setSinger(String singer) {
        this.singer = singer;
    }

    public String getSource() {
        if (hasText(source)) return source;
        if (meta != null && hasText(meta.source)) return meta.source;
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getInterval() {
        if (hasText(interval)) return interval;
        if (meta != null && hasText(meta.interval)) return meta.interval;
        return interval;
    }

    public void setInterval(String interval) {
        this.interval = interval;
    }

    public MusicMeta getMeta() {
        return meta;
    }

    public void setMeta(MusicMeta meta) {
        this.meta = meta;
    }

    public String getPicUrl() {
        if (img != null) return img;
        if (meta != null && meta.picUrl != null) return meta.picUrl;
        return null;
    }

    public void setPicUrl(String picUrl) {
        this.img = picUrl;
    }

    public List<QualityInfo> getTypes() {
        if (types != null) return types;
        if (meta != null && meta.qualitys != null) return meta.qualitys;
        return null;
    }

    public String getImg() {
        return getPicUrl();
    }

    public void setImg(String img) {
        this.img = img;
    }

    public String getAlbumId() {
        if (hasText(albumId)) return albumId;
        if (meta != null && hasText(meta.albumId)) return meta.albumId;
        return albumId;
    }

    public void setAlbumId(String albumId) {
        this.albumId = albumId;
    }

    public String getAlbumName() {
        if (albumName != null) return albumName;
        if (meta != null && meta.albumName != null) return meta.albumName;
        return null;
    }

    public void setAlbumName(String albumName) {
        this.albumName = albumName;
    }

    public String getSongmid() {
        if (songmid != null && !songmid.isEmpty()) return songmid;
        if (songId != null && !songId.isEmpty()) return songId;
        if (meta != null && meta.songId != null) {
            return meta.songId;
        }
        return id;
    }

    public void setSongmid(String songmid) {
        this.songmid = songmid;
    }

    public String getSongId() {
        return songId;
    }

    public void setSongId(String songId) {
        this.songId = songId;
    }

    public String getAlbumMid() {
        return albumMid;
    }

    public void setAlbumMid(String albumMid) {
        this.albumMid = albumMid;
    }

    public String getStrMediaMid() {
        return strMediaMid;
    }

    public void setStrMediaMid(String strMediaMid) {
        this.strMediaMid = strMediaMid;
    }

    public Long getRawInterval() {
        return rawInterval;
    }

    public void setRawInterval(Long rawInterval) {
        this.rawInterval = rawInterval;
    }

    public String getHash() {
        if (hasText(hash)) return hash;
        if (meta != null && hasText(meta.hash)) return meta.hash;
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getCopyrightId() {
        if (hasText(copyrightId)) return copyrightId;
        if (meta != null && hasText(meta.copyrightId)) return meta.copyrightId;
        return copyrightId;
    }

    public void setCopyrightId(String copyrightId) {
        this.copyrightId = copyrightId;
    }

    public String getLrcUrl() {
        if (hasText(lrcUrl)) return lrcUrl;
        if (meta != null && hasText(meta.lrcUrl)) return meta.lrcUrl;
        return lrcUrl;
    }

    public void setLrcUrl(String lrcUrl) {
        this.lrcUrl = lrcUrl;
    }

    public String getMrcUrl() {
        if (hasText(mrcUrl)) return mrcUrl;
        if (meta != null && hasText(meta.mrcUrl)) return meta.mrcUrl;
        return mrcUrl;
    }

    public void setMrcUrl(String mrcUrl) {
        this.mrcUrl = mrcUrl;
    }

    public String getTrcUrl() {
        if (hasText(trcUrl)) return trcUrl;
        if (meta != null && hasText(meta.trcUrl)) return meta.trcUrl;
        return trcUrl;
    }

    public void setTrcUrl(String trcUrl) {
        this.trcUrl = trcUrl;
    }

    public Bundle toPlaybackExtras() {
        Bundle extras = new Bundle();
        putString(extras, "song_id", getId());
        putString(extras, "source", getSource());
        putString(extras, "songmid", getSongmid());
        putString(extras, "pic_url", getPicUrl());
        putString(extras, "original_name", getName());
        putString(extras, "singer", getSinger());
        putString(extras, "hash", getHash());
        putString(extras, "interval", getInterval());
        putString(extras, "copyrightId", getCopyrightId());
        putString(extras, "albumId", getAlbumId());
        putString(extras, "lrcUrl", getLrcUrl());
        putString(extras, "mrcUrl", getMrcUrl());
        putString(extras, "trcUrl", getTrcUrl());
        return extras;
    }

    private static void putString(Bundle extras, String key, String value) {
        extras.putString(key, value == null ? "" : value);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public Map<String, QualityDetail> get_types() {
        if (_types != null) return _types;
        if (meta != null) return meta._qualitys;
        return null;
    }

    public void setTypes(List<QualityInfo> types) {
        this.types = types;
    }

    public void set_types(Map<String, QualityDetail> _types) {
        this._types = _types;
    }

    public static class MusicMeta {
        @SerializedName(value = "songId", alternate = {"songmid"})
        private String songId;

        @SerializedName(value = "name", alternate = {"songName", "songname"})
        private String name;

        @SerializedName(value = "singer", alternate = {"singerName", "singername", "artist"})
        private String singer;

        @SerializedName("source")
        private String source;

        @SerializedName("hash")
        private String hash;

        @SerializedName("interval")
        private String interval;

        @SerializedName("copyrightId")
        private String copyrightId;

        @SerializedName("albumId")
        private String albumId;

        @SerializedName(value = "lrcUrl", alternate = {"lyricUrl"})
        private String lrcUrl;

        @SerializedName(value = "mrcUrl", alternate = {"mrcurl"})
        private String mrcUrl;

        @SerializedName("trcUrl")
        private String trcUrl;

        @SerializedName("albumName")
        private String albumName;

        @SerializedName("picUrl")
        private String picUrl;

        @SerializedName("qualitys")
        private List<QualityInfo> qualitys;

        @SerializedName("_qualitys")
        private Map<String, QualityDetail> _qualitys;

        public String getSongId() {
            return songId;
        }

        public void setSongId(String songId) {
            this.songId = songId;
        }

        public String getAlbumName() {
            return albumName;
        }

        public void setAlbumName(String albumName) {
            this.albumName = albumName;
        }

        public String getPicUrl() {
            return picUrl;
        }

        public void setPicUrl(String picUrl) {
            this.picUrl = picUrl;
        }

        public List<QualityInfo> getQualitys() {
            return qualitys;
        }

        public void setQualitys(List<QualityInfo> qualitys) {
            this.qualitys = qualitys;
        }

        public Map<String, QualityDetail> get_qualitys() {
            return _qualitys;
        }

        public void set_qualitys(Map<String, QualityDetail> _qualitys) {
            this._qualitys = _qualitys;
        }
    }

    public static class QualityInfo {
        @SerializedName("type")
        private String type;

        @SerializedName("size")
        private String size;

        @SerializedName("hash")
        private String hash;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getSize() {
            return size;
        }

        public void setSize(String size) {
            this.size = size;
        }

        public String getHash() {
            return hash;
        }

        public void setHash(String hash) {
            this.hash = hash;
        }
    }

    public static class QualityDetail {
        @SerializedName("size")
        private String size;

        @SerializedName("hash")
        private String hash;

        public String getSize() {
            return size;
        }

        public void setSize(String size) {
            this.size = size;
        }

        public String getHash() {
            return hash;
        }

        public void setHash(String hash) {
            this.hash = hash;
        }
    }
}
