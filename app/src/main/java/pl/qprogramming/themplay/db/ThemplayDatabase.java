package pl.qprogramming.themplay.db;

import static pl.qprogramming.themplay.domain.Playlist.CURRENT_SONG_ID;

import android.content.Context;
import android.util.Log;

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

@Database(entities = {Playlist.class, Song.class, Preset.class}, version = 4)
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
                            .addMigrations(MIGRATION_3_4_ROOM_TRANSFORM)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Migration from ReActiveAndroid to Room
     */
    static final Migration MIGRATION_3_4_ROOM_TRANSFORM = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            //add new columns to Playlist
            database.execSQL("ALTER TABLE " + Playlist.PLAYLIST_TABLE_NAME +
                    " ADD COLUMN " + CURRENT_SONG_ID + " INTEGER DEFAULT NULL");
            //add new column to song
            database.execSQL("ALTER TABLE " + Song.SONG_TABLE_NAME +
                    " ADD COLUMN " + Song.COLUMN_PLAYLIST_OWNER_ID + " INTEGER DEFAULT NULL");
            //migrate playlist_songs to new format
            database.execSQL("CREATE TABLE IF NOT EXISTS `playlist_songs_new` (" +
                    "`playlist` INTEGER NOT NULL, " + // Assuming this was the FK col name to playlists.id
                    "`song` INTEGER NOT NULL, " +     // Assuming this was the FK col name to song.id
                    "PRIMARY KEY(`playlist`, `song`), " +
                    "FOREIGN KEY(`playlist`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(`song`) REFERENCES `song`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_songs_new_playlist` ON `playlist_songs_new` (`playlist`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_songs_new_song` ON `playlist_songs_new` (`song`)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_presets_name ON "+ Preset.PRESET_TABLE_NAME + " (name)");

            // Copy data from old playlist_songs to new one.
            // This assumes your old `playlist_songs` table had columns `playlist` and `song` storing the respective IDs.
            database.execSQL("INSERT INTO `playlist_songs_new` (playlist, song) SELECT playlist, song FROM `playlist_songs`");

            database.execSQL("DROP TABLE `playlist_songs`");
            database.execSQL("ALTER TABLE `playlist_songs_new` RENAME TO `playlist_songs`");
            // Now, "playlist_songs" matches the structure expected by PlaylistSongCrossRef with composite PK.

            // If your Song table was named something else by ReactiveAndroid and Room expects "song", rename it.
            // (Your current Song entity uses tableName="song", so this should be fine if old was also "song").

            // If Preset is a new table for version 4:
//            database.execSQL("CREATE TABLE IF NOT EXISTS `presets` (" + // Assuming 'presets' is table name for Preset entity
//                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
//                    "`name` TEXT)"); // Add other columns for Preset entity

            Log.d("MIGRATION", "Database migrated from version 3 to 4 (Room structure).");
        }
    };

}
