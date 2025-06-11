package pl.qprogramming.themplay.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import pl.qprogramming.themplay.domain.Playlist;
import pl.qprogramming.themplay.domain.Preset;
import pl.qprogramming.themplay.domain.Song;
import pl.qprogramming.themplay.repository.PlaylistRepository;
import pl.qprogramming.themplay.repository.PresetRepository;
import pl.qprogramming.themplay.repository.SongRepository;

@Database(entities = {Playlist.class, Song.class, Preset.class}, version = 2)
@TypeConverters({Converters.class})
public abstract class ThemplayDatabase extends RoomDatabase {

    public abstract PlaylistRepository playlistRepository();

    public abstract SongRepository songRepository();

    public abstract PresetRepository presetRepository();

    private static volatile ThemplayDatabase INSTANCE;

    public static ThemplayDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (ThemplayDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    ThemplayDatabase.class, "themplay")
                            // Add migrations here
                            .addMigrations(MIGRATION_1_2)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE song ADD COLUMN artist TEXT");
            database.execSQL("ALTER TABLE song ADD COLUMN title TEXT");
        }
    };
};