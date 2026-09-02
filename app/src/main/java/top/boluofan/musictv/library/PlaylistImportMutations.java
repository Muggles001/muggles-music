package top.boluofan.musictv.library;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import top.boluofan.musictv.catalog.PlaylistReference;
import top.boluofan.musictv.catalog.model.ListData;
import top.boluofan.musictv.catalog.model.Playlist;

public final class PlaylistImportMutations {
    private PlaylistImportMutations() {
    }

    public static Playlist findBySourceIdentity(List<Playlist> playlists,
                                                PlaylistReference reference) {
        if (playlists == null || reference == null) return null;
        for (Playlist playlist : playlists) {
            if (playlist != null
                    && Objects.equals(reference.getSource(), playlist.getSource())
                    && Objects.equals(reference.getSourceListId(), playlist.getSourceListId())) {
                return playlist;
            }
        }
        return null;
    }

    public static Playlist apply(ListData data, PlaylistImportPreview preview) {
        if (data == null || preview == null || preview.getSongs().isEmpty()) return null;
        List<Playlist> playlists = data.getUserList();
        if (playlists == null) {
            playlists = new ArrayList<>();
            data.setUserList(playlists);
        }

        Playlist target = findBySourceIdentity(playlists, preview.getReference());
        if (target == null) {
            target = new Playlist();
            target.setId("playlist_" + UUID.randomUUID().toString());
            target.setName(preview.getName());
            playlists.add(target);
        }
        replaceSourceSnapshot(target, preview);
        return target;
    }

    private static void replaceSourceSnapshot(Playlist target, PlaylistImportPreview preview) {
        target.setSource(preview.getReference().getSource());
        target.setSourceListId(preview.getReference().getSourceListId());
        target.setSongs(new ArrayList<>(preview.getSongs()));
        target.setSongCount(preview.getSongs().size());
        target.setPicUrl(preview.getCoverUrl());
        target.setDesc(preview.getDescription());
        target.setCreator(preview.getCreator());
        target.setLocationUpdateTime(System.currentTimeMillis());
    }
}
