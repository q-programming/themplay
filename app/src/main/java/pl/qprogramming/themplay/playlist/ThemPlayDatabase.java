package pl.qprogramming.themplay.playlist;

import android.database.sqlite.SQLiteDatabase;

import com.reactiveandroid.annotation.Database;
import com.reactiveandroid.internal.database.migration.Migration;

@Database(name = "themplay", version = 2)
public class ThemPlayDatabase {


    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SQLiteDatabase database) {
            database.execSQL("alter table " + Playlist.PLAYLIST_TABLE_NAME + " add column " + Playlist.BACKGROUND + " TEXT");
            database.execSQL("alter table " + Playlist.PLAYLIST_TABLE_NAME + " add column " + Playlist.TEXT_COLOR + " INTEGER");
            database.execSQL("alter table " + Playlist.PLAYLIST_TABLE_NAME + " add column " + Playlist.TEXT_OUTLINE + " BOOLEAN");
        }
    };
}



