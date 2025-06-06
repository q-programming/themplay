package pl.qprogramming.themplay.repository;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import pl.qprogramming.themplay.domain.Playlist;

@Dao
public interface PlaylistRepository {
    // --- Basic Playlist CRUD ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Single<Long> create(Playlist playlist);

    @Update
    Completable update(Playlist playlist);

    @Update
    Completable updateAll(List<Playlist> playlist);

    @Delete
    Completable delete(Playlist playlist);

    @Query("DELETE FROM " + Playlist.PLAYLIST_TABLE_NAME + " WHERE " + Playlist.PRESET + " = :presetName")
    Completable deleteAllByPresetName(String presetName);

    @Query("SELECT * FROM " + Playlist.PLAYLIST_TABLE_NAME + " WHERE id = :id")
    Maybe<Playlist> findOneById(long id); // Gets only the Playlist object

    @Query("SELECT * FROM " + Playlist.PLAYLIST_TABLE_NAME + " WHERE "+ Playlist.ACTIVE+ " = true")
    Maybe<List<Playlist>> findAllByActive();

    @Query("SELECT * FROM " + Playlist.PLAYLIST_TABLE_NAME + " WHERE preset LIKE :presetName ORDER BY " + Playlist.POSITION + " ASC")
    Single<List<Playlist>> findAllByPreset(String presetName);

    @Query("SELECT * FROM " + Playlist.PLAYLIST_TABLE_NAME + " WHERE preset LIKE :presetName AND active = 1 LIMIT 1")
    Maybe<Playlist> findOneActiveByPreset(String presetName);

    @Query("SELECT COUNT(*) FROM " + Playlist.PLAYLIST_TABLE_NAME + " WHERE " + Playlist.PRESET + " = :presetName AND " + Playlist.NAME + " = :name")
    Single<Integer> countByPresetNameAndName(String presetName, String name);
    @Query("SELECT * FROM " + Playlist.PLAYLIST_TABLE_NAME + " WHERE " + Playlist.PRESET + " = :presetName AND " + Playlist.NAME + " = :name")
    Maybe<Playlist> findByPresetNameAndName(String presetName, String name);

    @Query("SELECT COUNT(*) FROM " + Playlist.PLAYLIST_TABLE_NAME + " WHERE " + Playlist.PRESET + " = :presetName")
    Single<Integer> countAllByPreset(String presetName);

    // Method to update song count (could be called from service after adding/removing a song)
    @Query("UPDATE " + Playlist.PLAYLIST_TABLE_NAME + " SET " + Playlist.SONG_COUNT + " = :newCount WHERE " + Playlist.COLUMN_ID + " = :playlistId")
    Completable updateSongCountForPlaylist(long playlistId, int newCount);
}
