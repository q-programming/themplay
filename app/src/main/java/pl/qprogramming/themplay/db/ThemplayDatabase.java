package pl.qprogramming.themplay.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import pl.qprogramming.themplay.domain.Playlist;
import pl.qprogramming.themplay.domain.Preset;
import pl.qprogramming.themplay.domain.Song;
import pl.qprogramming.themplay.repository.PlaylistRepository;
import pl.qprogramming.themplay.repository.PresetRepository;
import pl.qprogramming.themplay.repository.SongRepository;

@Database(entities = {Playlist.class, Song.class, Preset.class}, version = 1)
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
                            .build();
                }
            }
        }
        return INSTANCE;
    }
};