package top.boluofan.musictv.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface LocalLibraryDao {
    @Query("SELECT * FROM local_library WHERE id = 1")
    LocalLibraryEntity get();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void put(LocalLibraryEntity entity);

    @Query("DELETE FROM local_library")
    void clear();
}
