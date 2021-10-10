package pl.qprogramming.themplay.playlist;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.reactiveandroid.Model;
import com.reactiveandroid.query.Select;

import java.text.MessageFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import androidx.annotation.Nullable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.val;
import lombok.var;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.playlist.exceptions.PlaylistNotFoundException;
import pl.qprogramming.themplay.settings.Property;

import static androidx.preference.PreferenceManager.getDefaultSharedPreferences;
import static pl.qprogramming.themplay.util.Utils.ARGS;
import static pl.qprogramming.themplay.util.Utils.PLAYLIST;
import static pl.qprogramming.themplay.util.Utils.POSITION;
import static pl.qprogramming.themplay.util.Utils.createPlaylist;
import static pl.qprogramming.themplay.util.Utils.isEmpty;

public class PlaylistService extends Service {
    private static final String TAG = PlaylistService.class.getSimpleName();

    @Setter
    private int activePlaylistPosition;
    private final IBinder mBinder = new LocalBinder();

    @SuppressLint("CheckResult")
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Binding service to " + intent + "this:" + this);
        findActive()
                .ifPresent(playlist -> fetchSongsByPlaylistAsync(playlist)
                        .subscribe(songs -> setSongsAndMakeActive(playlist, songs, false)));
        return mBinder;
    }

    //repository methods

    /**
     * Returns all playlists. For each of those playlist try to fetch it's songs relation and store it into songs
     *
     * @return List of all Playlists
     */
    public List<Playlist> getAll() {
        val sp = getDefaultSharedPreferences(this);
        val currentPresetName = sp.getString(Property.CURRENT_PRESET, null);
        return getAll(currentPresetName);
    }

    public List<Playlist> getAll(String presetName) {
        return Select.from(Playlist.class).where(Playlist.PRESET + " =?", presetName).fetch();
    }

    public Single<List<Playlist>> getAllAsync() {
        val sp = getDefaultSharedPreferences(this);
        val currentPresetName = sp.getString(Property.CURRENT_PRESET, null);
        return Select.from(Playlist.class).where(Playlist.PRESET + " =?", currentPresetName).fetchAsync();
    }

    /**
     * Find playlist by ID . If it's present , load all songs from that playlist
     *
     * @param id identifier of playlist
     * @return Optional of Playlist with potentially loaded songs
     */
    public Optional<Playlist> findById(long id) {
        return Optional.ofNullable(Select.from(Playlist.class).where("id = ?", id).fetchSingle());
    }

    /**
     * Find active playlist If it's present , load all songs from that playlist
     *
     * @return Optional of active Playlist with potentially loaded songs
     */

    public Optional<Playlist> findActive() {
        val sp = getDefaultSharedPreferences(this);
        val currentPresetName = sp.getString(Property.CURRENT_PRESET, null);
        return Optional.ofNullable(Select.from(Playlist.class).where(Playlist.ACTIVE + " = ? and " + Playlist.PRESET + "= ?", true, currentPresetName).fetchSingle());
    }

    public Single<List<Song>> fetchSongsByPlaylistAsync(Playlist playlist) {
        return Select.from(PlaylistSongs.class)
                .where(PlaylistSongs.PLAYLIST + " = ?", playlist.getId())
                .fetchAsync()
                .flatMapObservable(Observable::fromIterable)
                .map(PlaylistSongs::getSong)
                .toList()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    public List<Song> fetchSongsByPlaylistSync(Playlist playlist) {
        return Select.from(PlaylistSongs.class)
                .where(PlaylistSongs.PLAYLIST + " = ?", playlist.getId())
                .fetch().stream()
                .map(PlaylistSongs::getSong)
                .collect(Collectors.toList());
    }
    //functional methods

    public void save(Playlist playlist) {
        playlist.saveAsync()
                .subscribeOn(Schedulers.io())
                .subscribe();
    }

    public void addPlaylist(Playlist playlist) {
        Log.d(TAG, "Adding new playlist" + playlist);
        playlist.saveAsync()
                .subscribeOn(Schedulers.io())
                .subscribe();
        populateAndSend(EventType.PLAYLIST_NOTIFICATION_ADD);
    }

    public void addSongToPlaylist(Playlist playlist, Song song) {
        //TODO reshuffle random playlist when adding ?
        Log.d(TAG, "Adding song to playlist ");
        playlist.addSong(song);
        val relation = PlaylistSongs.builder().playlist(playlist).song(song).build();
        relation.saveAsync()
                .subscribeOn(Schedulers.io())
                .subscribe();
        playlist.saveAsync()
                .subscribeOn(Schedulers.io())
                .subscribe();
    }

    public void removeSongFromPlaylist(Playlist playlist, List<Song> songs) {
        playlist.getSongs().removeAll(songs);
        playlist.setSongCount(playlist.getSongs().size());
        for (Song song : songs) {
            if (song.equals(playlist.getCurrentSong())) {
                playlist.setCurrentSong(null);
            }
            song.deleteAsync()
                    .subscribeOn(Schedulers.io())
                    .subscribe();
        }
        playlist.saveAsync()
                .subscribeOn(Schedulers.io())
                .subscribe();
        populateAndSend(EventType.PLAYLIST_NOTIFICATION_DELETE);
        Toast.makeText(getApplicationContext(), getString(R.string.playlist_removed_selected_songs), Toast.LENGTH_SHORT).show();
    }

    public void removePlaylistsFromPreset(String presetName) {
        getAll(presetName).forEach(playlist -> {
            val songs = fetchSongsByPlaylistSync(playlist);
            playlist.setCurrentSong(null);
            playlist.deleteAsync()
                    .subscribeOn(Schedulers.io())
                    .subscribe();
            songs.forEach(song -> song.deleteAsync()
                    .subscribeOn(Schedulers.io())
                    .subscribe());
        });
    }


    @SuppressLint("CheckResult")
    public void removePlaylist(Playlist playlist, int position) {
        populateAndSend(EventType.PLAYLIST_NOTIFICATION_DELETE, position, playlist);
        val songs = fetchSongsByPlaylistSync(playlist);
        playlist.setCurrentSong(null);
        playlist.delete();
        songs.forEach(Model::delete);
    }

    @SneakyThrows
    public void setActive(Playlist item, int position) {
        val optionalActive = findActive();
        val optionalPlaylist = findById(item.getId());
        val playlist = optionalPlaylist.orElseThrow(PlaylistNotFoundException::new);
        if (!optionalActive.isPresent() || !optionalActive.get().getId().equals(playlist.getId())) {
            if (optionalActive.isPresent()) {
                val active = optionalActive.get();
                active.setActive(false);
                active.saveAsync()
                        .subscribeOn(Schedulers.io())
                        .subscribe();
            }
            makeActiveAndNotify(playlist, position);
        }
    }

    /**
     * Loads all songs for selected playlist, once fetch is completed make all actions required to
     * activate and play that playlist
     *
     * @param playlist playlist to be made active
     * @param position where in reculed list that position is ( for any potential notification )
     */
    @SuppressLint("CheckResult")
    private void makeActiveAndNotify(Playlist playlist, int position) {
        sendBroadcast(new Intent(EventType.OPERATION_STARTED.getCode()));
        fetchSongsByPlaylistAsync(playlist).subscribe(songs -> {
            activePlaylistPosition = position;
            setSongsAndMakeActive(playlist, songs, true);
        });
    }

    /**
     * Sets songs into playlist, and search for current song within. If none present, set first from list
     *
     * @param playlist playlist to be made active and played
     * @param songs    songs fetched from DB
     * @param play     true if playback should be started ( it will publish other event )
     */
    private void setSongsAndMakeActive(Playlist playlist, List<Song> songs, boolean play) {
        playlist.setSongs(songs);
        val sp = getDefaultSharedPreferences(this);
        val shuffle = sp.getBoolean(Property.SHUFFLE_MODE, true);
        createPlaylist(playlist, shuffle);
        var currentSong = playlist.getCurrentSong();
        if (currentSong == null && isEmpty(playlist.getSongs())) {
            val notActiveMsg = MessageFormat.format(getString(R.string.playlist_active_no_songs), playlist.getName());
            Toast.makeText(getApplicationContext(), notActiveMsg, Toast.LENGTH_LONG).show();
            return;
        } else if (currentSong == null && !isEmpty(playlist.getSongs())) {
            currentSong = playlist.getPlaylist().get(0);
            playlist.setCurrentSong(currentSong);
        }
        playlist.setActive(true);
        playlist.saveAsync()
                .subscribeOn(Schedulers.io())
                .subscribe();
        if (play) {
            populateAndSend(EventType.PLAYLIST_NOTIFICATION_ACTIVE, activePlaylistPosition, playlist);
        } else {
            populateAndSend(EventType.PLAYLIST_NOTIFICATION_NEW_ACTIVE, activePlaylistPosition, playlist);
        }
        sendBroadcast(new Intent(EventType.OPERATION_FINISHED.getCode()));
    }

    public void resetActiveFromPreset() {
        findActive().ifPresent(playlist -> {
            playlist.setActive(false);
            playlist.saveAsync()
                    .subscribeOn(Schedulers.io())
                    .subscribe();
        });
    }


    public class LocalBinder extends Binder {
        public PlaylistService getService() {
            return PlaylistService.this;
        }
    }

    private void populateAndSend(EventType type) {
        populateAndSend(type, -1, null);
    }

    private void populateAndSend(EventType type, int position, Playlist data) {
        Intent intent = new Intent(type.getCode());
        val args = new Bundle();
        args.putSerializable(POSITION, position);
        if (data != null) {
            args.putSerializable(PLAYLIST, data);
        }
        intent.putExtra(ARGS, args);
        sendBroadcast(intent);
    }
}
