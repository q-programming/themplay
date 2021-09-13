package pl.qprogramming.themplay.playlist;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;

import com.google.android.material.snackbar.Snackbar;
import com.reactiveandroid.query.Select;

import java.text.MessageFormat;
import java.util.List;
import java.util.Optional;

import androidx.annotation.Nullable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import lombok.SneakyThrows;
import lombok.val;
import lombok.var;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.playlist.exceptions.PlaylistNotFoundException;

import static pl.qprogramming.themplay.util.Utils.isEmpty;

public class PlaylistService extends Service {
    private static final String TAG = PlaylistService.class.getSimpleName();
    public static final String POSITION = "position";
    public static final String PLAYLIST = "playlist";
    public static final String ARGS = "args";

    private final IBinder mBinder = new LocalBinder();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Binding service to " + intent);
        return mBinder;

    }

    //repository methods

    /**
     * Returns all playlists. For each of those playlist try to fetch it's songs relation and store it into songs
     *
     * @return List of all Playlists
     */
    public List<Playlist> getAll() {
        return Select.from(Playlist.class).fetch();
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
        return Optional.ofNullable(Select.from(Playlist.class).where(Playlist.ACTIVE + " = ?", true).fetchSingle());
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
    //functional methods

    public void addPlaylist(Playlist playlist) {
        Log.d(TAG, "Adding new playlist" + playlist);
        playlist.saveAsync()
                .subscribeOn(Schedulers.io())
                .subscribe();
        populateAndSend(EventType.PLAYLIST_NOTIFICATION_ADD);
    }

    public void addSongToPlaylist(Playlist playlist, Song song) {
        Log.d(TAG, "Adding song to playlist ");
        playlist.addSong(song);
        val relation = PlaylistSongs.builder().playlist(playlist).song(song).build();
        relation.saveAsync()
                .subscribeOn(Schedulers.io())
                .subscribe();
        playlist.saveAsync()
                .subscribeOn(Schedulers.io())
                .subscribe();
        populateAndSend(EventType.PLAYLIST_NOTIFICATION_ADD);
    }


    @SuppressLint("CheckResult")
    public void removePlaylist(Playlist playlist, int position) {
        fetchSongsByPlaylistAsync(playlist)
                .subscribe(songs -> songs
                        .forEach(song -> {
                            song.deleteAsync()
                                    .subscribeOn(Schedulers.io())
                                    .subscribe();
                        }));
        playlist.deleteAsync()
                .subscribeOn(Schedulers.io())
                .subscribe();
        populateAndSend(EventType.PLAYLIST_NOTIFICATION_DELETE, position);
    }

    @SneakyThrows
    public void setActive(Playlist item, int position, View view) {
        val optionalActive = findActive();
        val optionalPlaylist = findById(item.getId());
        val playlist = optionalPlaylist.orElseThrow(PlaylistNotFoundException::new);
        if (!optionalActive.isPresent() || !optionalActive.get().getId().equals(playlist.getId())) {
            if (optionalActive.isPresent()) {
                val active = optionalActive.get();
                active.setActive(false);
                active.save();
            }
            makeActiveAndNotify(playlist, position, view);
        }
    }

    @SuppressLint("CheckResult")
    private void makeActiveAndNotify(Playlist playlist, int position, View view) {
        //load all songs ( might be needed for next song )
        var currentSong = playlist.getCurrentSong();
        val msg = MessageFormat.format(getString(R.string.playlist_active), playlist.getName());
        if (currentSong != null) {
            //just play it and then do fetch
            Snackbar.make(view, msg, Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            playlist.setActive(true);
            playlist.save();
            populateAndSend(EventType.PLAYLIST_NOTIFICATION_PLAY, position);
        } else {
            fetchSongsByPlaylistAsync(playlist).subscribe(songs -> {
                playlist.setSongs(songs);
                if (isEmpty(playlist.getSongs())) {
                    val notActiveMsg = MessageFormat.format(getString(R.string.playlist_active_no_songs), playlist.getName());
                    Snackbar.make(view, notActiveMsg, Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                } else {
                    val newCurrentSong = playlist.getSongs().get(0);
                    playlist.setCurrentSong(newCurrentSong);
                    Snackbar.make(view, msg, Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                }
                playlist.setActive(true);
                playlist.save();
                populateAndSend(EventType.PLAYLIST_NOTIFICATION_PLAY, position);

            });

        }

    }


    public class LocalBinder extends Binder {
        public PlaylistService getService() {
            return PlaylistService.this;
        }
    }

    private void populateAndSend(EventType type) {
        populateAndSend(type, -1, null);
    }

    private void populateAndSend(EventType type, int position) {
        populateAndSend(type, position, null);
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
