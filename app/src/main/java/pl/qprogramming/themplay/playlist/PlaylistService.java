package pl.qprogramming.themplay.playlist;

import static androidx.preference.PreferenceManager.getDefaultSharedPreferences;
import static pl.qprogramming.themplay.settings.Property.COPY_PLAYLIST;
import static pl.qprogramming.themplay.util.Utils.ARGS;
import static pl.qprogramming.themplay.util.Utils.PLAYLIST;
import static pl.qprogramming.themplay.util.Utils.POSITION;
import static pl.qprogramming.themplay.util.Utils.createPlaylist;
import static pl.qprogramming.themplay.util.Utils.isEmpty;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Action;
import io.reactivex.schedulers.Schedulers;
import lombok.val;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.db.ThemplayDatabase;
import pl.qprogramming.themplay.domain.Playlist;
import pl.qprogramming.themplay.domain.Preset;
import pl.qprogramming.themplay.domain.Song;
import pl.qprogramming.themplay.playlist.exceptions.PlaylistNotFoundException;
import pl.qprogramming.themplay.preset.exceptions.PresetAlreadyExistsException;
import pl.qprogramming.themplay.repository.PlaylistRepository;
import pl.qprogramming.themplay.repository.PresetRepository;
import pl.qprogramming.themplay.repository.SongRepository;
import pl.qprogramming.themplay.settings.Property;
import pl.qprogramming.themplay.util.RxSchedulers;

public class PlaylistService extends Service {
    private final CompositeDisposable disposables = new CompositeDisposable();
    private static final String TAG = PlaylistService.class.getSimpleName();
    private PlaylistRepository playlistRepository;
    private SongRepository songRepository;
    private PresetRepository presetRepository;
    private final IBinder mBinder = new LocalBinder();

    @Override
    public void onCreate() {
        super.onCreate();
        val database = ThemplayDatabase.getDatabase(getApplicationContext());
        playlistRepository = database.playlistRepository();
        songRepository = database.songRepository();
        presetRepository = database.presetRepository();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Binding service to " + intent + "this:" + this);
        ensureOnlyOnePlaylistIsActive();
        return mBinder;
    }

    /**
     * Fire forget operation that will ensure only one playlists is active
     */
    private void ensureOnlyOnePlaylistIsActive() {
        disposables.add(playlistRepository
                .findAllByActive()
                .subscribeOn(Schedulers.io())
                .flatMapCompletable(activePlaylists -> {
                    if (activePlaylists.size() > 1) {
                        Log.w(TAG, "Found " + activePlaylists.size() + " active playlists. Deactivating extras.");
                        Playlist playlistToKeepActive = activePlaylists.get(0);
                        List<Playlist> playlistsToDeactivate = new ArrayList<>(activePlaylists);
                        playlistsToDeactivate.remove(playlistToKeepActive);
                        for (Playlist playlist : playlistsToDeactivate) {
                            playlist.setActive(false);
                        }
                        return playlistRepository.updateAll(playlistsToDeactivate);
                    }
                    return Completable.complete();
                }).observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> Log.i(TAG, "Active playlist cleanup check completed successfully."),
                        throwable -> Log.e(TAG, "Error during active playlist cleanup check.", throwable)
                ));
    }

    /**
     * Returns all playlists for currently selected preset
     *
     * @return List of all Playlists
     */
    public Single<List<Playlist>> getAllByPresetName() {
        val sp = getDefaultSharedPreferences(this);
        val currentPresetName = sp.getString(Property.CURRENT_PRESET, null);
        return getAllByPresetName(currentPresetName)
                .compose(RxSchedulers.singleOnMain());
    }

    /**
     * Returns all playlists.
     * Upon completion , callback is called with list of playlists
     * Upon error , callback is called with error
     *
     * @param onPlaylistsReceived callback when playlists are received
     * @param onError             callback when error occurs
     */
    public void getAllByPresetName(Consumer<List<Playlist>> onPlaylistsReceived, Consumer<Throwable> onError) {
        disposables.add(getAllByPresetName().subscribe(
                onPlaylistsReceived::accept,
                onError::accept));
    }

    /**
     * Returns all playlists for currently selected preset
     *
     * @param presetName Name of preset
     */
    public Single<List<Playlist>> getAllByPresetName(String presetName) {
        return playlistRepository.findAllByPreset(presetName);
    }

    /**
     * Returns all playlists for currently selected preset
     * Loads all songs for each of them
     * Upon completion , callback is called with list of playlists
     * Upon error , callback is called with error
     *
     * @param presetName          Name of preset
     * @param onPlaylistsReceived callback when playlists are received
     * @param onError             callback when error occurs
     */
    public void getAllByPresetName(String presetName, Consumer<List<Playlist>> onPlaylistsReceived, Consumer<Throwable> onError) {
        disposables.add(getAllByPresetName(presetName)
                .flatMapObservable(Observable::fromIterable)
                .flatMap(playlists -> loadSongs(playlists)
                        .toObservable()
                        .subscribeOn(Schedulers.io()))
                .toList()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        onPlaylistsReceived::accept,
                        onError::accept));
    }

    /**
     * Find playlist by ID . If it's present , load all songs from that playlist
     *
     * @param id identifier of playlist
     * @return Optional of Playlist with potentially loaded songs
     */
    public Maybe<Playlist> findById(long id) {
        return playlistRepository.findOneById(id);
    }

    /**
     * Find playlist by ID . If it's present , load all songs from that playlist
     * Runs on Schedulers.io() and main thread
     *
     * @param id                 identifier of playlist
     * @param onPlaylistReceived callback when playlist is found
     * @param onError            callback when error occurs
     */
    public void findById(long id, Consumer<Playlist> onPlaylistReceived, Consumer<Throwable> onError) {
        disposables.add(
                findById(id)
                        .compose(RxSchedulers.maybeOnMain())
                        .subscribe(
                                onPlaylistReceived::accept,
                                onError::accept,
                                () -> Log.w(TAG, "Playlist with ID " + id + " not found (completed without item).")

                        )
        );
    }

    /**
     * Find active playlist If it's present , load all songs from that playlist
     *
     * @return Optional of active Playlist with potentially loaded songs
     */
    public Maybe<Playlist> findActive() {
        val sp = getDefaultSharedPreferences(this);
        val currentPresetName = sp.getString(Property.CURRENT_PRESET, null);
        return playlistRepository.findOneActiveByPreset(currentPresetName);
    }

    /**
     * Loads all songs belonging to playlist
     *
     * @param playlist playlist to have songs loaded
     * @return Single of playlist with loaded songs
     */
    public Single<Playlist> loadSongs(Playlist playlist) {
        return songRepository.getSongsForPlaylist(playlist.getId())
                .map(songs -> {
                    Log.d(TAG, "Fetched " + songs.size() + " songs for playlist: " + playlist.getName());
                    playlist.setSongs(songs);
                    return playlist;
                });
    }

    /**
     * Loads all songs belonging to playlist and runs on main thread
     *
     * @param playlist      playlist to have songs loaded
     * @param onSongsLoaded callback when songs are loaded
     */
    public void loadSongs(Playlist playlist, Consumer<Playlist> onSongsLoaded) {
        disposables.add(
                loadSongs(playlist)
                        .compose(RxSchedulers.singleOnMain())
                        .subscribe(onSongsLoaded::accept, throwable -> Log.e(TAG, "Error loading songs for playlist: " + playlist.getName(), throwable)));
    }

    /**
     * Loads all songs belonging to playlist and runs on main thread
     *
     * @param playlist      playlist to have songs loaded
     * @param onSongsLoaded callback when songs are loaded
     * @param onError       callback when error occurs
     */
    public void loadSongs(Playlist playlist, Consumer<Playlist> onSongsLoaded, Consumer<Throwable> onError) {
        disposables.add(
                loadSongs(playlist)
                        .compose(RxSchedulers.singleOnMain())
                        .subscribe(onSongsLoaded::accept, onError::accept));
    }

    /**
     * Save playlist to database
     * Fire forget mode
     *
     * @param playlist playlist to be saved
     */
    public void save(Playlist playlist) {
        disposables.add(
                playlistRepository.update(playlist)
                        .subscribeOn(Schedulers.io())
                        .subscribe(
                                () -> Log.d(TAG, "Playlist updated successfully: " + playlist.getName()),
                                throwable -> Log.e(TAG, "Error updating playlist: " + playlist.getName(), throwable)));
    }

    /**
     * Save playlist to database
     *
     * @param playlist        playlist to be saved
     * @param onPlaylistSaved callback when playlist is saved
     */
    public void save(Playlist playlist, Consumer<Playlist> onPlaylistSaved) {
        disposables.add(
                playlistRepository.update(playlist)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(() -> {
                            Log.d(TAG, "Playlist updated successfully: " + playlist.getName());
                            onPlaylistSaved.accept(playlist);
                        }, throwable -> Log.e(TAG, "Error updating playlist: " + playlist.getName(), throwable)));
    }

    /**
     * Creates new playlist and saves it to database
     *
     * @param playlist playlist to be created
     */
    public void addPlaylist(Playlist playlist, Consumer<Playlist> onPlaylistCreated, Consumer<Throwable> onError) {
        Log.d(TAG, "Adding new playlist" + playlist);
        val createTask = playlistRepository.countByPresetNameAndName(playlist.getPreset(), playlist.getName())
                .subscribeOn(Schedulers.io())
                .flatMap(exists -> {
                    if (exists > 0) {
                        return Single.error(new PresetAlreadyExistsException("Playlist with name " + playlist.getName() + " already exists for preset " + playlist.getPreset()));
                    } else {
                        return playlistRepository.countAllByPreset(playlist.getPreset());
                    }
                })
                .flatMap(count -> {
                    playlist.setPosition(count);
                    return playlistRepository.create(playlist);
                })
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSuccess(newPlaylistId -> {
                    Log.d(TAG, "Playlist created successfully with ID: " + newPlaylistId + ". Name: " + playlist.getName());
                    playlist.setId(newPlaylistId);
                    onPlaylistCreated.accept(playlist);
                    populateAndSend(EventType.PLAYLIST_NOTIFICATION_ADD, playlist);
                })
                .doOnError(onError::accept)
                .subscribe(
                        newPlaylistId -> Log.i(TAG, "Successfully added playlist ID: " + newPlaylistId + ", Name: " + playlist.getName()),
                        throwable -> {
                            if (!(throwable instanceof PresetAlreadyExistsException)) {
                                Log.e(TAG, "Error adding playlist: " + playlist.getName(), throwable);
                            }
                        }
                );
        disposables.add(createTask);
    }

    /**
     * Adds new song into playlist
     *
     * @param playlist      playlists which will have new song added
     * @param songsToInsert list of song to be added
     */
    public void addSongToPlaylist(Playlist playlist, List<Song> songsToInsert,
                                  Consumer<Playlist> onPlaylistUpdated) {
        val playlistId = playlist.getId();
        for (int i = 0; i < songsToInsert.size(); i++) {
            songsToInsert.get(i).setPlaylistOwnerId(playlistId);
        }
        disposables.add(
                songRepository.createAll(songsToInsert)
                        .subscribeOn(Schedulers.io())
                        .flatMap(insertedSongIds -> {
                            Log.d(TAG, "Songs inserted. Received " + insertedSongIds.size() + " IDs.");
                            if (insertedSongIds.size() != songsToInsert.size()) {
                                Log.w(TAG, "Mismatch between songs to insert and returned IDs count.");
                            }
                            for (int i = 0; i < songsToInsert.size() && i < insertedSongIds.size(); i++) {
                                songsToInsert.get(i).setId(insertedSongIds.get(i));
                            }
                            return songRepository.getSongCountForSpecificPlaylist(playlistId)
                                    .subscribeOn(Schedulers.io())
                                    .flatMap(count -> {
                                        // Update the in-memory playlist object
                                        playlist.setSongCount(count);
                                        if (playlist.getSongs() == null) {
                                            playlist.setSongs(new ArrayList<>());
                                        }
                                        playlist.getSongs().addAll(songsToInsert);
                                        playlist.setUpdatedAt(new Date());
                                        return playlistRepository
                                                .updateSongCountForPlaylist(playlistId, count)
                                                .subscribeOn(Schedulers.io())
                                                .toSingleDefault(playlist);
                                    });
                        })
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                updatedPl -> {
                                    Log.d(TAG, "Playlist update successful in service. Passing to UI callback.");
                                    onPlaylistUpdated.accept(playlist);
                                    Toast.makeText(this, R.string.playlist_added_new_songs, Toast.LENGTH_SHORT).show();
                                },
                                throwable -> Log.e(TAG, "Error in addSongToPlaylist RxJava chain", throwable)
                        )
        );
    }

    /**
     * Removes songs from playlist . First updates playlist and then removes, then goes through ids of songs to be deleted
     * Those songs will be deleted from database, afterwards playlist will be updated with count of songs
     * Upon completing all those operation , notify will be sent with updated playlist
     *
     * @param playlistId        playlist which should be updated
     * @param songsToRemove     list of songs to be deleted
     * @param onSuccessCallback callback when operation is successful
     * @param onErrorCallback   callback when error occurs
     */
    public void removeSongsFromPlaylist(
            final long playlistId,
            final List<Song> songsToRemove,
            final Consumer<Playlist> onSuccessCallback,
            final Consumer<Throwable> onErrorCallback) {
        removeSongsFromPlaylist(playlistId, songsToRemove, onSuccessCallback, onErrorCallback, () -> {/* No-op */});
    }

    /**
     * Removes songs from playlist . First updates playlist and then removes,
     * then goes through ids of songs to be deleted and removes them
     *
     * @param playlistId         Id of playlist
     * @param songsToRemove      List of songs to be removed
     * @param onSuccessCallback  Callback when operation is successful
     * @param onErrorCallback    Callback when error occurs
     * @param onCompleteCallback Callback when operation is completed
     */
    public void removeSongsFromPlaylist(
            final long playlistId,
            final List<Song> songsToRemove,
            final Consumer<Playlist> onSuccessCallback,
            final Consumer<Throwable> onErrorCallback,
            Action onCompleteCallback) {
        Log.d(TAG, "Removing " + songsToRemove.size() + " songs from playlist ID: " + playlistId);
        Single<Playlist> updateOperation = Single.defer(() -> playlistRepository.findOneById(playlistId)
                .switchIfEmpty(Single.error(new PlaylistNotFoundException("Playlist with ID " + playlistId + " not found.")))
                .flatMap(playlistFromDb ->
                        songRepository.getSongsForPlaylist(playlistFromDb.getId())
                                .map(songsFromDb -> {
                                    playlistFromDb.setSongs(songsFromDb);
                                    Log.d(TAG, "Fetched " + songsFromDb.size() + " songs for " + playlistFromDb.getName());
                                    return playlistFromDb;
                                })
                )
                .flatMap(currentPlaylistState -> {
                    List<Long> idsOfSongsMarkedForRemoval = songsToRemove.stream()
                            .map(Song::getId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
                    if (idsOfSongsMarkedForRemoval.isEmpty()) {
                        Log.d(TAG, "No valid song IDs to remove for " + currentPlaylistState.getName());
                        return Single.just(currentPlaylistState);
                    }
                    List<Song> songsRemainingInPlaylist = new ArrayList<>();
                    if (currentPlaylistState.getSongs() != null) {
                        songsRemainingInPlaylist = currentPlaylistState.getSongs().stream()
                                .filter(song -> !idsOfSongsMarkedForRemoval.contains(song.getId()))
                                .collect(Collectors.toList());
                    }
                    currentPlaylistState.setSongs(songsRemainingInPlaylist);
                    currentPlaylistState.setSongCount(songsRemainingInPlaylist.size());
                    currentPlaylistState.setUpdatedAt(new Date());
                    Long currentSongId = currentPlaylistState.getCurrentSongId();
                    if (currentSongId != null && idsOfSongsMarkedForRemoval.contains(currentSongId)) {
                        currentPlaylistState.setCurrentSongId(null);
                    }
                    Completable deleteDbSongsCompletable = songRepository.deleteSongsByIds(idsOfSongsMarkedForRemoval);
                    Completable updateDbPlaylistCompletable = playlistRepository.update(currentPlaylistState);
                    return deleteDbSongsCompletable
                            .andThen(updateDbPlaylistCompletable)
                            .andThen(Single.just(currentPlaylistState));
                }));
        disposables.add(
                updateOperation
                        .compose(RxSchedulers.singleOnMain())
                        .doOnSuccess(updatedPlaylist -> {
                            Log.i(TAG, "Successfully removed songs for " + updatedPlaylist.getName());
                            populateAndSend(EventType.PLAYLIST_NOTIFICATION_DELETE_SONGS, updatedPlaylist);
                        })
                        .doFinally(onCompleteCallback)
                        .subscribe(
                                onSuccessCallback::accept,
                                onErrorCallback::accept
                        )
        );
    }

    /**
     * Removes all playlists for selected preset, and then removes preset itself
     *
     * @param presetName name of preset for which playlists should be removed
     */
    public void removePreset(String presetName) {
        disposables.add(playlistRepository.deleteAllByPresetName(presetName)
                .andThen(presetRepository.deleteByName(presetName))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> Log.i(TAG, "Successfully removed all playlists and preset: " + presetName),
                        throwable -> Log.e(TAG, "Error removing all playlists and preset: " + presetName, throwable))
        );
    }

    /**
     * Removes playlist from database , reindex remaining playlists positions and notifies about removal
     *
     * @param removedPlaylist playlist to be removed
     */
    public void removePlaylist(Playlist removedPlaylist) {
        Log.d(TAG, "Removing playlist: " + removedPlaylist.getName());
        val playlistId = removedPlaylist.getId();
        val removeTask = findById(playlistId)
                .switchIfEmpty(Single.error(new PlaylistNotFoundException("Playlist with ID " + playlistId + " not found.")))
                .flatMap(playlist -> {
                    val presetName = playlist.getPreset();
                    return playlistRepository.delete(playlist)
                            .andThen(Single.just(presetName));
                })
                .flatMap(this::getAllByPresetName)
                .flatMapCompletable(playlists -> {
                    for (int i = 0; i < playlists.size(); i++) {
                        val playlist = playlists.get(i);
                        playlist.setPosition(i);
                    }
                    return playlistRepository.updateAll(playlists);
                })
                .doOnComplete(() -> populateAndSend(EventType.PLAYLIST_NOTIFICATION_DELETE, removedPlaylist))
                .subscribeOn(Schedulers.io())
                .subscribe(() -> Log.i(TAG, "Successfully removed playlist ID: " + playlistId + ", Name: " + removedPlaylist.getName()));
        disposables.add(removeTask);
    }

    /**
     * Paste playlist into new playlist , by making a direct clone of it, cloning all songs , and saving it to database
     * While saving position will be also updated ( to be last )
     *
     * @param copyId id of playlist to be pasted
     * @throws PlaylistNotFoundException  if playlist with given ID does not exist
     * @throws CloneNotSupportedException if clone is not supported
     */
    public void paste(long copyId, Consumer<Playlist> onPlaylistPasted) throws PlaylistNotFoundException, CloneNotSupportedException {
        val sp = getDefaultSharedPreferences(this);
        val currentPresetName = sp.getString(Property.CURRENT_PRESET, null);
        if (currentPresetName == null) {
            throw new IllegalStateException("Current preset name is not set.");
        }
        val pasteTask = findById(copyId)
                .switchIfEmpty(Single.error(new PlaylistNotFoundException("Playlist with ID " + copyId + " not found to copy.")))
                .flatMap(originalPlaylist -> {
                    val playlistCopy = originalPlaylist.clone();
                    playlistCopy.setPreset(currentPresetName);
                    String baseName = playlistCopy.getName(); // Get the original name to use as base
                    return findAvailableNameRecursive(baseName, currentPresetName, 0)
                            .map(uniqueName -> {
                                playlistCopy.setName(uniqueName); // Set the found unique name
                                Log.d(TAG, "Found unique name for pasted playlist: " + uniqueName);
                                return playlistCopy;
                            });

                })
                .flatMap(playlistWithUniqueName ->
                        playlistRepository.countAllByPreset(currentPresetName)
                                .map(count -> {
                                    playlistWithUniqueName.setPosition(count);
                                    return playlistWithUniqueName;
                                }))
                .flatMap(preparedPlaylist -> playlistRepository
                        .create(preparedPlaylist)
                        .map(newPlaylistId -> {
                            preparedPlaylist.setId(newPlaylistId);
                            Log.d(TAG, "Cloned playlist saved with new ID: " + newPlaylistId + ", Name: " + preparedPlaylist.getName());
                            return preparedPlaylist;
                        }))
                .flatMap(savedClone ->
                        cloneSongs(copyId, savedClone)
                )
                .flatMap(playlistWithSongs -> playlistRepository
                        .update(playlistWithSongs)
                        .andThen(Single.just(playlistWithSongs)))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        playlist -> {
                            populateAndSend(EventType.PLAYLIST_NOTIFICATION_ADD, playlist);
                            val spEdit = sp.edit();
                            spEdit.putLong(COPY_PLAYLIST, -1L);
                            spEdit.apply();
                            Log.i(TAG, "Paste operation for playlist ID " + copyId + " completed successfully.");
                            onPlaylistPasted.accept(playlist);
                        },
                        throwable -> Log.e(TAG, "Error during paste operation for playlist ID " + copyId, throwable)
                );
        //fire paste task
        disposables.add(pasteTask);
    }

    /**
     * Finds an available unique name for a playlist within a preset.
     * If baseName is "My Playlist", it will try:
     * "My Playlist"
     * "My Playlist (1)"
     * "My Playlist (2)"
     * ...
     * until an unused name is found.
     *
     * @param baseName   The original name of the playlist.
     * @param presetName The name of the preset.
     * @param attempt    The current attempt number (starts at 0 for no suffix).
     * @return Single emitting the first available unique name.
     */
    private Single<String> findAvailableNameRecursive(String baseName, String presetName, int attempt) {
        String currentNameAttempt;
        if (attempt == 0) {
            currentNameAttempt = baseName;
        } else {
            currentNameAttempt = baseName + "_" + attempt;
        }

        return playlistRepository.countByPresetNameAndName(presetName, currentNameAttempt)
                .flatMap(count -> {
                    if (count > 0) {
                        // Name exists, try the next one
                        return findAvailableNameRecursive(baseName, presetName, attempt + 1);
                    } else {
                        // Name is available
                        return Single.just(currentNameAttempt);
                    }
                });
    }

    /**
     * Clones all songs belonging to original playlist with id , by first grabbing them from database,
     * making clone, clear original playlist owner id , and saving it to database
     *
     * @param originalPlaylistId id of cloned playlist
     * @param savedClone         cloned playlist
     * @return Single of cloned playlist
     */
    private Single<Playlist> cloneSongs(long originalPlaylistId, Playlist savedClone) {
        Log.d(TAG, "Cloning songs from original playlist ID: " + originalPlaylistId + " into new playlist: " + savedClone.getName());
        return songRepository.getSongsForPlaylist(originalPlaylistId) // Assuming this returns Single<List<Song>>
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.computation())
                .flatMap(originalSongs -> {
                    if (originalSongs.isEmpty()) {
                        Log.d(TAG, "No songs to clone from playlist ID: " + originalPlaylistId);
                        savedClone.setSongs(new ArrayList<>());
                        savedClone.setSongCount(0);
                        return Single.just(savedClone);
                    }

                    List<Song> clonedSongObjects = new ArrayList<>();
                    for (Song originalSong : originalSongs) {
                        try {
                            Song songCopy = originalSong.clone();
                            songCopy.setId(null);
                            songCopy.setPlaylistOwnerId(savedClone.getId());
                            clonedSongObjects.add(songCopy);
                        } catch (CloneNotSupportedException e) {
                            Log.e(TAG, "Failed to clone song: " + originalSong.getFilename(), e);
                            return Single.error(new RuntimeException("Clone operation failed for song: " + originalSong.getFilename(), e));
                        }
                    }
                    Log.d(TAG, "Successfully prepared " + clonedSongObjects.size() + " songs for cloning.");

                    // Now, insert these cloned songs into the database and get their new IDs
                    return songRepository.createAll(clonedSongObjects) // Use the method that returns IDs
                            .subscribeOn(Schedulers.io())
                            .observeOn(Schedulers.computation())
                            .map(insertedSongIds -> {
                                if (insertedSongIds.size() != clonedSongObjects.size()) {
                                    Log.w(TAG, "ID count mismatch during song cloning. Expected: " + clonedSongObjects.size() + ", Got: " + insertedSongIds.size());
                                }
                                Log.d(TAG, "Received " + insertedSongIds.size() + " new IDs for cloned songs.");
                                for (int i = 0; i < clonedSongObjects.size() && i < insertedSongIds.size(); i++) {
                                    clonedSongObjects.get(i).setId(insertedSongIds.get(i));
                                }
                                Log.d(TAG, "Successfully saved " + clonedSongObjects.size() + " cloned songs for " + savedClone.getName());
                                savedClone.setSongs(clonedSongObjects);
                                savedClone.setSongCount(clonedSongObjects.size());
                                return savedClone;
                            });
                })
                .observeOn(AndroidSchedulers.mainThread());
    }

    /**
     * Set Playlist as active
     * Attempt to find active playlist and deactivate it first,
     * then find playlist by ID , load all songs for this playlist , set it as active
     * And broadcast event to play
     *
     * @param playlist playlist to be made active
     */
    public void setActive(Playlist playlist) {
        val playlistToActivateId = playlist.getId();
        Log.d(TAG, "Attempting to set playlist ID as active: " + playlistToActivateId);
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(EventType.OPERATION_STARTED.getCode()));
        val activeTask =
                findActive()
                        .flatMapCompletable(activePlaylist -> {
                                    if (!activePlaylist.getId().equals(playlistToActivateId)) {
                                        activePlaylist.setActive(false);
                                        return playlistRepository.update(activePlaylist);
                                    }
                                    return Completable.complete();
                                }
                        ).andThen(playlistRepository.findOneById(playlistToActivateId))
                        .switchIfEmpty(Single.error(new PlaylistNotFoundException("Playlist with ID " + playlistToActivateId + " not found.")))
                        .flatMap(playlistToActivate -> {
                            Log.d(TAG, "Found playlist to activate: " + playlistToActivate.getName());
                            return loadSongs(playlistToActivate);
                        })
                        .flatMapCompletable(this::buildPlaylistMakeActiveAndNotify)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(() -> {
                            Log.d(TAG, "Playlist set as active successfully.");
                            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(EventType.OPERATION_FINISHED.getCode()));
                        });
        disposables.add(activeTask);
    }

    /**
     * Creates playlist out of all available songs
     * Search for current song within. If none present, set first from list
     *
     * @param playlist playlist to be made active and played
     * @return Completable that completes when playlist is made active
     */
    private Completable buildPlaylistMakeActiveAndNotify(Playlist playlist) {
        val sp = getDefaultSharedPreferences(this);
        val shuffle = sp.getBoolean(Property.SHUFFLE_MODE, true);
        createPlaylist(playlist, shuffle);
        var currentSongId = playlist.getCurrentSongId();

        if (currentSongId == null && isEmpty(playlist.getPlaylist())) {
            populateAndSend(EventType.PLAYLIST_NOTIFICATION_PLAY_NO_SONGS, playlist);
            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(EventType.OPERATION_FINISHED.getCode()));
            Log.d(TAG, "Playlist has no songs.");
            return Completable.complete();
        } else if (currentSongId == null && !isEmpty(playlist.getPlaylist())) {
            val currentSong = playlist.getPlaylist().get(0);
            playlist.setCurrentSongId(currentSong.getId());
            playlist.setCurrentSong(currentSong);
            Log.d(TAG, "Setting current song to first in playlist");
        } else if (!isEmpty(playlist.getPlaylist())) {
            playlist.getPlaylist()
                    .stream()
                    .filter(song -> song.getId().equals(currentSongId))
                    .findFirst()
                    .ifPresentOrElse(
                            playlist::setCurrentSong,
                            () -> {
                                Log.d(TAG, "Song with ID " + currentSongId + " not found in playlist " + playlist.getName() + ". Picking first");
                                playlist.setCurrentSong(playlist.getPlaylist().get(0));
                            });

        }
        playlist.setActive(true);
        return playlistRepository.update(playlist).doOnComplete(() -> {
            Log.d(TAG, "Playlist set as active sending event to play");
            populateAndSend(EventType.PLAYLIST_NOTIFICATION_ACTIVE, playlist);
        });
    }

    public void resetActiveFromPreset() {
        disposables.add(
                findActive()
                        .observeOn(Schedulers.io())
                        .flatMapCompletable(playlist -> {
                            playlist.setActive(false);
                            Log.d(TAG, "Deactivating playlist (on IO thread): " + playlist.getName());
                            return playlistRepository.update(playlist);
                        })
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(() -> Log.d(TAG, "resetActiveFromPreset operation completed successfully."),
                                throwable -> Log.e(TAG, "resetActiveFromPreset operation failed.", throwable)
                        ));
    }


    /**
     * Save all playlists in background
     *
     * @param playlists list of playlists to be saved
     */
    public void saveAll(List<Playlist> playlists) {
        disposables.add(
                playlistRepository.updateAll(playlists)
                        .subscribeOn(Schedulers.io())
                        .observeOn(Schedulers.io())
                        .subscribe(() -> Log.d("PlaylistService", "Successfully saved/updated all " + playlists.size() + " playlists in the background."),
                                throwable -> Log.e("PlaylistService", "Error saving/updating all playlists in the background", throwable))
        );
    }

    /**
     * Loads all presets
     *
     * @param onPresetCreated callback when presets are loaded
     * @param onError         callback when error occurs
     */
    public void getAllPresets(Consumer<List<Preset>> onPresetCreated, Consumer<Throwable> onError) {
        disposables.add(presetRepository.findAll()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(onPresetCreated::accept, onError::accept));
    }

    /**
     * Creates new preset. Upon creation , get all and notify
     *
     * @param newPresetName   name of new preset
     * @param onPresetCreated callback when preset is created
     */
    public void addPreset(String newPresetName, Consumer<List<Preset>> onPresetCreated, Consumer<Throwable> onError) {
        final String trimmedName = newPresetName.trim();
        Preset newPreset = Preset.builder()
                .name(trimmedName)
                .build();

        val createAndFetchAllTask = presetRepository.countByName(trimmedName)
                .flatMap(count -> {
                    if (count > 0) {
                        return Single.error(new PresetAlreadyExistsException(
                                "A preset named '" + trimmedName + "' already exists."
                        ));
                    }
                    return presetRepository.create(newPreset)
                            .map(newId -> {
                                newPreset.setId(newId);
                                Log.i(TAG, "Preset created: ID " + newId + ", Name: " + newPreset.getName());
                                return newPreset;
                            });
                })
                .flatMap(createdPreset -> presetRepository.findAll())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError(onError::accept)
                .subscribe(onPresetCreated::accept,
                        throwable -> {
                            if (!(throwable instanceof PresetAlreadyExistsException)) {
                                Log.e(TAG, "Error creating preset: " + trimmedName, throwable);
                            }
                        });
        disposables.add(createAndFetchAllTask);
    }

    /**
     * Updates song in database in fire forget mode
     *
     * @param song song to be updated
     */
    public void updateSong(Song song) {
        disposables.add(songRepository.update(song)
                .subscribeOn(Schedulers.io())
                .subscribe(integer -> Log.d(TAG, "Song updated successfully: " + song.getFilename())));
    }


    public class LocalBinder extends Binder {
        public PlaylistService getService() {
            return PlaylistService.this;
        }
    }

    /**
     * Sends event to all listeners about playlist change
     *
     * @param type     type of event
     * @param playlist playlist which was changed
     */
    private void populateAndSend(EventType type, Playlist playlist) {
        Intent intent = new Intent(type.getCode());
        val args = new Bundle();
        args.putSerializable(POSITION, playlist.getPosition());
        args.putSerializable(PLAYLIST, playlist);
        intent.putExtra(ARGS, args);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        Log.d(TAG, "Playlist notification " + type.getCode() + " sent: " + playlist.getName());
    }
}
