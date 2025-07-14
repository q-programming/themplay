package pl.qprogramming.themplay.playlist;

import static androidx.preference.PreferenceManager.getDefaultSharedPreferences;
import static pl.qprogramming.themplay.playlist.EventType.PLAYLIST_NOTIFICATION_IS_ACTIVE_PLAYING;
import static pl.qprogramming.themplay.playlist.EventType.PLAYLIST_NOTIFICATION_NEW_ACTIVE;
import static pl.qprogramming.themplay.playlist.EventType.PRESET_ACTIVATED;
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
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.schedulers.Schedulers;
import lombok.val;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.db.ThemplayDatabase;
import pl.qprogramming.themplay.domain.Playlist;
import pl.qprogramming.themplay.domain.Preset;
import pl.qprogramming.themplay.domain.Song;
import pl.qprogramming.themplay.logger.Logger;
import pl.qprogramming.themplay.playlist.exceptions.OperationSkippedException;
import pl.qprogramming.themplay.playlist.exceptions.PlaylistNameExistsException;
import pl.qprogramming.themplay.playlist.exceptions.PlaylistNotFoundException;
import pl.qprogramming.themplay.preset.exceptions.PresetAlreadyExistsException;
import pl.qprogramming.themplay.preset.exceptions.PresetNotFoundException;
import pl.qprogramming.themplay.repository.PlaylistRepository;
import pl.qprogramming.themplay.repository.PresetRepository;
import pl.qprogramming.themplay.repository.SongRepository;
import pl.qprogramming.themplay.settings.Property;
import pl.qprogramming.themplay.util.RxSchedulers;

/**
 * Service responsible for managing playlists, including their creation, modification,
 * deletion, and playback state. It interacts with {@link PlaylistRepository},
 * {@link SongRepository}, and {@link PresetRepository} to persist and retrieve data.
 * <p>
 * This service operates in the background and uses RxJava for asynchronous operations.
 * It provides methods to:
 * <ul>
 *     <li>Retrieve playlists by preset.</li>
 *     <li>Find specific playlists by ID or active status.</li>
 *     <li>Load songs associated with a playlist.</li>
 *     <li>Save, update, and add new playlists.</li>
 *     <li>Add and remove songs from playlists.</li>
 *     <li>Update song positions within a playlist and its playback order.</li>
 *     <li>Manage presets (creation and deletion, including associated playlists).</li>
 *     <li>Clone (paste) existing playlists.</li>
 *     <li>Set a playlist as active and manage its playback preparation.</li>
 *     <li>Update individual song details.</li>
 * </ul>
 * <p>
 * The service uses {@link LocalBroadcastManager} to notify other components of playlist-related
 * events (e.g., when a playlist is added, deleted, or set as active).
 * It also ensures that only one playlist can be active at any given time for a preset.
 * <p>
 * Client components can bind to this service to directly call its public methods.
 */
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
        Logger.d(TAG, "Binding service to " + intent + "this:" + this);
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
                        Logger.w(TAG, "Found " + activePlaylists.size() + " active playlists. Deactivating extras.");
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
                        () -> Logger.i(TAG, "Active playlist cleanup check completed successfully."),
                        throwable -> Logger.e(TAG, "Error during active playlist cleanup check.", throwable)
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
                                () -> Logger.w(TAG, "Playlist with ID " + id + " not found (completed without item).")

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
                    Logger.d(TAG, "Fetched " + songs.size() + " songs for playlist: " + playlist.getName());
                    playlist.setSongs(songs);
                    if (playlist.getCurrentSongId() != null) {
                        val currentSong = songs.stream().filter(song -> song.getId().equals(playlist.getCurrentSongId())).findFirst().orElse(null);
                        playlist.setCurrentSong(currentSong);
                        playlist.setCurrentSongTitle(currentSong != null ? currentSong.getDisplayName() : null);
                    }
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
                        .subscribe(onSongsLoaded::accept, throwable -> Logger.e(TAG, "Error loading songs for playlist: " + playlist.getName(), throwable)));
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
     * Fire forget mode . Should not be used for full update operation
     *
     * @param playlist playlist to be saved
     */
    public void save(Playlist playlist) {
        disposables.add(
                playlistRepository.update(playlist)
                        .subscribeOn(Schedulers.io())
                        .subscribe(
                                () -> Logger.d(TAG, "Playlist updated successfully: " + playlist.getName()),
                                throwable -> Logger.e(TAG, "Error updating playlist: " + playlist.getName(), throwable)));
    }

    /**
     * Save playlist to database
     *
     * @param playlist        playlist to be saved
     * @param onPlaylistSaved callback when playlist is saved
     * @param onError         callback when error occurs
     */
    public void save(Playlist playlist, Consumer<Playlist> onPlaylistSaved, Consumer<Throwable> onError) {
        if (playlist == null) {
            if (onError != null) {
                onError.accept(new IllegalArgumentException("Playlist to save cannot be null."));
            }
            return;
        }
        val name = playlist.getName().trim();
        val updateTask = playlistRepository.findByPresetNameAndName(playlist.getPreset(), name)
                .subscribeOn(Schedulers.io())
                .flatMapSingleElement(conflictingPlaylist -> {
                    if (playlist.getId() != null && playlist.getId().equals(conflictingPlaylist.getId())) {
                        return playlistRepository.update(playlist).toSingleDefault(playlist);
                    } else {
                        return Single.error(new PlaylistNameExistsException(
                                "Playlist with name '" + playlist.getName() +
                                        "' already exists for preset '" + playlist.getPreset() + "'."));
                    }
                })
                .switchIfEmpty(Single.defer(() ->
                        playlistRepository.update(playlist).toSingleDefault(playlist)
                ))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        savedPlaylist -> {
                            Logger.d(TAG, "Playlist saved/updated successfully: " + savedPlaylist.getName());
                            if (onPlaylistSaved != null) {
                                onPlaylistSaved.accept(savedPlaylist);
                            }
                        },
                        throwable -> {
                            if (throwable instanceof PlaylistNameExistsException) {
                                Logger.d(TAG, "Playlist with name '" + playlist.getName() + "' already exists for preset '" + playlist.getPreset() + "'. Skipping.");
                            } else {
                                Logger.e(TAG, "Error during save operation for playlist: " +
                                        (playlist.getName() != null ? playlist.getName() : "ID " + playlist.getId()), throwable);
                            }
                            onError.accept(throwable);
                        }
                );
        disposables.add(updateTask);
    }

    /**
     * Creates new playlist and saves it to database
     *
     * @param playlist playlist to be created
     */
    public void addPlaylist(Playlist playlist, Consumer<Playlist> onPlaylistCreated, Consumer<Throwable> onError) {
        Logger.d(TAG, "Adding new playlist" + playlist);
        val createTask = playlistRepository.countByPresetNameAndName(playlist.getPreset(), playlist.getName())
                .subscribeOn(Schedulers.io())
                .flatMap(exists -> {
                    if (exists > 0) {
                        return Single.error(new PlaylistNameExistsException("Playlist with name " + playlist.getName() + " already exists for preset " + playlist.getPreset()));
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
                    Logger.d(TAG, "Playlist created successfully with ID: " + newPlaylistId + ". Name: " + playlist.getName());
                    playlist.setId(newPlaylistId);
                    onPlaylistCreated.accept(playlist);
                    populateAndSend(EventType.PLAYLIST_NOTIFICATION_ADD, playlist);
                })
                .doOnError(onError::accept)
                .subscribe(
                        newPlaylistId -> Logger.i(TAG, "Successfully added playlist ID: " + newPlaylistId + ", Name: " + playlist.getName()),
                        throwable -> {
                            if (!(throwable instanceof PresetAlreadyExistsException)) {
                                Logger.e(TAG, "Error adding playlist: " + playlist.getName(), throwable);
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
        val currentItemCount = playlist.getSongCount();
        for (int i = 0; i < songsToInsert.size(); i++) {
            Song song = songsToInsert.get(i);
            song.setPlaylistOwnerId(playlistId);
            song.setPlaylistPosition(currentItemCount + i);
        }
        disposables.add(
                songRepository.createAll(songsToInsert)
                        .subscribeOn(Schedulers.io())
                        .flatMap(insertedSongIds -> {
                            Logger.d(TAG, "Songs inserted. Received " + insertedSongIds.size() + " IDs.");
                            if (insertedSongIds.size() != songsToInsert.size()) {
                                Logger.w(TAG, "Mismatch between songs to insert and returned IDs count.");
                            }
                            for (int i = 0; i < songsToInsert.size() && i < insertedSongIds.size(); i++) {
                                songsToInsert.get(i).setId(insertedSongIds.get(i));
                            }
                            return songRepository.getSongCountForSpecificPlaylist(playlistId)
                                    .subscribeOn(Schedulers.io())
                                    .flatMap(count -> {
                                        // Update the in-memory playlist object, clear any predefined playback order
                                        playlist.setSongCount(count);
                                        playlist.setPlaybackOrderIds(null);
                                        if (playlist.getSongs() == null) {
                                            playlist.setSongs(new ArrayList<>());
                                        }
                                        playlist.getSongs().addAll(songsToInsert);
                                        playlist.setUpdatedAt(new Date());
                                        return playlistRepository
                                                .updateCountAndResetPlaybackOrder(playlistId, count)
                                                .subscribeOn(Schedulers.io())
                                                .toSingleDefault(playlist);
                                    });
                        })
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                updatedPl -> {
                                    Logger.d(TAG, "Playlist update successful in service. Passing to UI callback.");
                                    onPlaylistUpdated.accept(playlist);
                                    Toast.makeText(this, R.string.playlist_added_new_songs, Toast.LENGTH_SHORT).show();
                                },
                                throwable -> Logger.e(TAG, "Error in addSongToPlaylist RxJava chain", throwable)
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
        Logger.d(TAG, "Removing " + songsToRemove.size() + " songs from playlist ID: " + playlistId);
        Single<Playlist> updateOperation = Single.defer(() -> playlistRepository.findOneById(playlistId)
                .switchIfEmpty(Single.error(new PlaylistNotFoundException("Playlist with ID " + playlistId + " not found.")))
                .flatMap(playlistFromDb ->
                        songRepository.getSongsForPlaylist(playlistFromDb.getId())
                                .map(songsFromDb -> {
                                    playlistFromDb.setSongs(songsFromDb);
                                    Logger.d(TAG, "Fetched " + songsFromDb.size() + " songs for " + playlistFromDb.getName());
                                    return playlistFromDb;
                                })
                )
                .flatMap(currentPlaylistState -> {
                    List<Long> idsOfSongsMarkedForRemoval = songsToRemove.stream()
                            .map(Song::getId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
                    if (idsOfSongsMarkedForRemoval.isEmpty()) {
                        Logger.d(TAG, "No valid song IDs to remove for " + currentPlaylistState.getName());
                        return Single.just(currentPlaylistState);
                    }
                    List<Song> songsRemainingInPlaylist = new ArrayList<>();
                    if (currentPlaylistState.getSongs() != null) {
                        val newPosition = new AtomicInteger(0);
                        songsRemainingInPlaylist = currentPlaylistState.getSongs().stream()
                                .filter(song -> !idsOfSongsMarkedForRemoval.contains(song.getId()))
                                .sorted(Comparator.comparingInt(Song::getPlaylistPosition))
                                .peek(song -> song.setPlaylistPosition(newPosition.getAndIncrement()))
                                .collect(Collectors.toList());
                    }
                    currentPlaylistState.setSongs(songsRemainingInPlaylist);
                    currentPlaylistState.setSongCount(songsRemainingInPlaylist.size());
                    currentPlaylistState.setPlaybackOrderIds(null);
                    currentPlaylistState.setUpdatedAt(new Date());
                    Long currentSongId = currentPlaylistState.getCurrentSongId();
                    if (currentSongId != null && idsOfSongsMarkedForRemoval.contains(currentSongId)) {
                        currentPlaylistState.setCurrentSongId(null);
                        currentPlaylistState.setCurrentSongTitle(null);

                    }
                    Completable deleteDbSongsCompletable = songRepository.deleteSongsByIds(idsOfSongsMarkedForRemoval);
                    Completable updateDbSongsCompletable = songRepository.updateAll(songsRemainingInPlaylist);
                    Completable updateDbPlaylistCompletable = playlistRepository.update(currentPlaylistState);
                    return deleteDbSongsCompletable
                            .andThen(updateDbPlaylistCompletable)
                            .andThen(updateDbSongsCompletable)
                            .andThen(Single.just(currentPlaylistState));
                }));
        disposables.add(
                updateOperation
                        .compose(RxSchedulers.singleOnMain())
                        .doOnSuccess(updatedPlaylist -> {
                            Logger.i(TAG, "Successfully removed songs for " + updatedPlaylist.getName());
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
     * Updates the playback order of songs within a given playlist's active playback queue.
     * It reorders `playlist.getPlaylist()` to match the sequence of `songsInNewOrder`,
     * updates the `playbackOrderIds` in the database, and notifies listeners.
     *
     * @param playlist        The playlist whose playback order is to be updated.
     * @param songsInNewOrder A list of Song objects representing the new desired playback sequence.
     */
    public void updateSongsPlaylistPositions(Playlist playlist, List<Song> songsInNewOrder) {
        if (playlist == null || songsInNewOrder == null) {
            Logger.w(TAG, "Cannot update song positions: playlist or songsInNewOrder is null.");
            return;
        }
        List<Song> originalSongs = playlist.getPlaylist();
        List<Song> reorderedSongs = songsInNewOrder.stream()
                .map(newOrderSong -> originalSongs.stream()
                        .filter(originalSong -> originalSong.getId().equals(newOrderSong.getId()))
                        .findFirst()
                        .orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        playlist.getPlaylist().clear();
        playlist.getPlaylist().addAll(reorderedSongs);
        playlist.setPlaybackOrderFromSongs();
        playlist.setUpdatedAt(new Date());
        Logger.d(TAG, "Playlist " + playlist.getName() + " reordered. New playbackOrderIds: " + playlist.getPlaybackOrderIds());
        disposables.add(
                playlistRepository.update(playlist)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                () -> {
                                    Logger.i(TAG, "Successfully updated song playback order for playlist: " + playlist.getName());
                                    populateAndSend(EventType.PLAYLIST_NOTIFICATION_RECREATED_LIST, playlist);
                                },
                                throwable -> Logger.e(TAG, "Error updating song playback order for playlist: " + playlist.getName(), throwable)
                        )
        );
    }

    public void updateSongsPositions(List<Song> songs) {
        disposables.add(
                songRepository.updateAll(songs)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                () -> Logger.i(TAG, "Successfully updated songs positions"),
                                throwable -> Logger.e(TAG, "Error updating songs positions", throwable))
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
                .subscribe(() -> Logger.i(TAG, "Successfully removed all playlists and preset: " + presetName),
                        throwable -> Logger.e(TAG, "Error removing all playlists and preset: " + presetName, throwable))
        );
    }


    public void updatePreset(Preset presetToUpdate) {
        String newPresetName = presetToUpdate.getName().trim();
        val originalName = new AtomicReference<String>();
        disposables.add(
                presetRepository.findOneById(presetToUpdate.getId())
                        .switchIfEmpty(Single.error(new PresetNotFoundException("Preset with ID " + presetToUpdate.getId() + " not found for update.")))
                        .flatMap(dbPreset -> {
                            if (dbPreset.getName().equals(newPresetName)) {
                                Logger.i(TAG, "Preset name has not changed. No update performed for: " + newPresetName);
                                return Single.just(dbPreset);
                            }
                            return presetRepository.findByNameAndIdNotEqual(newPresetName, dbPreset.getId())
                                    .isEmpty()
                                    .flatMap(isEmpty -> {
                                        if (!isEmpty) {
                                            return Single.error(new PresetAlreadyExistsException("Preset name '" + newPresetName + "' is already taken."));
                                        }
                                        originalName.set(dbPreset.getName());
                                        dbPreset.setName(presetToUpdate.getName());
                                        return presetRepository.update(dbPreset)
                                                .andThen(
                                                        playlistRepository.findAllByPreset(originalName.get())
                                                                .flatMapCompletable(playlistsToUpdate -> {
                                                                    if (playlistsToUpdate.isEmpty()) {
                                                                        return Completable.complete();
                                                                    }
                                                                    for (Playlist playlist : playlistsToUpdate) {
                                                                        playlist.setPreset(newPresetName);
                                                                    }
                                                                    return playlistRepository.updateAll(playlistsToUpdate);
                                                                })
                                                )
                                                .toSingleDefault(dbPreset);
                                    });
                        })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                updatedPreset -> {
                                    notifyIfPresetWasActive(originalName.get(), updatedPreset);
                                    Logger.i(TAG, "Successfully processed update for preset: " + newPresetName);
                                },
                                throwable -> {
                                    String errorMessage;
                                    if (throwable instanceof PresetAlreadyExistsException) {
                                        Logger.w(TAG, throwable.getMessage());
                                        errorMessage = throwable.getMessage();
                                    } else if (throwable instanceof PresetNotFoundException) {
                                        Logger.e(TAG, throwable.getMessage());
                                        errorMessage = throwable.getMessage();
                                    } else if (throwable instanceof IllegalArgumentException) {
                                        Logger.e(TAG, "Error updating preset: " + throwable.getMessage());
                                        errorMessage = "Invalid data for preset update.";
                                    } else {
                                        Logger.e(TAG, "Error updating preset: " + newPresetName, throwable);
                                        errorMessage = "An unexpected error occurred while updating the preset.";
                                    }
                                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
                                }
                        )
        );
    }

    /**
     * If current preset is active , notify about it by updating properties and sending broadcast
     *
     * @param updatedPreset original preset which was updated
     */
    private void notifyIfPresetWasActive(String originalName, Preset updatedPreset) {
        val sp = getDefaultSharedPreferences(this);
        val currentPreset = sp.getString(Property.CURRENT_PRESET, null);
        if (currentPreset != null && currentPreset.equals(originalName)) {
            Logger.d(TAG, "[EVENT] Playlist service preset made active after rename");
            val spEdit = sp.edit();
            spEdit.putString(Property.CURRENT_PRESET, updatedPreset.getName());
            spEdit.apply();
            Intent intent = new Intent(PRESET_ACTIVATED.getCode());
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        }
    }


    /**
     * Removes playlist from database , reindex remaining playlists positions and notifies about removal
     *
     * @param removedPlaylist playlist to be removed
     */
    public void removePlaylist(Playlist removedPlaylist) {
        Logger.d(TAG, "Removing playlist: " + removedPlaylist.getName());
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
                .subscribe(() -> Logger.i(TAG, "Successfully removed playlist ID: " + playlistId + ", Name: " + removedPlaylist.getName()));
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
                                Logger.d(TAG, "Found unique name for pasted playlist: " + uniqueName);
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
                            Logger.d(TAG, "Cloned playlist saved with new ID: " + newPlaylistId + ", Name: " + preparedPlaylist.getName());
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
                            Logger.i(TAG, "Paste operation for playlist ID " + copyId + " completed successfully.");
                            onPlaylistPasted.accept(playlist);
                        },
                        throwable -> Logger.e(TAG, "Error during paste operation for playlist ID " + copyId, throwable)
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
        Logger.d(TAG, "Cloning songs from original playlist ID: " + originalPlaylistId + " into new playlist: " + savedClone.getName());
        return songRepository.getSongsForPlaylist(originalPlaylistId) // Assuming this returns Single<List<Song>>
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.computation())
                .flatMap(originalSongs -> {
                    if (originalSongs.isEmpty()) {
                        Logger.d(TAG, "No songs to clone from playlist ID: " + originalPlaylistId);
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
                            Logger.e(TAG, "Failed to clone song: " + originalSong.getFilename(), e);
                            return Single.error(new RuntimeException("Clone operation failed for song: " + originalSong.getFilename(), e));
                        }
                    }
                    Logger.d(TAG, "Successfully prepared " + clonedSongObjects.size() + " songs for cloning.");

                    // Now, insert these cloned songs into the database and get their new IDs
                    return songRepository.createAll(clonedSongObjects) // Use the method that returns IDs
                            .subscribeOn(Schedulers.io())
                            .observeOn(Schedulers.computation())
                            .map(insertedSongIds -> {
                                if (insertedSongIds.size() != clonedSongObjects.size()) {
                                    Logger.w(TAG, "ID count mismatch during song cloning. Expected: " + clonedSongObjects.size() + ", Got: " + insertedSongIds.size());
                                }
                                Logger.d(TAG, "Received " + insertedSongIds.size() + " new IDs for cloned songs.");
                                for (int i = 0; i < clonedSongObjects.size() && i < insertedSongIds.size(); i++) {
                                    clonedSongObjects.get(i).setId(insertedSongIds.get(i));
                                }
                                Logger.d(TAG, "Successfully saved " + clonedSongObjects.size() + " cloned songs for " + savedClone.getName());
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
     * @param playlistToMakeActive playlist to be made active
     * @param force                if true , this mean the active playlist must play
     */
    public void setActive(Playlist playlistToMakeActive, boolean force) {
        val playlistToActivateId = playlistToMakeActive.getId();
        Logger.d(TAG, "Attempting to set playlist ID as active: " + playlistToActivateId);
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(EventType.OPERATION_STARTED.getCode()));
        Single<Playlist> activationFlow = findActive()
                .flatMap(currentlyActivePlaylist -> {
                    if (!force && currentlyActivePlaylist.getId().equals(playlistToActivateId)) {
                        populateAndSend(PLAYLIST_NOTIFICATION_IS_ACTIVE_PLAYING, currentlyActivePlaylist);
                        return Maybe.error(new OperationSkippedException("Playlist already active and not forced"));
                    } else {
                        Logger.d(TAG, "Deactivating current active playlist: " + currentlyActivePlaylist.getName());
                        currentlyActivePlaylist.setActive(false);
                        return playlistRepository.update(currentlyActivePlaylist)
                                .andThen(playlistRepository.findOneById(playlistToActivateId));
                    }
                })
                .switchIfEmpty(Single.defer(() -> {
                    // If no active playlist is found, find the playlist by ID
                    Logger.d(TAG, "No active playlist found or previous path was empty. Finding playlist ID: " + playlistToActivateId + " to activate.");
                    return playlistRepository.findOneById(playlistToActivateId)
                            .switchIfEmpty(Single.error(new PlaylistNotFoundException("Playlist with ID " + playlistToActivateId + " not found.")));
                }))
                .flatMap(playlist -> {
                    Logger.d(TAG, "Loading songs for playlist to activate: " + playlist.getName());
                    return loadSongs(playlist);
                })
                .flatMap(this::buildPlaylistMakeActiveAndNotify)
                .flatMap(finalPlaylistToActivate -> {
                    Logger.d(TAG, "Setting playlist " + finalPlaylistToActivate.getName() + " as active in DB.");
                    finalPlaylistToActivate.setActive(true);
                    return playlistRepository.update(finalPlaylistToActivate)
                            .andThen(Single.just(finalPlaylistToActivate));
                });

        Disposable task = activationFlow
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        activatedPlaylist -> {
                            Logger.d(TAG, "Playlist " + activatedPlaylist.getName() + " successfully set as active.");
                            populateAndSend(EventType.PLAYLIST_NOTIFICATION_ACTIVE, activatedPlaylist);
                            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(EventType.OPERATION_FINISHED.getCode()));
                        },
                        throwable -> {
                            if (throwable instanceof OperationSkippedException) {
                                Logger.d(TAG, "Set active operation skipped as planned: " + throwable.getMessage());
                                LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(EventType.OPERATION_FINISHED.getCode()));
                            } else {
                                Logger.e(TAG, "Failed to set active playlist.", throwable);
                                LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(EventType.OPERATION_FINISHED.getCode()));
                            }
                        }
                );
        disposables.add(task);
    }

    /**
     * Finds active playlist and loads all songs for it
     * If found , build playlists and make it active , otherwise trigger onNoPlaylistFound
     *
     * @param onPlaylistFound   callback when active playlist is found
     * @param onNoPlaylistFound callback when no active playlist is found
     */
    public void getActiveAndLoadSongs(Consumer<Playlist> onPlaylistFound, Runnable onNoPlaylistFound) {
        disposables.add(findActive()
                .doOnSuccess(playlist -> Logger.d(TAG, "Find active found: " + playlist.getName()))
                .doOnComplete(() -> {
                    Logger.d(TAG, "findActive completed empty. Triggering onNoPlaylistFound.");
                    AndroidSchedulers.mainThread().scheduleDirect(onNoPlaylistFound);
                })
                .flatMapSingle(this::loadSongs)
                .flatMap(playlistWithSongs -> this.buildPlaylistMakeActiveAndNotify(playlistWithSongs, false))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(playlist -> {
                    Logger.d(TAG, "Active playlist loaded successfully.");
                    onPlaylistFound.accept(playlist);
                    populateAndSend(PLAYLIST_NOTIFICATION_NEW_ACTIVE, playlist);
                }, throwable -> {
                    if (throwable instanceof NoSuchElementException) {
                        Logger.d(TAG, "Unable to find active list");
                    } else {
                        Logger.e(TAG, "Error loading active playlist", throwable);
                    }
                }));
    }

    /**
     * Creates playlist out of all available songs
     * Search for current song within. If none present, set first from list
     *
     * @param playlist playlist to be made active and played
     * @return Completable that completes when playlist is made active
     */
    private Single<Playlist> buildPlaylistMakeActiveAndNotify(Playlist playlist) {
        return buildPlaylistMakeActiveAndNotify(playlist, true);
    }

    /**
     * Creates playlist out of all available songs
     * Search for current song within. If none present, set first from list
     * Notify about operation results accordingly if notify is true
     *
     * @param playlist playlist to be made active and played
     * @param notify   if true , notify about operation results
     * @return Completable that completes when playlist is made active
     */
    private Single<Playlist> buildPlaylistMakeActiveAndNotify(Playlist playlist, boolean notify) {
        if (playlist == null) {
            Logger.e(TAG, "Cannot build playlist - playlist is null");
            return Single.error(new IllegalArgumentException("Playlist cannot be null"));
        }
        try {
            preparePlaylistForPlayback(playlist, false);
            if (playlist.getPlaylist() == null || playlist.getPlaylist().isEmpty()) {
                String playlistName = (playlist.getName() != null) ? playlist.getName() : "unnamed playlist";
                Logger.d(TAG, "Playback list is null or empty after preparation for: " + playlistName);
                if (notify) {
                    populateAndSend(EventType.PLAYLIST_NOTIFICATION_PLAY_NO_SONGS, playlist);
                }
                LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(EventType.OPERATION_FINISHED.getCode()));
                return Single.just(playlist);
            }

            List<Song> currentPlaybackOrder = playlist.getPlaylist();
            Long currentSongId = playlist.getCurrentSongId();

            if (currentSongId == null && !isEmpty(currentPlaybackOrder)) {
                Song currentSong = currentPlaybackOrder.stream()
                        .filter(song -> song != null && song.getId() != null)
                        .findFirst()
                        .orElse(null);
                if (currentSong != null) {
                    playlist.setCurrentSongId(currentSong.getId());
                    playlist.setCurrentSongTitle(currentSong.getDisplayName());
                    playlist.setCurrentSong(currentSong);
                    Logger.d(TAG, "Setting current song to first valid song in playback order: " + currentSong.getFilename());
                } else {
                    Logger.e(TAG, "No valid songs found in playback order for playlist: " + playlist.getName());
                }
            } else if (currentSongId != null && !isEmpty(currentPlaybackOrder)) {
                Optional<Song> foundSong = currentPlaybackOrder.stream()
                        .filter(song -> song != null && song.getId() != null && song.getId().equals(currentSongId))
                        .findFirst();
                if (foundSong.isPresent()) {
                    playlist.setCurrentSong(foundSong.get());
                    Logger.d(TAG, "Found and set current song: " + foundSong.get().getFilename());
                } else {
                    Song fallbackSong = currentPlaybackOrder.stream()
                            .filter(song -> song != null && song.getId() != null)
                            .findFirst()
                            .orElse(null);
                    if (fallbackSong != null) {
                        Logger.d(TAG, "Song with ID " + currentSongId + " not found in playback order for playlist " + playlist.getName() + ". Using first valid song: " + fallbackSong.getFilename());
                        playlist.setCurrentSongId(fallbackSong.getId());
                        playlist.setCurrentSong(fallbackSong);
                        playlist.setCurrentSongTitle(fallbackSong.getDisplayName());
                    } else {
                        Logger.e(TAG, "No valid fallback songs found in playback order for playlist: " + playlist.getName());
                        playlist.setCurrentSongId(null);
                        playlist.setCurrentSongTitle(null);
                        playlist.setCurrentSong(null);
                    }
                }
            } else if (isEmpty(currentPlaybackOrder)) {
                Logger.w(TAG, "Current playback order is empty for playlist: " + playlist.getName() + ". Clearing current song.");
                playlist.setCurrentSongId(null);
                playlist.setCurrentSongTitle(null);
                playlist.setCurrentSong(null);
            }

            return Single.just(playlist);
        } catch (Exception e) {
            String playlistNameException = playlist.getName() != null ? playlist.getName() : "unknown playlist";
            Logger.e(TAG, "Error building playlist: " + playlistNameException, e);
            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(EventType.OPERATION_FINISHED.getCode()));
            return Single.error(e);
        }
    }

    /**
     * Prepares a playlist for playback. It first attempts to restore the song order
     * from the persisted 'playbackOrderIds'. If no valid saved order is found,
     * or if the saved order results in an empty list of playable songs,
     * it generates a new playback order based on the playlist's master songs
     * and the current shuffle preference.
     * <p>
     * This method MODIFIES the passed-in playlist object by:
     * 1. Setting its internal playback list (playlist.setPlaylist()).
     * 2. If a new order is generated, it also updates the playlist's
     * 'playbackOrderIds' string to reflect this new order.
     *
     * @param playlist The Playlist object to prepare. It's modified directly.
     * @return The modified Playlist object.
     */
    public Playlist preparePlaylistForPlayback(Playlist playlist, boolean newPlaylist) {
        if (playlist == null) {
            Logger.e(TAG, "Input playlist is null. Cannot prepare for playback.");
            return null;
        }
        List<Song> masterSongs = playlist.getSongs();
        if (masterSongs == null) {
            masterSongs = new ArrayList<>();
        }
        List<Song> finalPlaybackOrder = new ArrayList<>();
        List<Long> savedSongIdsOrder = playlist.getPlaybackOrder();
        if (!newPlaylist && !savedSongIdsOrder.isEmpty() && !masterSongs.isEmpty()) {
            List<Song> relevantMasterSongs = masterSongs.stream()
                    .filter(song -> song != null && song.getId() != null && savedSongIdsOrder.contains(song.getId()))
                    .sorted((song1, song2) -> {
                        Integer index1 = savedSongIdsOrder.indexOf(song1.getId());
                        Integer index2 = savedSongIdsOrder.indexOf(song2.getId());
                        return index1.compareTo(index2);
                    }).collect(Collectors.toList());
            finalPlaybackOrder.addAll(relevantMasterSongs);
            if (!finalPlaybackOrder.isEmpty()) {
                Logger.d(TAG, "Successfully restored playback order using sorting for: " + playlist.getName() + " with " + finalPlaybackOrder.size() + " songs.");
            }
        }
        //no playlist , generate new
        if (finalPlaybackOrder.isEmpty()) {
            Logger.d(TAG, (savedSongIdsOrder.isEmpty() ? "No saved order found" : "Saved order resulted in empty list") + " for '" + playlist.getName() + "'. Generating new playlist order.");
            if (masterSongs.isEmpty()) {
                Logger.d(TAG, "Master song collection is empty for playlist: " + playlist.getName() + ". Cannot generate new order.");
                playlist.setPlaylist(new ArrayList<>());
                playlist.setPlaybackOrderFromSongs();
                return playlist;
            }
            val sp = getDefaultSharedPreferences(this);
            val shuffle = sp.getBoolean(Property.SHUFFLE_MODE, true);
            createPlaylist(playlist, shuffle);
            finalPlaybackOrder = playlist.getPlaylist();
            if (finalPlaybackOrder == null) {
                finalPlaybackOrder = new ArrayList<>();
            }
            playlist.setPlaybackOrderFromSongs();
            Logger.d(TAG, "Generated new playlist order for: " + playlist.getName() +
                    " with " + finalPlaybackOrder.size() +
                    " songs, shuffle: " + shuffle +
                    ", new order IDs: " + playlist.getPlaybackOrderIds());
            //fire forget quick update of playlist
            disposables.add(playlistRepository.update(playlist)
                    .subscribeOn(Schedulers.io())
                    .subscribe(() -> {
                        Logger.d(TAG, "Playlist updated successfully with new playlist order.");
                    }));
        }
        playlist.setPlaylist(new ArrayList<>(finalPlaybackOrder));
        playlist.getPlaylist().removeAll(Collections.singleton(null));
        return playlist;
    }

    public void resetActiveFromPreset() {
        disposables.add(
                findActive()
                        .observeOn(Schedulers.io())
                        .flatMapCompletable(playlist -> {
                            playlist.setActive(false);
                            Logger.d(TAG, "Deactivating playlist (on IO thread): " + playlist.getName());
                            return playlistRepository.update(playlist);
                        })
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(() -> Logger.d(TAG, "resetActiveFromPreset operation completed successfully."),
                                throwable -> Logger.e(TAG, "resetActiveFromPreset operation failed.", throwable)
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
                        .subscribe(() -> Logger.d("PlaylistService", "Successfully saved/updated all " + playlists.size() + " playlists in the background."),
                                throwable -> Logger.e("PlaylistService", "Error saving/updating all playlists in the background", throwable))
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
                                Logger.i(TAG, "Preset created: ID " + newId + ", Name: " + newPreset.getName());
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
                                Logger.e(TAG, "Error creating preset: " + trimmedName, throwable);
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
                .subscribe(integer -> Logger.d(TAG, "Song updated successfully: " + song.getFilename())));
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
        Logger.d(TAG, "[EVENT] Playlist notification " + type + " sent: " + playlist.getName());
    }
}
