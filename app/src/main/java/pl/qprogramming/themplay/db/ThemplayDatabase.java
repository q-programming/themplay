package pl.qprogramming.themplay.db;

import static pl.qprogramming.themplay.util.Utils.IMAGES_DIR_NAME;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import lombok.val;
import pl.qprogramming.themplay.ThemplayApplication;
import pl.qprogramming.themplay.domain.Playlist;
import pl.qprogramming.themplay.domain.Preset;
import pl.qprogramming.themplay.domain.Song;
import pl.qprogramming.themplay.logger.Logger;
import pl.qprogramming.themplay.repository.PlaylistRepository;
import pl.qprogramming.themplay.repository.PresetRepository;
import pl.qprogramming.themplay.repository.SongRepository;

@Database(entities = {Playlist.class, Song.class, Preset.class}, version = 5)
@TypeConverters({Converters.class})
public abstract class ThemplayDatabase extends RoomDatabase {

    public abstract PlaylistRepository playlistRepository();

    public abstract SongRepository songRepository();

    public abstract PresetRepository presetRepository();

    private static volatile ThemplayDatabase INSTANCE;
    public static final String TAG = ThemplayDatabase.class.getSimpleName();

    public static ThemplayDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (ThemplayDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    ThemplayDatabase.class, "themplay")
                            // Add migrations here
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            final String MIGRATION_TAG = "[Migration 1=>2]";
            database.execSQL("ALTER TABLE " + Song.SONG_TABLE_NAME +
                    " ADD COLUMN " + Song.ARTIST + " TEXT");
            database.execSQL("ALTER TABLE " + Song.SONG_TABLE_NAME +
                    " ADD COLUMN " + Song.TITLE + " TEXT");
            database.execSQL("ALTER TABLE " + Song.SONG_TABLE_NAME +
                    " ADD COLUMN " + Song.PLAYLIST_POSITION + " INTEGER NOT NULL DEFAULT 0");
            Logger.i(MIGRATION_TAG, "Added columns " + Song.ARTIST + ", " + Song.TITLE + ", " + Song.PLAYLIST_POSITION + " to " + Song.SONG_TABLE_NAME + " table.");
        }
    };
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        public void migrate(SupportSQLiteDatabase database) {
            final String MIGRATION_TAG = "[Migration 2=>3]";
            database.execSQL("ALTER TABLE " + Playlist.PLAYLIST_TABLE_NAME + " ADD COLUMN " + Playlist.PLAYBACK_ORDER_IDS + " TEXT");
            Logger.i(MIGRATION_TAG, "Added column " + Playlist.PLAYBACK_ORDER_IDS + " to playlists table.");
        }
    };
    /**
     * Migration from 3 to 4. which takes all base64 old background images and saves them to files
     */
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            final String MIGRATION_TAG = "[Migration 3=>4]";
            val appContext = ThemplayApplication.getAppContext();
            if (appContext == null) {
                Logger.e(MIGRATION_TAG, "Application context is null, cannot perform file operations for migration.");
                throw new IllegalStateException("Application context is null, cannot perform file operations for migration.");
            }
            val cursor = database.query("SELECT "
                    + Playlist.COLUMN_ID + ", "
                    + Playlist.BACKGROUND + ", "
                    + Playlist.PRESET + ", "
                    + Playlist.NAME + " FROM " + Playlist.PLAYLIST_TABLE_NAME
                    + " WHERE " + Playlist.BACKGROUND + " IS NOT NULL AND " + Playlist.BACKGROUND + " != ''");
            if (cursor.moveToFirst()) {
                do {
                    val playlistId = cursor.getLong(cursor.getColumnIndexOrThrow(Playlist.COLUMN_ID));
                    val currentBackgroundImageData = cursor.getString(cursor.getColumnIndexOrThrow(Playlist.BACKGROUND));
                    val presetName = cursor.getString(cursor.getColumnIndexOrThrow(Playlist.PRESET));
                    val playlistName = cursor.getString(cursor.getColumnIndexOrThrow(Playlist.NAME));
                    if (currentBackgroundImageData == null || currentBackgroundImageData.isEmpty()) {
                        continue;
                    }
                    if (currentBackgroundImageData.startsWith(File.separator) || currentBackgroundImageData.startsWith("file:")) {
                        File potentialFile = new File(currentBackgroundImageData);
                        if (potentialFile.exists() && potentialFile.isFile()) {
                            Logger.d(MIGRATION_TAG, "Playlist " + playlistId + " background is already an existing file path: '" + currentBackgroundImageData + "'. Skipping conversion.");
                            continue;
                        } else {
                            Logger.w(MIGRATION_TAG, "Playlist " + playlistId + " background looks like a path but file doesn't exist or is not a file. Will attempt Base64 decode.");
                        }
                    }
                    byte[] imageBytes = null;
                    try {
                        imageBytes = Base64.decode(currentBackgroundImageData, Base64.DEFAULT);
                        Logger.d(MIGRATION_TAG, "Successfully decoded Base64 to byte array for playlist " + playlistId + ". Byte array length: " + (imageBytes != null ? imageBytes.length : "null"));

                    } catch (IllegalArgumentException iae) {
                        Logger.w(MIGRATION_TAG, "Playlist " + playlistId + " background was not valid Base64. Data snippet: " +
                                currentBackgroundImageData.substring(0, Math.min(100, currentBackgroundImageData.length())));
                    }
                    if (imageBytes != null && imageBytes.length > 0) {
                        val presetImagesDir = new File(appContext.getExternalFilesDir(null),
                                IMAGES_DIR_NAME + File.separator + presetName);
                        if (!presetImagesDir.exists()) {
                            if (!presetImagesDir.mkdirs()) {
                                Logger.e(MIGRATION_TAG, "Failed to create directory for playlist " + playlistId + ": " + presetImagesDir.getAbsolutePath());
                                continue;
                            }
                        }
                        val sanitizedPlaylistName = playlistName.replaceAll("[^a-zA-Z0-9.-]", "_");
                        val targetFile = new File(presetImagesDir, sanitizedPlaylistName + ".jpg");
                        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                            fos.write(imageBytes);
                            val values = new ContentValues();
                            values.put(Playlist.BACKGROUND, targetFile.getAbsolutePath());
                            database.update(Playlist.PLAYLIST_TABLE_NAME, SQLiteDatabase.CONFLICT_REPLACE, values, Playlist.COLUMN_ID + " = ?", new String[]{String.valueOf(playlistId)});
                            Logger.d(MIGRATION_TAG, "Migrated background for playlist " + playlistId + " by saving raw bytes to: " + targetFile.getAbsolutePath() + " (Size: " + imageBytes.length + " bytes)");
                        } catch (IOException e) {
                            Logger.e(MIGRATION_TAG, "Failed to save raw image bytes for playlist " + playlistId + " to " + targetFile.getAbsolutePath(), e);
                        }
                    }
                } while (cursor.moveToNext());
            }
            cursor.close();
            Logger.i(MIGRATION_TAG, "Finished MIGRATION_3_4 for playlist backgrounds (direct byte save).");
        }
    };
    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        public void migrate(SupportSQLiteDatabase database) {
            final String MIGRATION_TAG = "[Migration 4=>5]";
            database.execSQL("ALTER TABLE " + Playlist.PLAYLIST_TABLE_NAME + " ADD COLUMN " + Playlist.CURRENT_SONG_TITLE + " TEXT");
            Logger.i(MIGRATION_TAG, "Added column " + Playlist.CURRENT_SONG_TITLE + " to playlists table.");
        }
    };
};