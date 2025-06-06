package pl.qprogramming.themplay;

import static androidx.preference.PreferenceManager.getDefaultSharedPreferences;
import static pl.qprogramming.themplay.settings.Property.COPY_PLAYLIST;
import static pl.qprogramming.themplay.settings.Property.LAST_LAUNCH_VERSION;
import static pl.qprogramming.themplay.util.Utils.ARGS;
import static pl.qprogramming.themplay.util.Utils.PLAYLIST;
import static pl.qprogramming.themplay.util.Utils.PRESET;
import static pl.qprogramming.themplay.util.Utils.SONG;
import static pl.qprogramming.themplay.util.Utils.isEmpty;
import static pl.qprogramming.themplay.util.Utils.navigateToFragment;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import com.google.android.material.color.MaterialColors;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.val;
import pl.qprogramming.themplay.domain.Playlist;
import pl.qprogramming.themplay.domain.Preset;
import pl.qprogramming.themplay.domain.Song;
import pl.qprogramming.themplay.player.PlayerService;
import pl.qprogramming.themplay.playlist.EventType;
import pl.qprogramming.themplay.playlist.PlaylistService;
import pl.qprogramming.themplay.playlist.exceptions.PlaylistNameExistsException;
import pl.qprogramming.themplay.playlist.exceptions.PlaylistNotFoundException;
import pl.qprogramming.themplay.settings.Property;
import pl.qprogramming.themplay.util.Utils;
import pl.qprogramming.themplay.views.AboutFragment;
import pl.qprogramming.themplay.views.PlaylistFragment;
import pl.qprogramming.themplay.views.PlaylistSettingsFragment;
import pl.qprogramming.themplay.views.PresetsFragment;
import pl.qprogramming.themplay.views.SettingsFragment;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private PlaylistService playlistService;
    private PlayerService playerService;
    private boolean serviceIsBound;
    private boolean playerServiceIsBound;
    private int activeColor;
    private ProgressBar loader;

    private ActivityResultLauncher<String[]> multiplePermissionsLauncher;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setActiveColor();
        setupPreferences();
        setupMainMenu();
        setupLoader();
        setupMediaControls();
        checkPermissions();
        onLaunch();
    }

    private void onLaunch() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        String lastLaunchVersion = sp.getString(LAST_LAUNCH_VERSION, "");
        String currentAppVersion = "";
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            currentAppVersion = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Could not get current app version", e);
            if (lastLaunchVersion.isEmpty()) {
                Log.i(TAG, "First launch (or no stored version) AND current version unknown. Navigating to About.");
                navigateToFragment(getSupportFragmentManager(), new AboutFragment(), "about");
            } else {
                Log.w(TAG, "Current version unknown, but not first launch. Loading default PlaylistFragment.");
                navigateToFragment(getSupportFragmentManager(), new PlaylistFragment(), "playlist_default");
            }
            return;
        }

        Log.d(TAG, "Last launch version: '" + lastLaunchVersion + "', Current app version: '" + currentAppVersion + "'");

        if (lastLaunchVersion.isEmpty()) {
            Log.i(TAG, "First launch detected. Navigating to About Fragment.");
            navigateToFragment(getSupportFragmentManager(), new AboutFragment(), "about");
            sp.edit().putString(LAST_LAUNCH_VERSION, currentAppVersion).apply();
        } else {
            val comparison = Utils.compareVersions(lastLaunchVersion, currentAppVersion);
            boolean specialNavigationOccurred = false;
            switch (comparison) {
                case CURRENT_IS_NEWER:
                    Log.i(TAG, "App updated from " + lastLaunchVersion + " to " + currentAppVersion + ".");
                    // TODO: Navigate to a real ReleaseNotesFragment
                    // Example:
                    // Utils.navigateToFragment(getSupportFragmentManager(), new ReleaseNotesFragment(), "releaseNotes");
                    // specialNavigationOccurred = true; // Set this if you navigate
                    Toast.makeText(this, "App updated to version " + currentAppVersion + "! Check out what's new.", Toast.LENGTH_LONG).show();
                    sp.edit().putString(LAST_LAUNCH_VERSION, currentAppVersion).apply();
                    break;
                case STORED_IS_NEWER: // Downgrade
                    Log.w(TAG, "Stored version (" + lastLaunchVersion + ") is newer than current (" + currentAppVersion + "). Updating stored version.");
                    sp.edit().putString(LAST_LAUNCH_VERSION, currentAppVersion).apply();
                    break;
                case VERSIONS_ARE_SAME:
                    Log.d(TAG, "Versions are the same. Loading default fragment.");
                    break;
                case ERROR_PARSING:
                    Log.e(TAG, "Error parsing versions. Last: " + lastLaunchVersion + ", Current: " + currentAppVersion + ". Loading default fragment.");
                    break;
            }
            if (!specialNavigationOccurred) {
                Utils.navigateToFragment(getSupportFragmentManager(), new PlaylistFragment(), "playlist_default");
            }
        }
    }

    /**
     * Checks for required permissions and requests them if they are not granted
     */
    private void checkPermissions() {
        multiplePermissionsLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                (Map<String, Boolean> grantedPermissionsMap) -> {
                    Log.d(TAG, "Permissions result callback received: " + grantedPermissionsMap);
                    for (Map.Entry<String, Boolean> entry : grantedPermissionsMap.entrySet()) {
                        Log.d(TAG, "Permission: " + entry.getKey() + ", Granted in dialog: " + entry.getValue());
                    }
                    evaluateAndProceedBasedOnEssentialPermissions();
                });
        checkAndRequestRequiredPermissions();
    }

    private void checkAndRequestRequiredPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else { // API 30, 31, 32
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
        if (!permissionsToRequest.isEmpty()) {
            Log.d(TAG, "Requesting permissions: " + permissionsToRequest);
            multiplePermissionsLauncher.launch(permissionsToRequest.toArray(new String[0]));
        } else {
            Log.d(TAG, "All initially checked permissions appear to be granted.");
            evaluateAndProceedBasedOnEssentialPermissions();
        }
    }

    private void evaluateAndProceedBasedOnEssentialPermissions() {
        boolean allEssentialPermissionsCurrentlyGranted = true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                allEssentialPermissionsCurrentlyGranted = false;
                Log.w(TAG, Manifest.permission.READ_MEDIA_AUDIO + " is currently DENIED (Essential).");
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                allEssentialPermissionsCurrentlyGranted = false;
                Log.w(TAG, Manifest.permission.READ_MEDIA_IMAGES + " is currently DENIED (Essential).");
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, Manifest.permission.POST_NOTIFICATIONS + " is currently DENIED (user wants them but permission lacking).");
            }
        } else { // API 30-32
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                allEssentialPermissionsCurrentlyGranted = false;
                Log.w(TAG, Manifest.permission.READ_EXTERNAL_STORAGE + " is currently DENIED (Essential).");
            }
        }
        if (allEssentialPermissionsCurrentlyGranted) {
            Log.d(TAG, "All essential media permissions are currently GRANTED. App can now load and play media.");
        } else {
            Log.e(TAG, "One or more essential permissions are NOT granted. App functionality will be limited.");
            Toast.makeText(this, "Essential media permissions are required to use this app. Please grant them in app settings.", Toast.LENGTH_LONG).show();
        }
    }

    private void setupLoader() {
        loader = findViewById(R.id.operation_in_progress);
    }

    private void setActiveColor() {
        activeColor = MaterialColors.getColor(findViewById(R.id.bottomAppBar), R.attr.colorSecondary);
    }

    /**
     * Setup main menu click listeners
     * If copy playlist is set , show paste playlist option
     */
    private void setupMainMenu() {
        val sp = PreferenceManager.getDefaultSharedPreferences(this);
        val menu = findViewById(R.id.menu);
        menu.setOnClickListener(menuView -> {
            val popup = new PopupMenu(this, menu);
            popup.getMenuInflater().inflate(R.menu.settings_menu, popup.getMenu());
            val copyId = sp.getLong(COPY_PLAYLIST, -1L);
            popup.getMenu().findItem(R.id.pastePlaylist).setVisible(copyId >= 0);
            popup.setOnMenuItemClickListener(item -> {
                val itemId = item.getItemId();
                if (itemId == R.id.addPlaylist) {
                    addPlaylist();
                } else if (itemId == R.id.settings) {
                    navigateToFragment(getSupportFragmentManager(), new SettingsFragment(), "settings");
                } else if (itemId == R.id.preset) {
                    navigateToFragment(getSupportFragmentManager(), new PresetsFragment(), "presets");
                } else if (itemId == R.id.pastePlaylist) {
                    try {
                        playlistService.paste(copyId,
                                playlist -> Toast.makeText(getApplicationContext(), getString(R.string.playlist_pasted), Toast.LENGTH_LONG).show());
                    } catch (PlaylistNotFoundException | CloneNotSupportedException e) {
                        Log.e(TAG, "something went wrong while trying to paste playlist", e);
                        Toast.makeText(this, getString(R.string.playlist_paste_error), Toast.LENGTH_LONG).show();
                    }
                } else {
                    navigateToFragment(getSupportFragmentManager(), new AboutFragment(), "about");
                }
                return true;
            });
            popup.show();
        });
    }

    /**
     * Binds to services
     */
    private void setupServices() {
        Log.d(TAG, "Setting up services");
        val context = getApplicationContext();
        //playlist service
        val intent = new Intent(context, PlaylistService.class);
        context.bindService(intent, playlistServiceConnection, Context.BIND_AUTO_CREATE);
        //player service
        val playerIntent = new Intent(context, PlayerService.class);
        context.bindService(playerIntent, playerConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * Setup receiver for events
     */
    private void setupReceiver() {
        val filter = new IntentFilter(EventType.PLAYLIST_NOTIFICATION_ACTIVE.getCode());
        filter.addAction(EventType.PLAYLIST_NOTIFICATION_NEW_ACTIVE.getCode());
        filter.addAction(EventType.PLAYLIST_NOTIFICATION_DELETE.getCode());
        filter.addAction(EventType.PLAYBACK_NOTIFICATION_PLAY.getCode());
        filter.addAction(EventType.PLAYBACK_NOTIFICATION_STOP.getCode());
        filter.addAction(EventType.PLAYBACK_NOTIFICATION_PAUSE.getCode());
        filter.addAction(EventType.PRESET_ACTIVATED.getCode());
        filter.addAction(EventType.PRESET_REMOVED.getCode());
        filter.addAction(EventType.OPERATION_STARTED.getCode());
        filter.addAction(EventType.OPERATION_FINISHED.getCode());
        filter.addAction(EventType.PLAYBACK_NOTIFICATION_DELETE_NOT_FOUND.getCode());
        filter.addAction(EventType.PLAYLIST_NOTIFICATION_PLAY_NO_SONGS.getCode());
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);
    }


    /**
     * Sets up preferences while app is starting
     * Clear copy playlist pref on boot
     * Set dark mode based on preference
     * Set keep screen on based on preference
     */
    private void setupPreferences() {
        val sp = getDefaultSharedPreferences(this);
        sp.edit().remove(COPY_PLAYLIST).apply();
        val darkMode = sp.getBoolean(Property.DARK_MODE, false);
        val keepScreenOn = sp.getBoolean(Property.KEEP_SCREEN_ON, true);
        findViewById(R.id.activity_fragment_layout).setKeepScreenOn(keepScreenOn);
        AppCompatDelegate.setDefaultNightMode(darkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }

    /**
     * Sets up media controls for player
     * Play/Pause button
     * Next/Previous buttons
     * Stop button
     * Shuffle button
     */
    private void setupMediaControls() {
        val play_pause_btn = (ImageView) findViewById(R.id.play_pause);
        val shuffle_btn = (ImageView) findViewById(R.id.shuffle);
        play_pause_btn.setOnClickListener(play -> {
            if (playerService.isPlaying()) {
                playerService.pause();
                play_pause_btn.setImageResource(R.drawable.ic_play_32);
            } else {
                playerService.play();
                renderPauseButton();
            }
        });
        findViewById(R.id.next).setOnClickListener(next -> {
            playerService.next();
            renderPauseButton();
        });
        findViewById(R.id.previous).setOnClickListener(prev -> {
            playerService.previous();
            renderPauseButton();
        });
        findViewById(R.id.stop).setOnClickListener(stop -> {
            playerService.stop();
            play_pause_btn.setImageResource(R.drawable.ic_play_32);
        });

        val sp = getDefaultSharedPreferences(this);
        val shuffle = sp.getBoolean(Property.SHUFFLE_MODE, true);
        renderShuffle(shuffle);
        shuffle_btn.setOnClickListener(rand -> {
            val newRandom = !sp.getBoolean(Property.SHUFFLE_MODE, true);
            val editor = sp.edit();
            editor.putBoolean(Property.SHUFFLE_MODE, newRandom);
            editor.apply();
            renderShuffle(newRandom);
            val notify = new Intent(EventType.PLAYLIST_NOTIFICATION_RECREATE_LIST.getCode());
            val args = new Bundle();
            notify.putExtra(ARGS, args);
            LocalBroadcastManager.getInstance(this).sendBroadcast(notify);
        });

    }

    /**
     * Renders shuffle button
     *
     * @param shuffle state of shuffle button
     */
    private void renderShuffle(boolean shuffle) {
        val shuffle_btn = (ImageView) findViewById(R.id.shuffle);
        if (shuffle) {
            DrawableCompat.setTint(
                    DrawableCompat.wrap(shuffle_btn.getDrawable()),
                    activeColor
            );
        } else {
            shuffle_btn.setImageResource(R.drawable.ic_shuffle_32);
        }
    }

    /**
     * Renders pause button
     */
    private void renderPauseButton() {
        val play_pause = (ImageView) findViewById(R.id.play_pause);
        play_pause.setImageResource(R.drawable.ic_pause_32);
        DrawableCompat.setTint(
                DrawableCompat.wrap(play_pause.getDrawable()),
                activeColor
        );
    }

    /**
     * Renders play button
     */
    private void renderPlayButton() {
        val play_pause = (ImageView) findViewById(R.id.play_pause);
        play_pause.setImageResource(R.drawable.ic_play_32);
    }

    /**
     * Creates new playlist into currently selected preset
     * If it's not selected , show warning
     */
    private void addPlaylist() {
        val sp = getDefaultSharedPreferences(this);
        val currentPresetName = sp.getString(Property.CURRENT_PRESET, null);
        if (isEmpty(currentPresetName)) {
            val msg = getString(R.string.presets_create_first);
            Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
        } else {
            renderNewPlaylistDialog(currentPresetName);
        }
    }

    /**
     * Renders dialog for creating new playlist
     *
     * @param currentPresetName name of currently selected preset
     */
    private void renderNewPlaylistDialog(String currentPresetName) {
        val input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.playlist_name))
                .setView(input)
                .setPositiveButton(getString(R.string.create), (dialog, which) -> {
                    val playlistName = input.getText().toString().trim();
                    if (playlistName.isEmpty()) {
                        val msg = getString(R.string.playlist_add_atLeastOneChar);
                        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
                        input.setError(getString(R.string.playlist_name_atLeastOneChar));
                    } else {
                        input.setError(null);
                        val playlist = Playlist
                                .builder()
                                .name(playlistName)
                                .preset(currentPresetName)
                                .build();
                        playlistService.addPlaylist(playlist,
                                newPlaylist -> {
                                    navigateToFragment(getSupportFragmentManager(),
                                            new PlaylistSettingsFragment(playlist),
                                            playlist.getName() + playlist.getId());
                                    val msg = MessageFormat.format(getString(R.string.playlist_add_created), playlist.getName());
                                    Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
                                },
                                throwable -> {
                                    if (throwable instanceof PlaylistNameExistsException) {
                                        val msg = MessageFormat.format(getString(R.string.playlist_already_exists), playlistName);
                                        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
                                        input.setError(msg);
                                    } else {
                                        Log.e(TAG, "Error while adding new playlist", throwable);
                                        val msg = getString(R.string.playlist_add_error);
                                        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
                                    }
                                });
                    }
                })
                .setNegativeButton(getString(R.string.cancel), (dialog, which) -> dialog.cancel())
                .show();
    }

    /**
     * Binds to services and creates receiver upon starting of activity
     */
    @Override
    protected void onStart() {
        setupServices();
        setupReceiver();
        super.onStart();
    }

    /**
     * Unbinds from services upon stopping of activity
     */
    @Override
    protected void onStop() {
        Log.d(TAG, "Stopping main activity");
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "Receiver not registered");
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "Destroying main activity");
        super.onDestroy();
        doUnbindService();
    }

    void doUnbindService() {
        val context = getApplicationContext();
        if (serviceIsBound) {
            context.unbindService(playlistServiceConnection);
            serviceIsBound = false;
        }
        if (playerServiceIsBound) {
            context.unbindService(playerConnection);
            playerServiceIsBound = false;
        }
    }

    private final ServiceConnection playlistServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(TAG, "Playlist service connected");
            val binder = (PlaylistService.LocalBinder) service;
            playlistService = binder.getService();
            serviceIsBound = true;
        }

        public void onServiceDisconnected(ComponentName className) {
            playlistService = null;
        }
    };
    private final ServiceConnection playerConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(TAG, "Player service connected");
            val binder = (PlayerService.LocalBinder) service;
            playerService = binder.getService();
            playerServiceIsBound = true;
            val playBtn = (ImageView) findViewById(R.id.play_pause);
            val progressBar = (ProgressBar) findViewById(R.id.progressBar);
            playerService.setProgressBar(progressBar);
            if (playerService.isPlaying()) {
                renderPauseButton();
            } else {
                playBtn.setImageResource(R.drawable.ic_play_32);
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            playerService = null;
        }
    };


    /**
     * If playlist was activated , toggle play_pause button properly
     */
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            val event = EventType.getType(intent.getAction());
            Bundle args = intent.getBundleExtra(ARGS);
            Log.d(TAG, "Received event of type " + event.name());
            switch (event) {
                case OPERATION_STARTED:
                    loader.setVisibility(View.VISIBLE);
                    break;
                case OPERATION_FINISHED:
                    loader.setVisibility(View.GONE);
                    break;
                case PRESET_ACTIVATED:
                    playlistService.resetActiveFromPreset();
                    renderPlayButton();
                    break;
                case PRESET_REMOVED:
                    Optional.ofNullable(args.getSerializable(PRESET))
                            .ifPresent(object -> {
                                val preset = (Preset) object;
                                playlistService.removePreset(preset.getName());
                            });
                    break;
                case PLAYBACK_NOTIFICATION_PLAY:
                case PLAYLIST_NOTIFICATION_ACTIVE:
                case PLAYLIST_NOTIFICATION_NEW_ACTIVE:
                    renderPauseButton();
                    break;
                case PLAYBACK_NOTIFICATION_STOP:
                case PLAYBACK_NOTIFICATION_PAUSE:
                    renderPlayButton();
                    break;
                case PLAYLIST_NOTIFICATION_PLAY_NO_SONGS:
                    Optional.ofNullable(args.getSerializable(PLAYLIST))
                            .ifPresent(object -> {
                                        val playlist = (Playlist) object;
                                        val notActiveMsg = MessageFormat.format(getString(R.string.playlist_active_no_songs), playlist.getName());
                                        Toast.makeText(getApplicationContext(), notActiveMsg, Toast.LENGTH_LONG).show();
                                    }
                            );
                    break;
                case PLAYLIST_NOTIFICATION_DELETE:
                    Optional.ofNullable(args.getSerializable(PLAYLIST))
                            .ifPresent((playlist -> {
                                if (playerService.isActivePlaylist((Playlist) playlist)) {
                                    renderPlayButton();
                                }
                            }));
                    break;
                case PLAYBACK_NOTIFICATION_DELETE_NOT_FOUND:
                    Optional.ofNullable(args.getSerializable(PLAYLIST))
                            .ifPresent((object -> {
                                val playlist = (Playlist) object;
                                val song = (Song) args.getSerializable(SONG);
                                playlistService.removeSongsFromPlaylist(playlist.getId(), Collections.singletonList(song),
                                        updated -> Log.w(TAG, "Song deleted from playlist as it was not found: " + playlist.getName()),
                                        throwable -> Log.e(TAG, "Error while deleting not found song from playlist" + song.getFilename() + " from playlist: " + playlist.getName(), throwable));
                            }));
                    break;
            }
        }
    };

}