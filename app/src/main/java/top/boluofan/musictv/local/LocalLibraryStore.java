package top.boluofan.musictv.local;

import android.content.Context;
import com.google.gson.Gson;
import java.util.ArrayList;
import top.boluofan.musictv.api.model.ListData;

public final class LocalLibraryStore {
    private static final Gson GSON = new Gson();
    private final LocalLibraryDao dao;

    public LocalLibraryStore(Context context) {
        dao = MugglesDatabase.get(context).localLibrary();
    }

    public synchronized ListData get() {
        LocalLibraryEntity entity = dao.get();
        ListData data = entity == null ? new ListData() : GSON.fromJson(entity.json, ListData.class);
        if (data == null) data = new ListData();
        if (data.getDefaultList() == null) data.setDefaultList(new ArrayList<>());
        if (data.getLoveList() == null) data.setLoveList(new ArrayList<>());
        if (data.getUserList() == null) data.setUserList(new ArrayList<>());
        if (data.getTempList() == null) data.setTempList(new ArrayList<>());
        return data;
    }

    public synchronized void save(ListData data) {
        LocalLibraryEntity entity = new LocalLibraryEntity();
        entity.id = 1;
        entity.json = GSON.toJson(data == null ? get() : data);
        entity.updatedAt = System.currentTimeMillis();
        dao.put(entity);
    }

    public synchronized void clear() {
        dao.clear();
    }
}
