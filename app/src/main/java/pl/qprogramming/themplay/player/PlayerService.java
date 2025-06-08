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
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.util.EventLogger;
import androidx.preference.PreferenceManager;

import org.jetbrains.annotations.NotNull;

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
@UnstableApi
public class PlayerService extends Service {
    private static final long CROSSFADE_INTERVAL_MS = 50; // Smoother fade steps

    private static final String TAG = PlayerService.class.getSimpleName();
    private Playlist activePlaylist;
    @Setter
    private ProgressBar progressBar;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());

    private PlaylistService playlistService;
    private boolean serviceIsBound;

    private final IBinder mBinder = new PlayerService.LocalBinder();
    private PlayerServiceCallbacks mClientCallbacks;
//    private MediaPlayer mediaPlayer = new MediaPlayer();
//    private MediaPlayer auxPlayer;


    // Add these to your class variables
    private ExoPlayer currentPlayer;
    private ExoPlayer nextPlayer;
    private Handler crossfadeHandler = new Handler(Looper.getMainLooper());

    private VolumeScalingAudioProcessor mainVolumeProcessor;
    private VolumeScalingAudioProcessor nextVolumeProcessor;

    private MediaNotificationManager mNotificationManager;


    @Override
    public void onCreate() {
        super.onCreate();
        val playlistServiceIntent = new Intent(this, PlaylistService.class);
        bindService(playlistServiceIntent, playlistServiceConnection, Context.BIND_AUTO_CREATE);
        mNotificationManager = new MediaNotificationManager(this);
    }

    /**
     * Listens to intent coming from Notifications which are targeting this service directly
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            Logger.d(TAG, "[NOTIFICATION] Player service received action: " + intent.getAction());
            val action = EventType.getType(intent.getAction());
            if (PLAYBACK_NOTIFICATION_PLAY.equals(action)) {
                play();
            } else if (PLAYBACK_NOTIFICATION_PAUSE.equals(action)) {
                pause();
            } else if (PLAYBACK_NOTIFICATION_NEXT.equals(action)) {
                next();
            } else if (PLAYBACK_NOTIFICATION_PREV.equals(action)) {
                previous();
            } else if (PLAYBACK_NOTIFICATION_STOP.equals(action)) {
                stop();
            }
            notifyClientPlaybackStateChanged(action);
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
            if (crossfadeHandler != null) {
                crossfadeHandler.removeCallbacksAndMessages(null);
            }
            if (nextPlayer != null) {
                nextPlayer.release();
                nextPlayer = null;
            }
            if (currentPlayer != null) {
                currentPlayer.release();
                currentPlayer = null;
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
        Logger.d(TAG, "Returning binder , player is playing ? " + isPlaying());
        return mBinder;
    }

    public void ensureAudioProcessorsInitialized() {
        if (mainVolumeProcessor == null) {
            mainVolumeProcessor = new VolumeScalingAudioProcessor();
            Logger.d(TAG, "Initialized mainVolumeProcessor");
        }
    }

    private ExoPlayer buildPlayerWithProcessor(VolumeScalingAudioProcessor processorToUse) {
        if (processorToUse == null) {
            Logger.e(TAG, "buildPlayerWithProcessor called with a null processor! Creating a new one as fallback.");
            processorToUse = new VolumeScalingAudioProcessor();
        }
        processorToUse.reset();
        CustomAudioRenderersFactory renderersFactory = new CustomAudioRenderersFactory(this, processorToUse);
        val player = new ExoPlayer.Builder(this)
                .setLooper(getMainLooper())
                .setRenderersFactory(renderersFactory)
                .build();
        player.addAnalyticsListener(new EventLogger());
        return player;
    }


    private void fadeIntoNewPlaylist(Playlist playlist) {
        if (isPlaying()) {
            val currentSong = activePlaylist.getCurrentSong();
            currentSong.setCurrentPosition((int) currentPlayer.getCurrentPosition());
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

        public void setCallbacks(PlayerServiceCallbacks callbacks) {
            PlayerService.this.mClientCallbacks = callbacks;
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
     * If there is no active playlist in service , attempt to load one from db and play it , otherwise show toast msg
     */
    public void play() {
        if (activePlaylist != null) {
            val currentSong = activePlaylist.getCurrentSong();
            fadeIntoNewSong(currentSong, currentSong.getCurrentPosition());
            populateAndSend(EventType.PLAYLIST_NOTIFICATION_PLAY, activePlaylist.getPosition());
        } else {
            Logger.d(TAG, "Recived play command but no active playlist found in service, searching for one");
            playlistService.getActiveAndLoadSongs(playlist -> {
                activePlaylist = playlist;
                play();
            }, () -> {
                Toast.makeText(getApplicationContext(), getString(R.string.playlist_no_active_playlist), Toast.LENGTH_LONG).show();
                populateAndSend(PLAYBACK_NOTIFICATION_STOP, 0);
            });
        }
    }


    public void pause() {
        currentPlayer.pause();
        val currentSong = activePlaylist.getCurrentSong();
        currentSong.setCurrentPosition((int) currentPlayer.getCurrentPosition());
        playlistService.updateSong(currentSong);
        mNotificationManager.createMediaNotification(currentSong, activePlaylist.getName(), true);
        progressHandler.removeCallbacks(updateProgressTask);
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
                //fade stop current player
            } else {
                currentPlayer.stop();
                currentPlayer.release();
            }
            progressHandler.removeCallbacks(updateProgressTask);
        }
    }


    @UnstableApi
    public void fadeIntoNewSong(final Song nextSong, final int songPosition) {
        //start with notifications
        mNotificationManager.createMediaNotification(nextSong, activePlaylist.getName(), false);
        String msg = MessageFormat.format(
                getString(R.string.playlist_now_playing),
                nextSong.getFilename()
        );
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
        // Make sure mainVolumeProcessor exists
        ensureAudioProcessorsInitialized();
        Logger.d(TAG, "Fading into song: " + nextSong.getFilename());
        Uri uri = Uri.parse(nextSong.getFileUri());
        Logger.d(TAG, "Opening file: " + uri.toString());
        if (currentPlayer != null && currentPlayer.isPlaying()) {
            Logger.d(TAG, "Already playing, preparing crossfade");
            prepareNextPlayer(uri, songPosition, nextSong);
        } else {
            Logger.d(TAG, "Starting fresh playback");
            if (currentPlayer != null) {
                currentPlayer.release();
            }
            startNewPlayer(uri, songPosition, nextSong);
        }
    }

    @UnstableApi
    private void prepareNextPlayer(final Uri uri, final int position, final Song nextSongToPlay) {
        if (nextPlayer != null) {
            nextPlayer.release();
            nextPlayer = null;
        }
        if (nextVolumeProcessor == null || nextVolumeProcessor == mainVolumeProcessor) {
            nextVolumeProcessor = new VolumeScalingAudioProcessor();
            Logger.d(TAG, "Created/Replaced nextVolumeProcessor for upcoming track.");
        } else {
            nextVolumeProcessor.reset();
            Logger.d(TAG, "Reset existing distinct nextVolumeProcessor.");
        }
        nextVolumeProcessor.setVolumeFactor(0f);
        Logger.d(TAG, "Building nextPlayer with nextVolumeProcessor: " + System.identityHashCode(nextVolumeProcessor));
        nextPlayer = buildPlayerWithProcessor(nextVolumeProcessor);
        nextPlayer.setVolume(1.0f);
        //load song
        MediaItem mediaItem = MediaItem.fromUri(uri);
        nextPlayer.setMediaItem(mediaItem);
        nextPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    if (nextPlayer == null) {
                        return;
                    }
                    nextPlayer.seekTo(position);
                    nextPlayer.play();
                    startCrossfade(nextSongToPlay, position, mainVolumeProcessor, nextVolumeProcessor);
                    nextPlayer.removeListener(this);
                }
            }

            @Override
            public void onPlayerError(@NotNull PlaybackException error) {
                Player.Listener.super.onPlayerError(error);
                Logger.e(TAG, "PlayerError in currentPlayer for song: " + nextSongToPlay.getFilename(), error);
                handleWrongSong(nextSongToPlay);
            }
        });
        nextPlayer.prepare();
    }

    private void startCrossfade(final Song nextSongBeingPlayed, final int position,
                                final VolumeScalingAudioProcessor currentTrackProcessor,
                                final VolumeScalingAudioProcessor nextTrackProcessor) {

        final int fadeDurationMs = getDuration();
        // Defensive checks
        if (currentTrackProcessor == null || nextTrackProcessor == null) {
            Logger.e(TAG, "Crossfade cannot start: one or both audio processors are null.");
            if (nextPlayer != null) {
                if (currentPlayer != null) {
                    currentPlayer.stop();
                    currentPlayer.release();
                }
                currentPlayer = nextPlayer;
                mainVolumeProcessor = nextTrackProcessor;
                if (mainVolumeProcessor != null) {
                    mainVolumeProcessor.setVolumeFactor(1.0f);
                }
                nextPlayer = null;
                this.nextVolumeProcessor = null;
                observeEnding(currentPlayer, nextSongBeingPlayed);
                updateUI((int) currentPlayer.getCurrentPosition());
            }
            return;
        }
        if (currentTrackProcessor == nextTrackProcessor) {
            Logger.e(TAG, "Crossfade cannot start: current and next processors are the same instance! Performing hard switch.");
            // Handle hard switch (similar to above)
            if (nextPlayer != null) {
                if (currentPlayer != null) {
                    currentPlayer.stop();
                    currentPlayer.release();
                }
                currentPlayer = nextPlayer;
                mainVolumeProcessor = nextTrackProcessor;
                mainVolumeProcessor.setVolumeFactor(1.0f);
                nextPlayer = null;
                this.nextVolumeProcessor = null;
                observeEnding(currentPlayer, nextSongBeingPlayed);
                updateUI((int) currentPlayer.getCurrentPosition());
            }
            return;
        }
        if (fadeDurationMs <= 0) {
            Logger.d(TAG, "Crossfade: No fade duration, immediate switch.");
            if (currentPlayer != null) {
                currentPlayer.stop();
                currentPlayer.release();
            }
            currentPlayer = nextPlayer;
            mainVolumeProcessor = nextTrackProcessor;
            nextPlayer = null;
            this.nextVolumeProcessor = null;
            if (currentPlayer != null) {
                mainVolumeProcessor.setVolumeFactor(1.0f);
                observeEnding(currentPlayer, nextSongBeingPlayed);
                updateUI(position);
            }
            return;
        }
        final int steps = Math.max(1, fadeDurationMs / (int) CROSSFADE_INTERVAL_MS);
        final float volumeStepFactor = 1.0f / steps;
        Logger.d(TAG, "Starting crossfade: currentProc=" + System.identityHashCode(currentTrackProcessor) +
                ", nextProc=" + System.identityHashCode(nextTrackProcessor));
        crossfadeHandler.removeCallbacksAndMessages(null);
        crossfadeHandler.post(new Runnable() {
            int step = 0;

            @Override
            public void run() {
                if (currentPlayer == null || nextPlayer == null || currentTrackProcessor == null || nextTrackProcessor == null) {
                    Logger.w(TAG, "Crossfade aborted: Player or processor became null during fade.");
                    // Simplified cleanup: attempt to switch to nextPlayer if it's valid
                    if (nextPlayer != null && nextTrackProcessor != null) {
                        if (currentPlayer != null) {
                            currentPlayer.stop();
                            currentPlayer.release();
                        }
                        PlayerService.this.currentPlayer = nextPlayer;
                        PlayerService.this.mainVolumeProcessor = nextTrackProcessor;
                        PlayerService.this.nextPlayer = null;
                        PlayerService.this.nextVolumeProcessor = null; // Ready for next cycle
                        PlayerService.this.mainVolumeProcessor.setVolumeFactor(1.0f);
                        observeEnding(PlayerService.this.currentPlayer, nextSongBeingPlayed);
                        updateUI((int) PlayerService.this.currentPlayer.getCurrentPosition());
                    } else if (currentPlayer != null && currentTrackProcessor != null) { // Fallback to current
                        currentTrackProcessor.setVolumeFactor(1.0f);
                    }
                    return;
                }
                float nextVolFactor = Math.min(1.0f, step * volumeStepFactor);
                float currVolFactor = Math.max(0.0f, 1.0f - (step * volumeStepFactor)); // Assuming current starts at 1.0
                nextTrackProcessor.setVolumeFactor(nextVolFactor);
                currentTrackProcessor.setVolumeFactor(currVolFactor);
                if (step < steps) {
                    step++;
                    crossfadeHandler.postDelayed(this, CROSSFADE_INTERVAL_MS);
                } else {
                    Logger.d(TAG, "Crossfade complete.");
                    nextTrackProcessor.setVolumeFactor(1.0f);
                    currentTrackProcessor.setVolumeFactor(0.0f);
                    val oldPlayer = PlayerService.this.currentPlayer;
                    PlayerService.this.currentPlayer = PlayerService.this.nextPlayer;
                    PlayerService.this.mainVolumeProcessor = nextTrackProcessor; // The next track's processor is now the main one
                    PlayerService.this.nextPlayer = null;
                    PlayerService.this.nextVolumeProcessor = null; // Will be recreated in prepareNextPlayer

                    if (oldPlayer != null) {
                        oldPlayer.stop();
                        oldPlayer.release();
                        Logger.d(TAG, "Old player released after crossfade.");
                    }
                    observeEnding(PlayerService.this.currentPlayer, nextSongBeingPlayed);
                    updateUI((int) PlayerService.this.currentPlayer.getCurrentPosition());
                }
            }
        });
    }


    @UnstableApi
    private void startNewPlayer(final Uri uri, final int position, final Song songToPlay) {
        if (currentPlayer != null) {
            currentPlayer.release();
            currentPlayer = null;
        }
        if (mainVolumeProcessor == null) {
            mainVolumeProcessor = new VolumeScalingAudioProcessor();
            Logger.d(TAG, "Initialized mainVolumeProcessor for new player.");
        } else {
            mainVolumeProcessor.reset();
            Logger.d(TAG, "Reset mainVolumeProcessor for new player.");
        }
        mainVolumeProcessor.setVolumeFactor(1.0f);
        Logger.d(TAG, "Building new currentPlayer with mainVolumeProcessor: " + System.identityHashCode(mainVolumeProcessor));
        currentPlayer = buildPlayerWithProcessor(mainVolumeProcessor);
        currentPlayer.setVolume(1.0f);
        MediaItem mediaItem = MediaItem.fromUri(uri);
        currentPlayer.setMediaItem(mediaItem);
        currentPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    if (currentPlayer == null) return;
                    currentPlayer.seekTo(position);
                    currentPlayer.play();
                    observeEnding(currentPlayer, songToPlay);
                    updateUI(position);
                    currentPlayer.removeListener(this);
                }
            }

            @Override
            public void onPlayerError(@NotNull PlaybackException error) {
                Player.Listener.super.onPlayerError(error);
                Logger.e(TAG, "PlayerError in currentPlayer for song: " + songToPlay.getFilename(), error);
                handleWrongSong(songToPlay);
            }
        });
        currentPlayer.prepare();
    }

    private void observeEnding(final ExoPlayer player, final Song song) {
        final Handler h = new Handler(getMainLooper());
        final int fadeDuration = getDuration();
        Runnable endingCheck = new Runnable() {
            @Override
            public void run() {
                if (player == null || !player.isPlaying()) return;

                long trackDuration = player.getDuration();
                if (trackDuration == C.TIME_UNSET) {
                    h.postDelayed(this, 500);
                    return;
                }
                long currentPosition = player.getCurrentPosition();
                long triggerPoint = trackDuration - fadeDuration;
                if (currentPosition < triggerPoint) {
                    h.postDelayed(this, 500);
                } else {
                    next();
                }
            }
        };
        h.post(endingCheck);
    }


    private void updateUI(int position) {
        progressBar.setProgress(position);
        updateProgressBar();

    }


//    /**
//     * Fades into new song
//     *
//     * @param nextSong     current song playing/active
//     * @param songPosition from where next song should pickup
//     */
//    private void fadeIntoNewSong(Song nextSong, int songPosition) {
//        mNotificationManager.createMediaNotification(nextSong, activePlaylist.getName(), false);
//        Logger.d(TAG, "Fading into song from " + nextSong.getFilename());
//        try {
//            val uri = Uri.parse(nextSong.getFileUri());
//            Logger.d(TAG, "Opening file " + uri.toString());
//            if (isPlaying()) {
//                Logger.d(TAG, "Already playing something, creating secondary player to fade in");
//                auxPlayer = new MediaPlayer();
//                auxPlayer.setDataSource(this, uri);
//                auxPlayer.prepare();
//                auxPlayer.seekTo(songPosition);
//                auxPlayer.setVolume(0, 0);
//                Logger.d(TAG, "Fading out current song");
//                fadeOut(mediaPlayer);
//                Logger.d(TAG, "Fading in new song");
//                fadeIn(auxPlayer);
//                Logger.d(TAG, "Starting player");
//                auxPlayer.start();
//                mediaPlayer = auxPlayer;
//            } else {
//                Logger.d(TAG, "Nothing is playing yet, creating new player");
//                mediaPlayer = new MediaPlayer();
//                mediaPlayer.setDataSource(this, uri);
//                mediaPlayer.prepare();
//                mediaPlayer.seekTo(songPosition);
//                mediaPlayer.setVolume(0, 0);
//                Logger.d(TAG, "Fading in new song");
//                fadeIn(mediaPlayer);
//                Logger.d(TAG, "Starting player");
//                mediaPlayer.start();
//            }
//            observeEnding(mediaPlayer);
//            progressBar.setProgress(songPosition);
//            updateProgressBar();
//            val msg = MessageFormat.format(getString(R.string.playlist_now_playing), nextSong.getFilename());
//            Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
//        } catch (IOException | SecurityException e) {
//            Logger.e(TAG, "Error playing song: " + nextSong.getFilename(), e);
//            if (e.getMessage() != null) { // Good practice to check if getMessage() is null
//                Logger.d(TAG, "Error details: " + e.getMessage());
//            }
//            String errorMsg = MessageFormat.format(getString(R.string.playlist_cant_play), nextSong.getFilename());
//            Toast.makeText(getBaseContext(), errorMsg, Toast.LENGTH_LONG).show();
//            if (activePlaylist != null) {
//                activePlaylist.setCurrentSong(null);
//                boolean removedFromCurrentSequence;
//                if (activePlaylist.getPlaylist() != null) {
//                    removedFromCurrentSequence = activePlaylist.getPlaylist().remove(nextSong);
//                    if (removedFromCurrentSequence) {
//                        Logger.d(TAG, "Problematic song '" + nextSong.getFilename() + "' removed from current playback sequence (getPlaylist).");
//                    } else {
//                        Logger.w(TAG, "Problematic song '" + nextSong.getFilename() + "' NOT found in current playback sequence (getPlaylist).");
//                    }
//                } else {
//                    Logger.w(TAG, "activePlaylist.getPlaylist() was null. Cannot remove problematic song from sequence.");
//                }
//                boolean removedFromMasterList;
//                if (activePlaylist.getSongs() != null) {
//                    removedFromMasterList = activePlaylist.getSongs().remove(nextSong);
//                    if (removedFromMasterList) {
//                        activePlaylist.setSongCount(activePlaylist.getSongCount() - 1);
//                        Logger.d(TAG, "Problematic song '" + nextSong.getFilename() + "' removed from master song list (getSongs).");
//                    } else {
//                        Logger.w(TAG, "Problematic song '" + nextSong.getFilename() + "' NOT found in master song list (getSongs).");
//                    }
//                } else {
//                    Logger.w(TAG, "activePlaylist.getSongs() was null. Cannot remove problematic song from master list.");
//                }
//            } else {
//                Logger.w(TAG, "activePlaylist or nextSong was null. Cannot perform direct removal.");
//            }
//            Intent intent = new Intent(PLAYBACK_NOTIFICATION_DELETE_NOT_FOUND.getCode());
//            Bundle args = new Bundle();
//            args.putSerializable(Utils.SONG, nextSong);
//            args.putSerializable(PLAYLIST, activePlaylist); // Pass the context of the playlist
//            intent.putExtra(ARGS, args);
//            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
//            next();
//        }
//    }

    public void handleWrongSong(Song nextSong) {
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
            return currentPlayer != null && currentPlayer.isPlaying();
        } catch (IllegalStateException e) {
            Logger.d(TAG, "media player is definitely not playing");
            return false;
        }
    }

//    private void fadeOut(final MediaPlayer player) {
//        val deviceVolume = getDeviceVolume();
//        val h = new Handler(getMainLooper());
//        h.postDelayed(new Runnable() {
//            final int duration = getDuration();
//            private float time = duration;
//
//            @Override
//            public void run() {
//                try {
//                    time -= 100;
//                    float volume = (deviceVolume * time) / duration;
//                    player.setVolume(volume, volume);
//                    if (time > 0)
//                        h.postDelayed(this, 100);
//                    else {
//                        player.reset();
//                        player.release();
//                    }
//                } catch (IllegalStateException e) {
//                    Logger.d(TAG, "FadeOut stopped due to error in MediaPlayer");
//                }
//            }
//        }, 100);
//    }

    private void updateProgressBar() {
        progressHandler.postDelayed(updateProgressTask, 100);
    }


    private final Runnable updateProgressTask = new Runnable() {
        public void run() {
            val totalDuration = currentPlayer.getDuration();
            val currentDuration = currentPlayer.getCurrentPosition();
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

//    void observeEnding(final ExoPlayer player) {
//        val h = new Handler(getMainLooper());
//        h.postDelayed(new Runnable() {
//            final int duration = getDuration();
//
//            @Override
//            public void run() {
//                try {
//                    if (player.isPlaying()) {
//                        val currentPosition = player.getCurrentPosition();
//                        val totalDuration = player.getDuration() - duration; //take total duration - fade
//                        if (currentPosition < totalDuration) {
//                            h.postDelayed(this, 500);
//                        } else {
//                            next();
//                        }
//                    }
//                } catch (IllegalStateException e) {
//                    Logger.d(TAG, "Ending observing ending as transition to next song is already in progress and media player is released");
//                }
//
//            }
//        }, 500);
//    }

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
            Logger.d(TAG, "[EVENT] Received event " + intent.getAction());
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
                                .ifPresent(playlist -> {
                                    updateCurrentSongAndSwitchPlaylist((Playlist) playlist);
                                });
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

    private void updateCurrentSongAndSwitchPlaylist(Playlist playlist) {
        val currentSong = activePlaylist.getCurrentSong();
        currentSong.setCurrentPosition((int) currentPlayer.getCurrentPosition());
        playlistService.updateSong(currentSong);
        activePlaylist = playlist;
    }

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
                            } else if (activePlaylist.getCurrentSong() == null && !activePlaylist.getSongs().isEmpty() && isPlaying()) {
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

    private void notifyClientPlaybackStateChanged(EventType type) {
        if (mClientCallbacks != null) {
            new Handler(Looper.getMainLooper()).post(() -> {
                mClientCallbacks.onPlaybackStateChanged(type);
            });
        }
    }

    public interface PlayerServiceCallbacks {
        void onPlaybackStateChanged(EventType type);
    }

}
