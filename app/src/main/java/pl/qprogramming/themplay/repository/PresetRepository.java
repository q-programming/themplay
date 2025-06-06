package pl.qprogramming.themplay.repository;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import io.reactivex.Completable;
import io.reactivex.Single;
import pl.qprogramming.themplay.domain.Preset;

@Dao
public interface PresetRepository {

    /**
     * Inserts a new Preset into the database.
     * If a preset with the same name already exists (due to the unique index),
     * this operation will fail (throws SQLiteConstraintException).
     *
     * @param preset The preset to insert.
     * @return A Single emitting the new row ID of the inserted preset.
     */
    @Insert()
    Single<Long> create(Preset preset);

    /**
     * Updates an existing Preset in the database.
     * If the name is changed to one that already exists (violating unique index),
     * this operation will fail (throws SQLiteConstraintException).
     *
     * @param preset The preset to update. Must have a valid ID.
     * @return A Completable that signals completion or error.
     */
    @Update()
    Completable update(Preset preset);

    /**
     * Deletes a Preset from the database.
     * Note: This does not automatically handle associated Playlists.
     * That logic should be in a Repository/Service layer.
     *
     * @param preset The preset to delete.
     * @return A Completable that signals completion or error.
     */
    @Delete
    Completable delete(Preset preset);

    /**
     * Deletes a Preset from the database by its ID.
     *
     * @param presetName The name of preset to be deleted
     * @return A Completable that signals completion or error.
     */
    @Query("DELETE FROM " + Preset.PRESET_TABLE_NAME + " WHERE " + Preset.NAME + " = :presetName")
    Completable deleteByName(String presetName);

    @Query("SELECT COUNT(*) FROM " + Preset.PRESET_TABLE_NAME + " WHERE " + Preset.NAME + " = :name")
    Single<Integer> countByName(String name);

    /**
     * Retrieves all Presets from the database, ordered by name ascending.
     *
     * @return A Single emitting a List of all Presets. Emits an empty list if no presets exist.
     */
    @Query("SELECT * FROM " + Preset.PRESET_TABLE_NAME + " ORDER BY " + Preset.NAME + " ASC")
    Single<List<Preset>> findAll();


}
