package pl.qprogramming.themplay.player;

import static androidx.preference.PreferenceManager.getDefaultSharedPreferences;
import static pl.qprogramming.themplay.playlist.EventType.PLAYBACK_NOTIFICATION_DELETE_NOT_FOUND;
import static pl.qprogramming.themplay.playlist.EventType.PLAYBACK_NOTIFICATION_NEXT;
import static pl.qprogramming.themplay.playlist.EventType.PLAYBACK_NOTIFICATION_PAUSE;
import static pl.qprogramming.themplay.playlist.EventType.PLAYBACK_NOTIFICATION_PLAY;
import static pl.qprogramming.themplay.playlist.EventType.PLAYBACK_NOTIFICATION_PREV;
import static pl.qprogramming.themplay.playlist.EventType.PLAYBACK_NOTIFICATION_STOP;
import static pl.qprogramming.themplay.playlist.EventType.PLAYER_INIT_ACTION;
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
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.preference.PreferenceManager;

import java.text.MessageFormat;
import java.util.Optional;

import lombok.Setter;
import lombok.val;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.domain.Playlist;
import pl.qprogramming.themplay.domain.Song;
import pl.qprogramming.themplay.logger.Logger;
import pl.qprogramming.themplay.player.audio.AudioProcessorManager;
import pl.qprogramming.themplay.player.audio.CrossfadeController;
import pl.qprogramming.themplay.player.audio.ExoPlayerManager;
import pl.qprogramming.themplay.player.audio.VolumeScalingAudioProcessor;
import pl.qprogramming.themplay.playlist.EventType;
import pl.qprogramming.themplay.playlist.PlaylistService;
import pl.qprogramming.themplay.settings.Property;
import pl.qprogramming.themplay.util.Utils;

/**
 * Service responsible for music playback.
 * <p>
 * This service manages the playback of songs from a playlist. It utilizes two Players
 * to achieve seamless transitions between songs through fade-in and fade-out effects.
 * One Player (currentPlayer)  handles the currently playing song, while the auxiliary Player (nextPlayer)
 * is used to prepare and fade in the next song.
 *
 * @see ExoPlayerManager
 * @see CrossfadeController
 * @see AudioProcessorManager
 * @see VolumeScalingAudioProcessor
 */
@UnstableApi
public class Player extends Service {
    private static final String TAG = Player.class.getSimpleName();
    private Playlist activePlaylist;
    @Setter
    private ProgressBar progressBar;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());

    private PlaylistService playlistService;
    private boolean serviceIsBound;

    private final IBinder mBinder = new Player.LocalBinder();
    private PlayerServiceCallbacks mClientCallbacks;

    private ExoPlayer currentPlayer;
    private ExoPlayer nextPlayer;

    private VolumeScalingAudioProcessor mainVolumeProcessor;
    private VolumeScalingAudioProcessor nextVolumeProcessor;
    private CrossfadeController crossfadeController;

    private MediaNotificationManager mNotificationManager;
    private boolean isFadeStopRequested = false;

    private boolean isProgressUpdateRunning = false;

    private volatile boolean isTransitionInProgress = false;


    /**
     * Called when the service is created.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        val playlistServiceIntent = new Intent(this, PlaylistService.class);
        bindService(playlistServiceIntent, playlistServiceConnection, Context.BIND_AUTO_CREATE);
        mNotificationManager = new MediaNotificationManager(this);
        crossfadeController = new CrossfadeController();
    }

    /**
     * Listens to intent coming from Notifications which are targeting this service directly
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Logger.d(TAG, "onStartCommand");
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
            } else if (PLAYER_INIT_ACTION.equals(action)) {
                mNotificationManager.createIdleNotification();
            }
            notifyClientPlaybackStateChanged(action);
        }
        return START_STICKY;
    }

    /**
     * Called when the service is destroyed.
     * Cleans up resources and stops the service.
     */
    @Override
    public void onDestroy() {
        Logger.d(TAG, "onDestroy");
        cleanup();
        super.onDestroy();
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
            if (serviceIsBound) {
                unbindService(playlistServiceConnection);
                serviceIsBound = false;
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

    @Override
    public boolean onUnbind(Intent intent) {
        Logger.d(TAG, "Service was unbinded, player is playing ? " + isPlaying());
        return super.onUnbind(intent);
    }

    public class LocalBinder extends Binder {
        public Player getService() {
            return Player.this;
        }

        public void setCallbacks(PlayerServiceCallbacks callbacks) {
            Player.this.mClientCallbacks = callbacks;
        }
    }

    /**
     * Gets fade stop flag from settings
     */
    private boolean isFadeStop() {
        val sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        return sp.getBoolean(Property.FADE_STOP, true);
    }

    /**
     * Gets fade duration in milliseconds from settings
     */
    private int getDuration() {
        val sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        return Integer.parseInt(sp.getString(Property.FADE_DURATION, "4")) * 1000;
    }

    /**
     * Flag to prevent multiple simultaneous play requests
     */
    private volatile boolean isPlayRequested = false;

    /**
     * Plays current playlist with spam protection
     * If there is no active playlist in service, attempt to load one from db and play it, otherwise show toast msg
     */
    public void play() {
        if (isPlayRequested) {
            Logger.d(TAG, "Play request already in progress, ignoring duplicate play() request");
            return;
        }
        if (isPlaying()) {
            Logger.d(TAG, "Already playing, ignoring play() request");
            return;
        }
        Logger.d(TAG, "Starting play() request");
        try {
            if (activePlaylist != null) {
                isPlayRequested = true;
                val currentSong = activePlaylist.getCurrentSong();
                fadeIntoNewSong(currentSong, currentSong.getCurrentPosition());
                populateAndSend(EventType.PLAYLIST_NOTIFICATION_PLAY, activePlaylist.getPosition());
            } else {
                Logger.d(TAG, "Received play command but no active playlist found in service, searching for one");
                playlistService.getActiveAndLoadSongs(playlist -> {
                    try {
                        activePlaylist = playlist;
                        play();
                    } catch (Exception e) {
                        Logger.e(TAG, "Error in async play() callback", e);
                        isPlayRequested = false;
                    }
                }, () -> {
                    Toast.makeText(getApplicationContext(), getString(R.string.playlist_no_active_playlist), Toast.LENGTH_LONG).show();
                    populateAndSend(PLAYBACK_NOTIFICATION_STOP, 0);
                    isPlayRequested = false;
                });
            }
        } catch (Exception e) {
            Logger.e(TAG, "Error in play() request", e);
            isPlayRequested = false;
        }
    }

    /**
     * Pauses the current player with optional fade-out effect.
     *
     * <p>Saves the current playback position, updates the notification to show paused state,
     * and stops progress updates. If fade pause is enabled, gradually reduces volume before
     * pausing; otherwise pauses immediately.</p>
     *
     * @see #fadePauseCurrentPlayer()
     * @see #isFadeStop()
     */
    public void pause() {
        Logger.d(TAG, "Pause media player");
        if (isPlaying()) {
            Song currentSong = updateCurrentSongProgress(true);
            if (isFadeStop()) {
                fadePauseCurrentPlayer();
            } else {
                currentPlayer.pause();
            }
            mNotificationManager.createMediaNotification(currentSong, activePlaylist.getName(), true);
            stopProgressUpdates();
            isPlayRequested = false;
        }
    }

    /**
     * Plays next song in playlist with spam protection
     * Gets current song index and increases it by 1
     * If no song found it will be 0 as indexOf returns -1 in that case
     */
    public void next() {
        if (isTransitionInProgress) {
            Logger.d(TAG, "Transition already in progress, ignoring next() request");
            return;
        }
        isTransitionInProgress = true;
        Logger.d(TAG, "Starting next() transition");
        try {
            val sp = getDefaultSharedPreferences(this);
            val shuffle = sp.getBoolean(Property.SHUFFLE_MODE, true);
            if (isEmpty(activePlaylist.getPlaylist())) {
                createPlaylist(activePlaylist, shuffle);
            }
            if (activePlaylist != null && !activePlaylist.getPlaylist().isEmpty()) {
                updateCurrentSongProgress(false);
                var songIndex = activePlaylist.getPlaylist().indexOf(activePlaylist.getCurrentSong()) + 1;
                if (songIndex > activePlaylist.getPlaylist().size() - 1) {
                    Logger.d(TAG, "Creating new playlist");
                    createPlaylist(activePlaylist, shuffle);
                    songIndex = 0;
                }
                val song = activePlaylist.getPlaylist().get(songIndex);
                activePlaylist.setCurrentSong(song);
                activePlaylist.setCurrentSongId(song.getId());
                playlistService.save(activePlaylist);
                fadeIntoNewSong(song, 0);
                populateAndSend(EventType.PLAYLIST_NOTIFICATION_NEXT, activePlaylist.getPosition());
            } else {
                Toast.makeText(getApplicationContext(), getString(R.string.playlist_no_active_playlist), Toast.LENGTH_LONG).show();
                populateAndSend(PLAYBACK_NOTIFICATION_STOP, 0);
                isTransitionInProgress = false;
            }
        } catch (Exception e) {
            Logger.e(TAG, "Error in next() transition", e);
            isTransitionInProgress = false;
        }
    }

    /**
     * Plays previous song in playlist with spam protection
     */
    public void previous() {
        if (isTransitionInProgress) {
            Logger.d(TAG, "Transition already in progress, ignoring previous() request");
            return;
        }
        isTransitionInProgress = true;
        Logger.d(TAG, "Starting previous() transition");
        try {
            if (activePlaylist != null) {
                updateCurrentSongProgress(false);
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
                isTransitionInProgress = false; // Reset flag on error
            }
        } catch (Exception e) {
            Logger.e(TAG, "Error in previous() transition", e);
            isTransitionInProgress = false; // Reset flag on error
        }
    }

    /**
     * Stops the media player and cleans up resources.
     * if fade stop is set , smoothly stops playback
     */
    public void stop() {
        mNotificationManager.createIdleNotification();
        Logger.d(TAG, "Stop media player");
        if (isPlaying()) {
            updateCurrentSongProgress(true);
            if (isFadeStop()) {
                fadeStopCurrentPlayer();
            } else {
                ExoPlayerManager.safeReleasePlayer(currentPlayer);
                currentPlayer = null;
                mainVolumeProcessor = null;
            }
            stopProgressUpdates();
            isPlayRequested = false;
            isTransitionInProgress = false;
        }
    }

    /**
     * Fades into a new playlist by updating the current song position and starting the first song.
     *
     * <p>If currently playing, saves the current playback position before switching to the new playlist.
     * Then starts playing the current song of the new playlist from its saved position.</p>
     *
     * @param playlist The new playlist to fade into
     * @see #fadeIntoNewSong(Song, int)
     * @see #updateCurrentSongProgress(boolean)
     */
    private void fadeIntoNewPlaylist(Playlist playlist) {
        Logger.d(TAG, "Fading into new playlist ?" + playlist.getName());
        isTransitionInProgress = true;
        Logger.d(TAG, "Starting playlist transition to: " + playlist.getName());
        try {
            if (isPlaying()) {
                val currentSong = activePlaylist.getCurrentSong();
                currentSong.setCurrentPosition((int) currentPlayer.getCurrentPosition());
                playlistService.updateSong(currentSong);
            }
            activePlaylist = playlist;
            val song = activePlaylist.getCurrentSong();
            if (song != null) {
                fadeIntoNewSong(song, song.getCurrentPosition());
            } else {
                Logger.w(TAG, "New playlist has no current song");
                isTransitionInProgress = false;
            }
        } catch (Exception e) {
            Logger.e(TAG, "Error in playlist transition", e);
            isTransitionInProgress = false;
        }
    }


    /**
     * Performs a fade-out stop of the current player with proper resource cleanup.
     *
     * <p>Uses the crossfade controller to gradually reduce volume to zero before
     * releasing the player and processor resources. Sets a flag to prevent new
     * song requests during the fade-out process.</p>
     *
     * @see CrossfadeController#startFadeOut(ExoPlayer, VolumeScalingAudioProcessor, int, Runnable)
     */
    private void fadeStopCurrentPlayer() {
        if (currentPlayer == null || mainVolumeProcessor == null) {
            return;
        }
        isFadeStopRequested = true;
        crossfadeController.startFadeOut(currentPlayer, mainVolumeProcessor, getDuration(), () -> {
            currentPlayer = null;
            mainVolumeProcessor = null;
            isFadeStopRequested = false;
        });
    }

    /**
     * Performs a fade-out pause of the current player without resource cleanup.
     *
     * <p>Uses the crossfade controller to gradually reduce volume to zero before
     * pausing the player. Unlike fadeStopCurrentPlayer, this preserves the player
     * and processor resources for potential resume operations. Sets a flag to prevent
     * new song requests during the fade-out process.</p>
     *
     * @see CrossfadeController#startFadeOut(ExoPlayer, VolumeScalingAudioProcessor, int, Runnable)
     * @see #fadeStopCurrentPlayer()
     */
    private void fadePauseCurrentPlayer() {
        if (currentPlayer == null || mainVolumeProcessor == null) {
            return;
        }
        isFadeStopRequested = true;
        crossfadeController.startFadeOut(currentPlayer, mainVolumeProcessor, getDuration(), () -> {
            if (currentPlayer != null) {
                currentPlayer.pause();
            }
            isFadeStopRequested = false;
        });
    }


    /**
     * Initiates a smooth transition to a new song with crossfade or fade-in effects.
     *
     * <p>Determines whether to perform a crossfade (if currently playing) or a fresh
     * start with fade-in. Ignores requests if a fade-stop is in progress.</p>
     *
     * @param nextSong     The song to transition to
     * @param songPosition The position in milliseconds to start playback from
     * @see #prepareNextPlayer(Uri, int, Song)
     * @see #startNewPlayer(Uri, int, Song)
     * @see CrossfadeController
     */
    @UnstableApi
    public void fadeIntoNewSong(final Song nextSong, final int songPosition) {
        if (isFadeStopRequested) {
            Logger.d(TAG, "Fade stop in progress, ignoring new song request");
            isTransitionInProgress = false;
            return;
        }
        updateNotificationAndUI(nextSong);
        ensureAudioProcessorsInitialized();
        Uri uri = Uri.parse(nextSong.getFileUri());
        Logger.d(TAG, "Fading into song: " + nextSong.getFilename());
        if (isPlaying()) {
            Logger.d(TAG, "Already playing, preparing crossfade");
            prepareNextPlayer(uri, songPosition, nextSong);
        } else {
            Logger.d(TAG, "Starting fresh playback");
            startNewPlayer(uri, songPosition, nextSong);
        }
    }

    /**
     * Prepares the next player for crossfade operation with a new song.
     *
     * <p>Creates and configures a secondary ExoPlayer with its own volume processor,
     * then initiates the crossfade when the player is ready.</p>
     *
     * @param uri      The URI of the next song
     * @param position The playback position to start from
     * @param nextSong The Song object for the next track
     * @see ExoPlayerManager#createPlayerWithProcessor(Context, VolumeScalingAudioProcessor)
     * @see AudioProcessorManager#createProcessor(float)
     */
    @UnstableApi
    private void prepareNextPlayer(final Uri uri, final int position, final Song nextSong) {
        ExoPlayerManager.safeReleasePlayer(nextPlayer);
        nextPlayer = null;
        if (nextVolumeProcessor == null || nextVolumeProcessor == mainVolumeProcessor) {
            nextVolumeProcessor = AudioProcessorManager.createProcessor(0f);
        } else {
            AudioProcessorManager.resetProcessor(nextVolumeProcessor, 0f);
        }
        nextPlayer = ExoPlayerManager.createPlayerWithProcessor(this, nextVolumeProcessor);
        ExoPlayerManager.preparePlayer(nextPlayer, uri, position, nextSong,
                player -> startCrossfade(nextSong),
                this::handlePlayerError
        );
    }

    /**
     * Starts a new player with fade-in effect when no audio is currently playing.
     *
     * <p>Creates a fresh ExoPlayer instance with volume processor and applies
     * a fade-in transition from silence to full volume.</p>
     *
     * @param uri        The URI of the song to play
     * @param position   The playback position to start from
     * @param songToPlay The Song object for the track
     * @see ExoPlayerManager#createPlayerWithProcessor(Context, VolumeScalingAudioProcessor)
     * @see CrossfadeController#startFadeIn(VolumeScalingAudioProcessor, int, Runnable)
     */
    @UnstableApi
    private void startNewPlayer(final Uri uri, final int position, final Song songToPlay) {
        ExoPlayerManager.safeReleasePlayer(currentPlayer);
        currentPlayer = null;
        if (mainVolumeProcessor == null) {
            mainVolumeProcessor = AudioProcessorManager.createProcessor(0.0f);
        } else {
            AudioProcessorManager.resetProcessor(mainVolumeProcessor, 0.0f);
        }
        currentPlayer = ExoPlayerManager.createPlayerWithProcessor(this, mainVolumeProcessor);
        ExoPlayerManager.preparePlayer(currentPlayer, uri, position, songToPlay,
                player -> {
                    crossfadeController.startFadeIn(mainVolumeProcessor, getDuration(), () -> {
                        Logger.d(TAG, "Fade-in complete for new player");
                        isPlayRequested = false;
                        isTransitionInProgress = false;
                    });
                    observeEnding(songToPlay);
                    startProgressUpdates();
                },
                this::handlePlayerError
        );
    }

    /**
     * Initiates the crossfade transition between current and next players.
     *
     * @param nextSong The song being faded into
     */
    private void startCrossfade(final Song nextSong) {
        AudioProcessorManager.ProcessorPair processors =
                new AudioProcessorManager.ProcessorPair(mainVolumeProcessor, nextVolumeProcessor);
        crossfadeController.startCrossfade(getDuration(), processors, currentPlayer, nextPlayer,
                new CrossfadeController.CrossfadeCallback() {
                    @Override
                    public void onCrossfadeComplete(ExoPlayer newCurrentPlayer, VolumeScalingAudioProcessor newMainProcessor) {
                        // Swap players
                        ExoPlayerManager.safeReleasePlayer(currentPlayer);
                        currentPlayer = newCurrentPlayer;
                        mainVolumeProcessor = newMainProcessor;
                        nextPlayer = null;
                        nextVolumeProcessor = null;
                        observeEnding(nextSong);
                        startProgressUpdates();
                        isTransitionInProgress = false;
                        isPlayRequested = false;
                    }

                    @Override
                    public void onCrossfadeAborted() {
                        performHardSwitch(nextSong);
                        isPlayRequested = false;
                        isTransitionInProgress = false;
                    }
                }
        );
    }


    /**
     * Performs an immediate switch to the next player without crossfade.
     *
     * <p>Used as a fallback when crossfade operations fail. Swaps players
     * immediately and sets full volume on the new player.</p>
     *
     * @see AudioProcessorManager#safeSetVolume(VolumeScalingAudioProcessor, float)
     */
    private void performHardSwitch(Song nextSong) {
        if (nextPlayer != null && nextVolumeProcessor != null) {
            ExoPlayerManager.safeReleasePlayer(currentPlayer);
            currentPlayer = nextPlayer;
            mainVolumeProcessor = nextVolumeProcessor;
            nextPlayer = null;
            nextVolumeProcessor = null;
            AudioProcessorManager.safeSetVolume(mainVolumeProcessor, 1.0f);
            observeEnding(nextSong);
            startProgressUpdates();
        }
    }

    /**
     * Handles player errors by logging and delegating to error handling logic.
     *
     * @param error The playback exception that occurred
     * @param song  The song that caused the error
     */
    private void handlePlayerError(PlaybackException error, Song song) {
        Logger.e(TAG, "Player error for song: " + song.getFilename(), error);
        handleWrongSong(song);
        isPlayRequested = false;
        isTransitionInProgress = false;
    }

    /**
     * Updates notification and displays toast message for the current song.
     *
     * @param song The song to display in notification and toast
     */
    private void updateNotificationAndUI(Song song) {
        mNotificationManager.createMediaNotification(song, activePlaylist.getName(), false);
        String msg = MessageFormat.format(getString(R.string.playlist_now_playing), song.getDisplayName());
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    /**
     * Updates current song progress in the playlist service.
     *
     * @param currentPosition if true, saves current playback position; if false, saves position as 0
     * @return The updated Song object, or null if no active playlist
     */
    private Song updateCurrentSongProgress(boolean currentPosition) {
        if (activePlaylist != null && currentPlayer!=null) {
            Song currentSong = activePlaylist.getCurrentSong();
            if (currentSong != null) {
                if (currentPosition) {
                    currentSong.setCurrentPosition((int) currentPlayer.getCurrentPosition());
                } else {
                    currentSong.setCurrentPosition(0);
                }
                playlistService.updateSong(currentSong);
            }
            return currentSong;
        }
        return null;
    }

    /**
     * Ensures the main volume processor is initialized with default volume.
     *
     * @see AudioProcessorManager#createProcessor(float)
     */
    private void ensureAudioProcessorsInitialized() {
        if (mainVolumeProcessor == null) {
            mainVolumeProcessor = AudioProcessorManager.createProcessor(1.0f);
        }
    }

    /**
     * Cleans up all player resources and controllers.
     *
     * <p>Should be called in onDestroy() to prevent memory leaks.</p>
     *
     * @see CrossfadeController#cleanup()
     * @see ExoPlayerManager#safeReleasePlayer(ExoPlayer)
     */
    private void cleanup() {
        if (crossfadeController != null) {
            crossfadeController.cleanup();
        }
        ExoPlayerManager.safeReleasePlayer(currentPlayer);
        ExoPlayerManager.safeReleasePlayer(nextPlayer);
        currentPlayer = null;
        nextPlayer = null;
        mainVolumeProcessor = null;
        nextVolumeProcessor = null;
        mNotificationManager.removeNotification();
    }

    /**
     * Monitors playback progress and triggers next song when crossfade point is reached.
     *
     * @param currentSong The song currently being played (for logging/debugging)
     */
    private void observeEnding(final Song currentSong) {
        Log.d(TAG, "Observing ending for song: " + currentSong.getFilename());
        final Handler h = new Handler(getMainLooper());
        final int fadeDuration = getDuration();
        Runnable endingCheck = new Runnable() {
            @Override
            public void run() {
                if (currentPlayer == null) {
                    Log.d(TAG, "Current player is null for song: " + currentSong.getFilename());
                    return;
                }
                int playbackState = currentPlayer.getPlaybackState();
                if (playbackState == androidx.media3.common.Player.STATE_IDLE || playbackState == androidx.media3.common.Player.STATE_ENDED) {
                    Log.d(TAG, "Player ended or idle for song: " + currentSong.getFilename());
                    return;
                }
                long trackDuration = currentPlayer.getDuration();
                if (trackDuration == C.TIME_UNSET) {
                    Log.d(TAG, "Track duration is not set for: " + currentSong.getFilename());
                    h.postDelayed(this, 500);
                    return;
                }
                long currentPosition = currentPlayer.getCurrentPosition();
                long triggerPoint = trackDuration - fadeDuration;
                if (currentPosition < triggerPoint) {
                    h.postDelayed(this, 500);
                } else {
                    Log.d(TAG, "Song " + currentSong.getFilename() + "ended, playing next");
                    next();
                }
            }
        };

        h.post(endingCheck);
    }

    /**
     * Handle scenario when there is something wrong while playing song
     * There are scenario when song was deleted , or corrupted. In this case we will notify user about it and delete song from playlist
     * THen attempt to play next song is made. It will then proceed with normal flow ( if playlist is empty it will just stop )
     *
     * @param problematicSong song that was attempted to play
     */
    public void handleWrongSong(Song problematicSong) {
        String errorMsg = MessageFormat.format(getString(R.string.playlist_cant_play), problematicSong.getFilename());
        Toast.makeText(getBaseContext(), errorMsg, Toast.LENGTH_LONG).show();
        if (activePlaylist != null) {
            activePlaylist.setCurrentSong(null);
            boolean removedFromCurrentSequence;
            if (activePlaylist.getPlaylist() != null) {
                removedFromCurrentSequence = activePlaylist.getPlaylist().remove(problematicSong);
                if (removedFromCurrentSequence) {
                    Logger.d(TAG, "Problematic song '" + problematicSong.getFilename() + "' removed from current playback sequence (getPlaylist).");
                } else {
                    Logger.w(TAG, "Problematic song '" + problematicSong.getFilename() + "' NOT found in current playback sequence (getPlaylist).");
                }
            } else {
                Logger.w(TAG, "activePlaylist.getPlaylist() was null. Cannot remove problematic song from sequence.");
            }
            boolean removedFromMasterList;
            if (activePlaylist.getSongs() != null) {
                removedFromMasterList = activePlaylist.getSongs().remove(problematicSong);
                if (removedFromMasterList) {
                    activePlaylist.setSongCount(activePlaylist.getSongCount() - 1);
                    Logger.d(TAG, "Problematic song '" + problematicSong.getFilename() + "' removed from master song list (getSongs).");
                } else {
                    Logger.w(TAG, "Problematic song '" + problematicSong.getFilename() + "' NOT found in master song list (getSongs).");
                }
            } else {
                Logger.w(TAG, "activePlaylist.getSongs() was null. Cannot remove problematic song from master list.");
            }
        } else {
            Logger.w(TAG, "activePlaylist or problematicSong was null. Cannot perform direct removal.");
        }
        //notify all agents about problematic song deletion
        Intent intent = new Intent(PLAYBACK_NOTIFICATION_DELETE_NOT_FOUND.getCode());
        Bundle args = new Bundle();
        args.putSerializable(Utils.SONG, problematicSong);
        args.putSerializable(PLAYLIST, activePlaylist);
        intent.putExtra(ARGS, args);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        next();
    }

    /**
     * Checks if active playlist is the same as playlist
     *
     * @param playlist playlist to check
     * @return true if active playlist is the same as playlist
     */
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

    /**
     * Starts progress bar updates if not already running
     */
    private void startProgressUpdates() {
        if (!isProgressUpdateRunning) {
            isProgressUpdateRunning = true;
            progressHandler.post(updateProgressTask);
            Logger.d(TAG, "Progress updates started");
        }
    }

    /**
     * Stops progress bar updates
     */
    private void stopProgressUpdates() {
        if (isProgressUpdateRunning) {
            isProgressUpdateRunning = false;
            progressHandler.removeCallbacks(updateProgressTask);
            Logger.d(TAG, "Progress updates stopped");
        }
    }

    /**
     * Updated progress task with proper loop management
     */
    private final Runnable updateProgressTask = new Runnable() {
        public void run() {
            if (!isProgressUpdateRunning) {
                Logger.d(TAG, "Progress updates stopped, exiting loop");
                return;
            }
            if (currentPlayer == null) {
                progressHandler.postDelayed(this, 100);
                return;
            }
            try {
                long totalDuration = currentPlayer.getDuration();
                long currentDuration = currentPlayer.getCurrentPosition();

                if (totalDuration <= 0) {
                    progressHandler.postDelayed(this, 100);
                    return;
                }

                if (currentDuration > totalDuration) {
                    currentDuration = totalDuration;
                }

                int progress = getProgressPercentage(currentDuration, totalDuration);
                progress = Math.max(0, Math.min(100, progress));
                progressBar.setProgress(progress);

            } catch (IllegalStateException e) {
                Logger.d(TAG, "Player state exception during progress update, skipping");
            }

            // Only continue if updates are still supposed to be running
            if (isProgressUpdateRunning) {
                progressHandler.postDelayed(this, 100);
            }
        }
    };


    /**
     * Calculates progress percentage for progress bar
     *
     * @param currentDuration current duration of song
     * @param totalDuration   total duration of song
     * @return progress percentage
     */
    public int getProgressPercentage(long currentDuration, long totalDuration) {
        val currentSeconds = (int) (currentDuration / 1000);
        val totalSeconds = (int) (totalDuration / 1000);
        return (int) ((((double) currentSeconds) / totalSeconds) * 100);
    }

    /**
     * Sends event from service towards all receivers
     *
     * @param type     type of event
     * @param position position of song
     */
    private void populateAndSend(EventType type, int position) {
        Intent intent = new Intent(type.getCode());
        val args = new Bundle();
        args.putSerializable(Utils.POSITION, position);
        intent.putExtra(ARGS, args);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }


    /**
     * Updates current song position and swaps active playlist
     *
     * @param playlist new playlist
     */
    private void updateCurrentSongAndSwitchPlaylist(Playlist playlist) {
        updateCurrentSongProgress(true);
        activePlaylist = playlist;
    }

    /**
     * Handler for removed song to secure of potentially playing song in service
     *
     * @param args    bundle with song and shuffle flag
     * @param shuffle flag to shuffle playlist
     */
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

    private void handleRecreateList(boolean shuffle) {
        if (activePlaylist != null) {
            createPlaylist(activePlaylist, shuffle);
        } else {
            Logger.d(TAG, "Received recreate command but no active playlist found in service, searching for one");
            playlistService.getActiveAndLoadSongs(
                    playlist -> activePlaylist = playlist,
                    () ->
                            Log.d(TAG, "No active playlist found in service while trying to recreate it."));
        }
    }

    /**
     * connection to service allowing communication
     */
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
            new Handler(Looper.getMainLooper()).post(() -> mClientCallbacks.onPlaybackStateChanged(type));
        }
    }


    /**
     * Receiver for events from other services or agents
     */
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            val event = EventType.getType(intent.getAction());
            Logger.d(TAG, "[EVENT] Received event " + event);
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
                                .ifPresent(playlist -> updateCurrentSongAndSwitchPlaylist((Playlist) playlist));
                    }
                    break;
                case PLAYLIST_NOTIFICATION_RECREATE_LIST:
                    handleRecreateList(shuffle);
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

    public interface PlayerServiceCallbacks {
        void onPlaybackStateChanged(EventType type);
    }

}
