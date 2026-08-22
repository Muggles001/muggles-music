package top.boluofan.musictv.local;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {LocalLibraryEntity.class}, version = 1, exportSchema = false)
public abstract class MugglesDatabase extends RoomDatabase {
    private static volatile MugglesDatabase instance;

    public abstract LocalLibraryDao localLibrary();

    public static MugglesDatabase get(Context context) {
        if (instance == null) {
            synchronized (MugglesDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                            MugglesDatabase.class, "muggles-v2.db").build();
                }
            }
        }
        return instance;
    }
}
