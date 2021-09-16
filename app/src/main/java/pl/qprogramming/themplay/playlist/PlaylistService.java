package pl.qprogramming.themplay.playlist;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.VolumeShaper;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.reactiveandroid.query.Select;

import java.io.IOException;
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
    //TODO extract to vars
    public static final int DURATION_MILLIS = 4000;
    private Playlist activePlaylist;
    private int activePlaylistPosition;
    private float volume = 1;
    private MediaPlayer mediaPlayer = new MediaPlayer();
    private MediaPlayer auxPlayer = new MediaPlayer();

    private final IBinder mBinder = new LocalBinder();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Binding service to " + intent);
        return mBinder;

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        if (auxPlayer != null) {
            auxPlayer.release();
        }
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
    public void setActive(Playlist item, int position) {
        val optionalActive = findActive();
        val optionalPlaylist = findById(item.getId());
        val playlist = optionalPlaylist.orElseThrow(PlaylistNotFoundException::new);
        if (!optionalActive.isPresent() || !optionalActive.get().getId().equals(playlist.getId())) {
            if (optionalActive.isPresent()) {
                val active = optionalActive.get();
                if (mediaPlayer.isPlaying()) {
                    val currentSong = active.getCurrentSong();
                    currentSong.setCurrentPosition(mediaPlayer.getCurrentPosition());
                    currentSong.save();
                }
                active.setActive(false);
                active.save();
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
        fetchSongsByPlaylistAsync(playlist).subscribe(songs -> {
            activePlaylistPosition = position;
            activePlaylist = playlist;
            setSongsAndMakeActive(activePlaylist, songs);
            populateAndSend(EventType.PLAYLIST_NOTIFICATION_PLAY, position);
        });
    }

    /**
     * Sets songs into playlist, and search for current song within. If none present, set first from list
     *
     * @param playlist playlist to be made active and played
     * @param songs    songs fetched from DB
     */
    private void setSongsAndMakeActive(Playlist playlist, List<Song> songs) {
        playlist.setSongs(songs);
        var currentSong = playlist.getCurrentSong();
        if (currentSong == null && isEmpty(playlist.getSongs())) {
            val notActiveMsg = MessageFormat.format(getString(R.string.playlist_active_no_songs), playlist.getName());
            Toast.makeText(getApplicationContext(), notActiveMsg, Toast.LENGTH_LONG).show();
            return;
        } else if (currentSong == null && !isEmpty(playlist.getSongs())) {
            currentSong = playlist.getSongs().get(0);
            playlist.setCurrentSong(currentSong);
        }
        playlist.setActive(true);
        playlist.save();
        val msg = MessageFormat.format(getString(R.string.playlist_now_playing), currentSong.getFilename());
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
        play();
    }

    /**
     * Plays current playlist
     */
    public void play() {
        //might happen we just press play and active list is not yet selected
        if (activePlaylist == null) {
            val playlists = getAll();
            if (!playlists.isEmpty()) {
                makeActiveAndNotify(playlists.get(0), 0);
            } else {
                Toast.makeText(this, getString(R.string.playlist_no_playlists), Toast.LENGTH_LONG).show();
            }
        } else {
            val currentSong = activePlaylist.getCurrentSong();
            try {
                if (mediaPlayer.isPlaying()) {
                    auxPlayer = new MediaPlayer();
                    auxPlayer.setDataSource(getApplicationContext(), Uri.parse(currentSong.getFileUri()));
                    auxPlayer.setLooping(true);
                    auxPlayer.prepare();
                    auxPlayer.seekTo(currentSong.getCurrentPosition());
                    auxPlayer.start();
                    fadeInOrOutAudio(mediaPlayer, true);
                    fadeInOrOutAudio(auxPlayer, false);
                    mediaPlayer = auxPlayer;
                    auxPlayer = null;
                } else {
                    mediaPlayer.reset();
                    mediaPlayer.setDataSource(getApplicationContext(), Uri.parse(currentSong.getFileUri()));
                    mediaPlayer.setLooping(true);
                    mediaPlayer.prepare();
                    mediaPlayer.seekTo(currentSong.getCurrentPosition());
                    mediaPlayer.start();
                    fadeInOrOutAudio(mediaPlayer, false);
                }

            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG, e.getMessage());
                val errorMsg = MessageFormat.format(getString(R.string.playlist_cant_play), currentSong);
                Toast.makeText(getBaseContext(), errorMsg, Toast.LENGTH_LONG).show();
            }
        }
    }

    public void pause() {
        mediaPlayer.pause();
    }

    private void fadeInOrOutAudio(MediaPlayer mediaPlayer, boolean out) {
        val config = out ? fadeOutConfig() : fadeInConfig();
        val volumeShaper = mediaPlayer.createVolumeShaper(config);
        volumeShaper.apply(VolumeShaper.Operation.PLAY);
    }


    private VolumeShaper.Configuration fadeOutConfig() {
        val times = new float[]{0f, 1f};
        val volumes = new float[]{1f, 0f};
        return new VolumeShaper.Configuration.Builder()
                .setDuration(DURATION_MILLIS)
                .setCurve(times, volumes)
                .setInterpolatorType(VolumeShaper.Configuration.INTERPOLATOR_TYPE_CUBIC)
                .build();
    }

    private VolumeShaper.Configuration fadeInConfig() {
        val times = new float[]{0f, 1f};
        val volumes = new float[]{0f, 1f};
        return new VolumeShaper.Configuration.Builder()
                .setDuration(DURATION_MILLIS)
                .setCurve(times, volumes)
                .setInterpolatorType(VolumeShaper.Configuration.INTERPOLATOR_TYPE_CUBIC)
                .build();
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
