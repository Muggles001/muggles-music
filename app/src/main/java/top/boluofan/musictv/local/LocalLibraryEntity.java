package top.boluofan.musictv.local;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "local_library")
public class LocalLibraryEntity {
    @PrimaryKey
    public int id;
    public String json;
    public long updatedAt;
}
