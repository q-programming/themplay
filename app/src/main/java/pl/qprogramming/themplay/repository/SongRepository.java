package pl.qprogramming.themplay.repository;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import io.reactivex.Completable;
import io.reactivex.Single;
import pl.qprogramming.themplay.domain.Song;

@Dao
public interface SongRepository {

    // --- Basic Song CRUD ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Single<Long> create(Song song);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Single<List<Long>> createAll(List<Song> songs);

    @Update
    Single<Integer> update(Song song);

    @Update
    Completable updateAll(List<Song> songs);

    @Delete
    Single<Integer> delete(Song song);

    @Query("DELETE FROM " + Song.SONG_TABLE_NAME + " WHERE id IN (:songIds)")
    Completable deleteSongsByIds(List<Long> songIds);

    @Query("SELECT * FROM " + Song.SONG_TABLE_NAME + " WHERE " + Song.COLUMN_PLAYLIST_OWNER_ID + " = :playlistId ORDER BY " + Song.PLAYLIST_POSITION)
    Single<List<Song>> getSongsForPlaylist(long playlistId);

    @Query("SELECT COUNT(*) FROM " + Song.SONG_TABLE_NAME + " WHERE " + Song.COLUMN_PLAYLIST_OWNER_ID + " = :playlistId")
    Single<Integer> getSongCountForSpecificPlaylist(long playlistId);

    @Query("DELETE FROM " + Song.SONG_TABLE_NAME + " WHERE " + Song.COLUMN_PLAYLIST_OWNER_ID + " = :playlistId")
    Completable deleteAllSongsFromPlaylist(long playlistId);

}