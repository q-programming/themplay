package pl.qprogramming.themplay.playlist;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.reactiveandroid.query.Select;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;
import java.util.Optional;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.val;
import lombok.var;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.playlist.exceptions.PlaylistNotFoundException;
import pl.qprogramming.themplay.settings.Property;

import static androidx.preference.PreferenceManager.getDefaultSharedPreferences;
import static pl.qprogramming.themplay.util.Utils.isEmpty;

public class PlaylistService extends Service {
    private static final String TAG = PlaylistService.class.getSimpleName();
    public static final String POSITION = "position";
    public static final String PLAYLIST = "playlist";
    public static final String ARGS = "args";
    @Getter
    private Playlist activePlaylist;
    @Setter
    private int activePlaylistPosition;
    private MediaPlayer mediaPlayer = new MediaPlayer();
    private MediaPlayer auxPlayer;
    private final IBinder mBinder = new LocalBinder();

    @SuppressLint("CheckResult")
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Binding service to " + intent);
        findActive().ifPresent(playlist -> {
            fetchSongsByPlaylistAsync(playlist).subscribe(playlist::setSongs);
            activePlaylist = playlist;
        });
        return mBinder;
    }

    private int getDuration() {
        val sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        return Integer.parseInt(sp.getString(Property.FADE_DURATION, "4")) * 1000;
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
                if (isPlaying()) {
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
        fadeIntoNewSong(currentSong, currentSong.getCurrentPosition());
        populateAndSend(EventType.PLAYLIST_NOTIFICATION_ACTIVE, activePlaylistPosition);
    }

    /**
     * Plays current playlist
     */
    public void play() {
        if (activePlaylist == null) {
            Toast.makeText(this, getString(R.string.playlist_no_active_playlist), Toast.LENGTH_LONG).show();
        } else {
            val currentSong = activePlaylist.getCurrentSong();
            fadeIntoNewSong(currentSong, currentSong.getCurrentPosition());
            populateAndSend(EventType.PLAYLIST_NOTIFICATION_PLAY, activePlaylistPosition);
        }
    }

    private void fadeIntoNewSong(Song currentSong, int position) {
        val sp = getDefaultSharedPreferences(this);
        val repeat = sp.getBoolean(Property.REPEAT_MODE, true);
        try {
            if (isPlaying()) {
                auxPlayer = new MediaPlayer();
                auxPlayer.setDataSource(getApplicationContext(), Uri.parse(currentSong.getFileUri()));
                //TODO this repeats current file only!
//                auxPlayer.setLooping(repeat);
                auxPlayer.prepare();
                auxPlayer.seekTo(position);
                auxPlayer.setVolume(0, 0);
                fadeOut(mediaPlayer);
                fadeIn(auxPlayer);
                auxPlayer.start();
                mediaPlayer = auxPlayer;
                observeEnding(mediaPlayer);
            } else {
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setDataSource(getApplicationContext(), Uri.parse(currentSong.getFileUri()));
                //TODO this repeats current file only!
                //mediaPlayer.setLooping(repeat);
                mediaPlayer.prepare();
                mediaPlayer.seekTo(position);
                mediaPlayer.setVolume(0, 0);
                fadeIn(mediaPlayer);
                mediaPlayer.start();
                observeEnding(mediaPlayer);
            }
            val msg = MessageFormat.format(getString(R.string.playlist_now_playing), currentSong.getFilename());
            Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Log.d(TAG, e.getMessage());
            val errorMsg = MessageFormat.format(getString(R.string.playlist_cant_play), currentSong);
            Toast.makeText(getBaseContext(), errorMsg, Toast.LENGTH_LONG).show();
        }
    }

    public void pause() {
        mediaPlayer.pause();
        val currentSong = activePlaylist.getCurrentSong();
        currentSong.setCurrentPosition(mediaPlayer.getCurrentPosition());
    }

    public void stop() {
        if (isPlaying()) {
            val currentSong = activePlaylist.getCurrentSong();
            currentSong.saveAsync();
            mediaPlayer.reset();
            mediaPlayer.release();
            populateAndSend(EventType.PLAYLIST_NOTIFICATION_STOP, activePlaylistPosition);
        }
    }

    public void next() {
        if (activePlaylist == null) {
            Toast.makeText(this, getString(R.string.playlist_no_active_playlist), Toast.LENGTH_LONG).show();
        } else {
            //TODO change to random playlist
            // val nextSong = new SecureRandom().nextInt(activePlaylist.getSongs().size());

            // get current song index and increase ,
            // if no song found it will be 0 as indexOf returns -1 in that case
            var songIndex = activePlaylist.getSongs().indexOf(activePlaylist.getCurrentSong()) + 1;
            if (songIndex > activePlaylist.getSongs().size() - 1) {
                songIndex = 0;
            }
            val song = activePlaylist.getSongs().get(songIndex);
            activePlaylist.setCurrentSong(song);
            activePlaylist.save();
            fadeIntoNewSong(song, 0);
            populateAndSend(EventType.PLAYLIST_NOTIFICATION_NEXT, activePlaylistPosition);
        }
    }

    public void previous() {
        if (activePlaylist == null) {
            Toast.makeText(this, getString(R.string.playlist_no_active_playlist), Toast.LENGTH_LONG).show();
        } else {
            val songs = activePlaylist.getSongs();
            val lastSongIndex = songs.size() - 1;
            //if this was first song we will loop around to last song in playlist
            var songIndex = songs.indexOf(activePlaylist.getCurrentSong()) - 1;
            if (songIndex < 0) {
                songIndex = lastSongIndex;
            }
            val song = songs.get(songIndex);
            activePlaylist.setCurrentSong(song);
            activePlaylist.save();
            fadeIntoNewSong(song, 0);
            populateAndSend(EventType.PLAYLIST_NOTIFICATION_PREV, activePlaylistPosition);
        }
    }

    /**
     * Check if there is activePlaylist and is mediaPlayer playing
     * There might be IllegalState exception which means media player was not initialized or already released
     *
     * @return true if there is music playing
     */
    public boolean isPlaying() {
        try {
            return activePlaylist != null && mediaPlayer.isPlaying();
        } catch (IllegalStateException e) {
            Log.d(TAG, "media player is definitely not playing");
            return false;
        }
    }

    private void fadeOut(final MediaPlayer player) {
        val deviceVolume = getDeviceVolume();
        val h = new Handler();
        h.postDelayed(new Runnable() {
            final int duration = getDuration();
            private float time = duration;

            @Override
            public void run() {
                try {
                    time -= 100;
                    float volume = (deviceVolume * time) / duration;
                    player.setVolume(volume, volume);
                    if (time > 0)
                        h.postDelayed(this, 100);
                    else {
                        player.reset();
                        player.release();
                    }
                } catch (IllegalStateException e) {
                    player.reset();
                    player.release();
                }
            }
        }, 100);
    }

    private void fadeIn(final MediaPlayer player) {
        val deviceVolume = getDeviceVolume();
        val h = new Handler();
        h.postDelayed(new Runnable() {
            private float time = 0.0f;
            final int duration = getDuration();

            @Override
            public void run() {
                try {
                    time += 100;
                    float volume = (deviceVolume * time) / duration;
                    player.setVolume(volume, volume);
                    if (time < duration)
                        h.postDelayed(this, 100);
                } catch (IllegalStateException e) {
                    player.reset();
                    player.release();
                }
            }
        }, 100);

    }

    void observeEnding(final MediaPlayer player) {
        val h = new Handler();
        h.postDelayed(new Runnable() {
            final int duration = getDuration();

            @Override
            public void run() {
                try {
                    if(player.isPlaying()){
                        val currentPosition = player.getCurrentPosition();
                        val totalDuration = player.getDuration() - duration; //take total duration - fade
                        if (currentPosition < totalDuration) {
                            h.postDelayed(this, 100);
                        } else {
                            next();
                        }
                    }
                } catch (IllegalStateException e) {
                    Log.d(TAG, "Ending observing ending as transition to next song is already in progress and media player is released");
                }

            }
        }, 100);
    }

    public float getDeviceVolume() {
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        int volumeLevel = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        return (float) volumeLevel / maxVolume;
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
