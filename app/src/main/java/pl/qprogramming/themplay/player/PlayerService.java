package pl.qprogramming.themplay.player;

import static androidx.preference.PreferenceManager.getDefaultSharedPreferences;
import static pl.qprogramming.themplay.playlist.EventType.PLAYBACK_NOTIFICATION_DELETE_NOT_FOUND;
import static pl.qprogramming.themplay.playlist.EventType.PLAYBACK_NOTIFICATION_NEXT;
import static pl.qprogramming.themplay.playlist.EventType.PLAYBACK_NOTIFICATION_PAUSE;
import static pl.qprogramming.themplay.playlist.EventType.PLAYBACK_NOTIFICATION_PLAY;
import static pl.qprogramming.themplay.playlist.EventType.PLAYBACK_NOTIFICATION_PREV;
import static pl.qprogramming.themplay.playlist.EventType.PLAYBACK_NOTIFICATION_STOP;
import static pl.qprogramming.themplay.playlist.EventType.PLAYLIST_NOTIFICATION_ACTIVE;
import static pl.qprogramming.themplay.playlist.EventType.PLAYLIST_NOTIFICATION_ADD;
import static pl.qprogramming.themplay.playlist.EventType.PLAYLIST_NOTIFICATION_DELETE;
import static pl.qprogramming.themplay.playlist.EventType.PLAYLIST_NOTIFICATION_DELETE_SONGS;
import static pl.qprogramming.themplay.playlist.EventType.PLAYLIST_NOTIFICATION_NEW_ACTIVE;
import static pl.qprogramming.themplay.playlist.EventType.PLAYLIST_NOTIFICATION_RECREATE_LIST;
import static pl.qprogramming.themplay.playlist.EventType.PRESET_ACTIVATED;
import static pl.qprogramming.themplay.util.Utils.ARGS;
import static pl.qprogramming.themplay.util.Utils.PLAYLIST;
import static pl.qprogramming.themplay.util.Utils.createPlaylist;
import static pl.qprogramming.themplay.util.Utils.isEmpty;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Optional;

import lombok.Setter;
import lombok.val;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.domain.Playlist;
import pl.qprogramming.themplay.domain.Song;
import pl.qprogramming.themplay.logger.Logger;
import pl.qprogramming.themplay.playlist.EventType;
import pl.qprogramming.themplay.playlist.PlaylistService;
import pl.qprogramming.themplay.settings.Property;
import pl.qprogramming.themplay.util.Utils;

/**
 * Service responsible for music playback.
 * <p>
 * This service manages the playback of songs from a playlist. It utilizes two MediaPlayers
 * to achieve seamless transitions between songs through fade-in and fade-out effects.
 * One MediaPlayer handles the currently playing song, while the auxiliary MediaPlayer (auxPlayer)
 * is used to prepare and fade in the next song.
 */
public class PlayerService extends Service {

    private static final String TAG = PlayerService.class.getSimpleName();
    private Playlist activePlaylist;
    @Setter
    private ProgressBar progressBar;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());

    private PlaylistService playlistService;
    private boolean serviceIsBound;

    private final IBinder mBinder = new PlayerService.LocalBinder();
    private MediaPlayer mediaPlayer = new MediaPlayer();
    private MediaPlayer auxPlayer;
    private MediaNotificationManager mNotificationManager;


    @Override
    public void onCreate() {
        super.onCreate();
        val playlistServiceIntent = new Intent(this, PlaylistService.class);
        bindService(playlistServiceIntent, playlistServiceConnection, Context.BIND_AUTO_CREATE);
        mNotificationManager = new MediaNotificationManager(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            Logger.d(TAG, "onStartCommand received action: " + action);
            if (PLAYBACK_NOTIFICATION_PLAY.getCode().equals(action)) {
                play();
            } else if (PLAYBACK_NOTIFICATION_PAUSE.getCode().equals(action)) {
                pause();
            } else if (PLAYBACK_NOTIFICATION_NEXT.getCode().equals(action)) {
                next();
            } else if (PLAYBACK_NOTIFICATION_PREV.getCode().equals(action)) {
                previous();
            } else if (PLAYBACK_NOTIFICATION_STOP.getCode().equals(action)) {
                stop();
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Logger.d(TAG, "onDestroy");
        super.onDestroy();
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
            if (serviceIsBound) {
                unbindService(playlistServiceConnection);
                serviceIsBound = false;
            }
            if (mediaPlayer != null && isPlaying()) {
                mediaPlayer.stop();
            }
        } catch (IllegalArgumentException e) {
            Logger.d(TAG, "Receiver not registered");
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Logger.d(TAG, "Binding service to " + intent + "this:" + this);
        val filter = new IntentFilter(PLAYLIST_NOTIFICATION_NEW_ACTIVE.getCode());
        filter.addAction(PLAYLIST_NOTIFICATION_ACTIVE.getCode());
        filter.addAction(PLAYLIST_NOTIFICATION_DELETE.getCode());
        filter.addAction(PLAYLIST_NOTIFICATION_DELETE_SONGS.getCode());
        filter.addAction(PLAYBACK_NOTIFICATION_PLAY.getCode());
        filter.addAction(PLAYBACK_NOTIFICATION_NEXT.getCode());
        filter.addAction(PLAYBACK_NOTIFICATION_PREV.getCode());
        filter.addAction(PLAYBACK_NOTIFICATION_PAUSE.getCode());
        filter.addAction(PLAYBACK_NOTIFICATION_STOP.getCode());
        filter.addAction(PLAYLIST_NOTIFICATION_ADD.getCode());
        filter.addAction(PLAYLIST_NOTIFICATION_RECREATE_LIST.getCode());
        filter.addAction(PRESET_ACTIVATED.getCode());
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);
        Logger.d(TAG, "Returning binder , player is playing ? " + mediaPlayer.isPlaying());
        return mBinder;
    }


    private void fadeIntoNewPlaylist(Playlist playlist) {
        if (isPlaying()) {
            val currentSong = activePlaylist.getCurrentSong();
            currentSong.setCurrentPosition(mediaPlayer.getCurrentPosition());
            playlistService.updateSong(currentSong);
        }
        activePlaylist = playlist;
        val song = activePlaylist.getCurrentSong();
        if (song != null) {
            fadeIntoNewSong(song, song.getCurrentPosition());
        }
    }


    public class LocalBinder extends Binder {
        public PlayerService getService() {
            return PlayerService.this;
        }
    }

    private boolean isFadeStop() {
        val sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        return sp.getBoolean(Property.FADE_STOP, false);
    }

    private int getDuration() {
        val sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        return Integer.parseInt(sp.getString(Property.FADE_DURATION, "4")) * 1000;
    }

    /**
     * Plays current playlist
     */
    public void play() {
        if (activePlaylist != null) {
            val currentSong = activePlaylist.getCurrentSong();
            fadeIntoNewSong(currentSong, currentSong.getCurrentPosition());
            populateAndSend(EventType.PLAYLIST_NOTIFICATION_PLAY, activePlaylist.getPosition());
        } else {
            Toast.makeText(getApplicationContext(), getString(R.string.playlist_no_active_playlist), Toast.LENGTH_LONG).show();
            populateAndSend(PLAYBACK_NOTIFICATION_STOP, 0);
        }
    }


    public void pause() {
        mediaPlayer.pause();
        val currentSong = activePlaylist.getCurrentSong();
        currentSong.setCurrentPosition(mediaPlayer.getCurrentPosition());
        playlistService.updateSong(currentSong);
        mNotificationManager.createMediaNotification(currentSong, activePlaylist.getName(), true);
        progressHandler.removeCallbacks(updateProgressTask);
    }

    public void stop() {
        mNotificationManager.removeNotification();
        Logger.d(TAG, "Stop media player");
        if (isPlaying()) {
            if (activePlaylist != null) {
                val currentSong = activePlaylist.getCurrentSong();
                if (currentSong != null) {
                    playlistService.updateSong(currentSong);
                }
            }
            if (isFadeStop()) {
                fadeOut(mediaPlayer);
            } else {
                mediaPlayer.reset();
                mediaPlayer.release();
            }
            progressHandler.removeCallbacks(updateProgressTask);
        }
    }

    public void next() {
        val sp = getDefaultSharedPreferences(this);
        val shuffle = sp.getBoolean(Property.SHUFFLE_MODE, true);
        if (isEmpty(activePlaylist.getPlaylist())) {
            createPlaylist(activePlaylist, shuffle);
        }
        if (activePlaylist != null && !activePlaylist.getPlaylist().isEmpty()) {
            // get current song index and increase ,
            // if no song found it will be 0 as indexOf returns -1 in that case
            var songIndex = activePlaylist.getPlaylist().indexOf(activePlaylist.getCurrentSong()) + 1;
            if (songIndex > activePlaylist.getPlaylist().size() - 1) {
                Logger.d(TAG, "Creating new playlist");
                createPlaylist(activePlaylist, shuffle);
                songIndex = 0;
            }
            val song = activePlaylist.getPlaylist().get(songIndex);
            activePlaylist.setCurrentSong(song);
            playlistService.save(activePlaylist);
            fadeIntoNewSong(song, 0);
            populateAndSend(EventType.PLAYLIST_NOTIFICATION_NEXT, activePlaylist.getPosition());
        } else {
            Toast.makeText(getApplicationContext(), getString(R.string.playlist_no_active_playlist), Toast.LENGTH_LONG).show();
            populateAndSend(PLAYBACK_NOTIFICATION_STOP, 0);
        }
    }

    public void previous() {
        if (activePlaylist != null) {
            if (isEmpty(activePlaylist.getPlaylist())) {
                val sp = getDefaultSharedPreferences(this);
                val shuffle = sp.getBoolean(Property.SHUFFLE_MODE, true);
                createPlaylist(activePlaylist, shuffle);
            }
            val songs = activePlaylist.getPlaylist();
            val lastSongIndex = songs.size() - 1;
            //if this was first song we will loop around to last song in playlist
            var songIndex = songs.indexOf(activePlaylist.getCurrentSong()) - 1;
            if (songIndex < 0) {
                songIndex = lastSongIndex;
            }
            val song = songs.get(songIndex);
            activePlaylist.setCurrentSong(song);
            playlistService.save(activePlaylist);
            fadeIntoNewSong(song, 0);
            populateAndSend(EventType.PLAYLIST_NOTIFICATION_PREV, activePlaylist.getPosition());
        } else {
            Toast.makeText(getApplicationContext(), getString(R.string.playlist_no_active_playlist), Toast.LENGTH_LONG).show();
            populateAndSend(PLAYBACK_NOTIFICATION_STOP, 0);
        }
    }


    /**
     * Fades into new song
     *
     * @param nextSong     current song playing/active
     * @param songPosition from where next song should pickup
     */
    private void fadeIntoNewSong(Song nextSong, int songPosition) {
        mNotificationManager.createMediaNotification(nextSong, activePlaylist.getName(), false);
        Logger.d(TAG, "Fading into song from " + nextSong.getFilename());
        try {
            val uri = Uri.parse(nextSong.getFileUri());
            Logger.d(TAG, "Opening file " + uri.toString());
            if (isPlaying()) {
                Logger.d(TAG, "Already playing something, creating secondary player to fade in");
                auxPlayer = new MediaPlayer();
                auxPlayer.setDataSource(this, uri);
                auxPlayer.prepare();
                auxPlayer.seekTo(songPosition);
                auxPlayer.setVolume(0, 0);
                Logger.d(TAG, "Fading out current song");
                fadeOut(mediaPlayer);
                Logger.d(TAG, "Fading in new song");
                fadeIn(auxPlayer);
                Logger.d(TAG, "Starting player");
                auxPlayer.start();
                mediaPlayer = auxPlayer;
            } else {
                Logger.d(TAG, "Nothing is playing yet, creating new player");
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setDataSource(this, uri);
                mediaPlayer.prepare();
                mediaPlayer.seekTo(songPosition);
                mediaPlayer.setVolume(0, 0);
                Logger.d(TAG, "Fading in new song");
                fadeIn(mediaPlayer);
                Logger.d(TAG, "Starting player");
                mediaPlayer.start();
            }
            observeEnding(mediaPlayer);
            progressBar.setProgress(songPosition);
            updateProgressBar();
            val msg = MessageFormat.format(getString(R.string.playlist_now_playing), nextSong.getFilename());
            Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
        } catch (IOException | SecurityException e) {
            Logger.e(TAG, "Error playing song: " + nextSong.getFilename(), e);
            if (e.getMessage() != null) { // Good practice to check if getMessage() is null
                Logger.d(TAG, "Error details: " + e.getMessage());
            }
            String errorMsg = MessageFormat.format(getString(R.string.playlist_cant_play), nextSong.getFilename());
            Toast.makeText(getBaseContext(), errorMsg, Toast.LENGTH_LONG).show();
            if (activePlaylist != null) {
                activePlaylist.setCurrentSong(null);
                boolean removedFromCurrentSequence;
                if (activePlaylist.getPlaylist() != null) {
                    removedFromCurrentSequence = activePlaylist.getPlaylist().remove(nextSong);
                    if (removedFromCurrentSequence) {
                        Logger.d(TAG, "Problematic song '" + nextSong.getFilename() + "' removed from current playback sequence (getPlaylist).");
                    } else {
                        Logger.w(TAG, "Problematic song '" + nextSong.getFilename() + "' NOT found in current playback sequence (getPlaylist).");
                    }
                } else {
                    Logger.w(TAG, "activePlaylist.getPlaylist() was null. Cannot remove problematic song from sequence.");
                }
                boolean removedFromMasterList;
                if (activePlaylist.getSongs() != null) {
                    removedFromMasterList = activePlaylist.getSongs().remove(nextSong);
                    if (removedFromMasterList) {
                        activePlaylist.setSongCount(activePlaylist.getSongCount() - 1);
                        Logger.d(TAG, "Problematic song '" + nextSong.getFilename() + "' removed from master song list (getSongs).");
                    } else {
                        Logger.w(TAG, "Problematic song '" + nextSong.getFilename() + "' NOT found in master song list (getSongs).");
                    }
                } else {
                    Logger.w(TAG, "activePlaylist.getSongs() was null. Cannot remove problematic song from master list.");
                }
            } else {
                Logger.w(TAG, "activePlaylist or nextSong was null. Cannot perform direct removal.");
            }
            Intent intent = new Intent(PLAYBACK_NOTIFICATION_DELETE_NOT_FOUND.getCode());
            Bundle args = new Bundle();
            args.putSerializable(Utils.SONG, nextSong);
            args.putSerializable(PLAYLIST, activePlaylist); // Pass the context of the playlist
            intent.putExtra(ARGS, args);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
            next();
        }
    }


    public boolean isActivePlaylist(Playlist playlist) {
        return playlist.equals(activePlaylist);
    }


    /**
     * Check if there is activePlaylist and is mediaPlayer playing
     * There might be IllegalState exception which means media player was not initialized or already released
     *
     * @return true if there is music playing
     */
    public boolean isPlaying() {
        try {
            return mediaPlayer.isPlaying();
        } catch (IllegalStateException e) {
            Logger.d(TAG, "media player is definitely not playing");
            return false;
        }
    }

    private void fadeOut(final MediaPlayer player) {
        val deviceVolume = getDeviceVolume();
        val h = new Handler(getMainLooper());
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
                    Logger.d(TAG, "FadeOut stopped due to error in MediaPlayer");
                }
            }
        }, 100);
    }

    private void updateProgressBar() {
        progressHandler.postDelayed(updateProgressTask, 100);
    }


    private final Runnable updateProgressTask = new Runnable() {
        public void run() {
            val totalDuration = mediaPlayer.getDuration();
            val currentDuration = mediaPlayer.getCurrentPosition();
            val progress = getProgressPercentage(currentDuration, totalDuration);
            progressBar.setProgress(progress);
            progressHandler.postDelayed(this, 100);
        }
    };


    private void fadeIn(final MediaPlayer player) {
        val deviceVolume = getDeviceVolume();
        val h = new Handler(getMainLooper());
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
                    Logger.d(TAG, "FadeIn stopped due to error in MediaPlayer");
                }
            }
        }, 100);

    }

    void observeEnding(final MediaPlayer player) {
        val h = new Handler(getMainLooper());
        h.postDelayed(new Runnable() {
            final int duration = getDuration();

            @Override
            public void run() {
                try {
                    if (player.isPlaying()) {
                        val currentPosition = player.getCurrentPosition();
                        val totalDuration = player.getDuration() - duration; //take total duration - fade
                        if (currentPosition < totalDuration) {
                            h.postDelayed(this, 500);
                        } else {
                            next();
                        }
                    }
                } catch (IllegalStateException e) {
                    Logger.d(TAG, "Ending observing ending as transition to next song is already in progress and media player is released");
                }

            }
        }, 500);
    }

    public int getProgressPercentage(long currentDuration, long totalDuration) {
        val currentSeconds = (int) (currentDuration / 1000);
        val totalSeconds = (int) (totalDuration / 1000);
        return (int) ((((double) currentSeconds) / totalSeconds) * 100);
    }


    private float getDeviceVolume() {
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        int volumeLevel = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        return (float) volumeLevel / maxVolume;
    }

    private void populateAndSend(EventType type, int position) {
        Intent intent = new Intent(type.getCode());
        val args = new Bundle();
        args.putSerializable(Utils.POSITION, position);
        intent.putExtra(ARGS, args);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Logger.d(TAG, "Received event for playback " + intent.getAction());
            val event = EventType.getType(intent.getAction());
            Bundle args = intent.getBundleExtra(ARGS);
            val sp = getDefaultSharedPreferences(context);
            val shuffle = sp.getBoolean(Property.SHUFFLE_MODE, true);
            switch (event) {
                case PLAYBACK_NOTIFICATION_NEXT:
                    next();
                    break;
                case PLAYBACK_NOTIFICATION_PREV:
                    previous();
                    break;
                case PLAYBACK_NOTIFICATION_PLAY:
                    play();
                    break;
                case PLAYBACK_NOTIFICATION_STOP:
                    stop();
                    break;
                case PLAYBACK_NOTIFICATION_PAUSE:
                    pause();
                    break;
                case PRESET_ACTIVATED:
                    stop();
                    activePlaylist = null;
                    break;
                case PLAYLIST_NOTIFICATION_ADD:
                    if (args != null) {
                        Optional.ofNullable(args.getSerializable(PLAYLIST))
                                .ifPresent(playlist -> {
                                    if (playlist.equals(activePlaylist)) {
                                        activePlaylist = (Playlist) playlist;
                                    }
                                });
                    }
                    break;
                case PLAYLIST_NOTIFICATION_ACTIVE:
                    if (args != null) {
                        Optional.ofNullable(args.getSerializable(PLAYLIST))
                                .ifPresent((playlist -> fadeIntoNewPlaylist((Playlist) playlist)));
                    }
                    break;
                case PLAYLIST_NOTIFICATION_NEW_ACTIVE:
                    if (args != null) {
                        Optional.ofNullable(args.getSerializable(PLAYLIST))
                                .ifPresent(playlist -> activePlaylist = (Playlist) playlist);
                    }
                    break;
                case PLAYLIST_NOTIFICATION_RECREATE_LIST:
                    createPlaylist(activePlaylist, shuffle);
                    break;
                case PLAYLIST_NOTIFICATION_DELETE:
                    if (args != null) {
                        Optional.ofNullable(args.getSerializable(PLAYLIST))
                                .ifPresent(playlist -> {
                                    if (playlist.equals(activePlaylist)) {
                                        populateAndSend(PLAYBACK_NOTIFICATION_STOP, activePlaylist.getPosition());
                                        activePlaylist = null;
                                    }
                                });
                    }
                    break;
                case PLAYLIST_NOTIFICATION_DELETE_SONGS:
                    handleSongDeleted(args, shuffle);
                    break;
            }
        }
    };

    private void handleSongDeleted(Bundle args, boolean shuffle) {
        if (args != null) {
            Optional.ofNullable(args.getSerializable(PLAYLIST))
                    .ifPresent(object -> {
                        val playlist = (Playlist) object;
                        if (activePlaylist != null && playlist.getId().equals(activePlaylist.getId())) {
                            activePlaylist = playlist;
                            createPlaylist(activePlaylist, shuffle);
                            if (activePlaylist.getSongs().isEmpty()) {
                                populateAndSend(PLAYBACK_NOTIFICATION_STOP, activePlaylist.getPosition());
                            } else if (activePlaylist.getCurrentSong() == null && !activePlaylist.getSongs().isEmpty()) {
                                populateAndSend(PLAYBACK_NOTIFICATION_NEXT, activePlaylist.getPosition());
                            }
                        }
                    });
        }
    }


    private final ServiceConnection playlistServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            val binder = (PlaylistService.LocalBinder) service;
            playlistService = binder.getService();
            serviceIsBound = true;
        }

        public void onServiceDisconnected(ComponentName className) {
            playlistService = null;
        }
    };


}
