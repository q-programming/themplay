package pl.qprogramming.themplay;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
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

import com.google.android.material.color.MaterialColors;
import com.reactiveandroid.ReActiveAndroid;
import com.reactiveandroid.ReActiveConfig;
import com.reactiveandroid.internal.database.DatabaseConfig;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.Optional;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.preference.PreferenceManager;
import lombok.val;
import pl.qprogramming.themplay.player.PlayerService;
import pl.qprogramming.themplay.playlist.EventType;
import pl.qprogramming.themplay.playlist.Playlist;
import pl.qprogramming.themplay.playlist.PlaylistService;
import pl.qprogramming.themplay.playlist.Song;
import pl.qprogramming.themplay.playlist.ThemPlayDatabase;
import pl.qprogramming.themplay.playlist.exceptions.PlaylistNotFoundException;
import pl.qprogramming.themplay.preset.Preset;
import pl.qprogramming.themplay.settings.Property;
import pl.qprogramming.themplay.views.AboutFragment;
import pl.qprogramming.themplay.views.PlaylistFragment;
import pl.qprogramming.themplay.views.PlaylistSettingsFragment;
import pl.qprogramming.themplay.views.PresetsFragment;
import pl.qprogramming.themplay.views.SettingsFragment;

import static androidx.preference.PreferenceManager.getDefaultSharedPreferences;
import static pl.qprogramming.themplay.playlist.ThemPlayDatabase.MIGRATION_1_2;
import static pl.qprogramming.themplay.settings.Property.COPY_PLAYLIST;
import static pl.qprogramming.themplay.util.Utils.ARGS;
import static pl.qprogramming.themplay.util.Utils.PLAYLIST;
import static pl.qprogramming.themplay.util.Utils.PRESET;
import static pl.qprogramming.themplay.util.Utils.SONG;
import static pl.qprogramming.themplay.util.Utils.isEmpty;
import static pl.qprogramming.themplay.util.Utils.navigateToFragment;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private PlaylistService playlistService;
    private PlayerService playerService;
    private boolean serviceIsBound;
    private boolean playerServiceIsBound;
    private int activeColor;
    private ProgressBar loader;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupDBConnection();
        setupServices();
        setActiveColor();
        setupPreferences();
        setupMainMenu();
        setupLoader();
        setupMediaControls();
        checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE);
        checkPermission(Manifest.permission.INTERNET);
        //load playlist fragment
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.activity_fragment_layout, new PlaylistFragment())
                .commit();
        val filter = new IntentFilter(EventType.PLAYLIST_NOTIFICATION_ACTIVE.getCode());
        filter.addAction(EventType.PLAYLIST_NOTIFICATION_DELETE.getCode());
        filter.addAction(EventType.PLAYBACK_NOTIFICATION_PLAY.getCode());
        filter.addAction(EventType.PLAYBACK_NOTIFICATION_STOP.getCode());
        filter.addAction(EventType.PLAYBACK_NOTIFICATION_PAUSE.getCode());
        filter.addAction(EventType.PRESET_ACTIVATED.getCode());
        filter.addAction(EventType.PRESET_REMOVED.getCode());
        filter.addAction(EventType.OPERATION_STARTED.getCode());
        filter.addAction(EventType.OPERATION_FINISHED.getCode());
        filter.addAction(EventType.PLAYBACK_NOTIFICATION_DELETE_NOT_FOUND.getCode());
        registerReceiver(receiver, filter);
    }

    private void setupLoader() {
        loader = findViewById(R.id.operation_in_progress);
    }

    private void setActiveColor() {
        activeColor = MaterialColors.getColor(findViewById(R.id.bottomAppBar), R.attr.colorSecondary);
    }

    private void setupDBConnection() {
        DatabaseConfig appDatabase = new DatabaseConfig.Builder(ThemPlayDatabase.class)
                .addMigrations(MIGRATION_1_2)
                .build();
        ReActiveAndroid.init(new ReActiveConfig.Builder(this)
                .addDatabaseConfigs(appDatabase)
                .build());
    }

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
                        playlistService.paste(copyId);
                    } catch (PlaylistNotFoundException | CloneNotSupportedException e) {
                        Log.d(TAG, "something went wrong while trying to paste playlist", e);
                        Toast.makeText(this, getString(R.string.playlist_paste_error), Toast.LENGTH_LONG).show();
                        e.printStackTrace();
                    }
                } else {
                    navigateToFragment(getSupportFragmentManager(), new AboutFragment(), "about");
                }
                return true;
            });
            popup.show();
        });
    }

    private void setupServices() {
        val context = getApplicationContext();
        val intent = new Intent(context, PlaylistService.class);
        val playerIntent = new Intent(context, PlayerService.class);
        context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        context.bindService(playerIntent, playerConnection, Context.BIND_AUTO_CREATE);

    }


    private void setupPreferences() {
        val sp = getDefaultSharedPreferences(this);
        val darkMode = sp.getBoolean(Property.DARK_MODE, false);
        val keepScreenOn = sp.getBoolean(Property.KEEP_SCREEN_ON, true);
        findViewById(R.id.activity_fragment_layout).setKeepScreenOn(keepScreenOn);
        AppCompatDelegate.setDefaultNightMode(darkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }


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
            sendBroadcast(notify);
        });

    }

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

    private void renderPauseButton() {
        val play_pause = (ImageView) findViewById(R.id.play_pause);
        play_pause.setImageResource(R.drawable.ic_pause_32);
        DrawableCompat.setTint(
                DrawableCompat.wrap(play_pause.getDrawable()),
                activeColor
        );
    }

    private void renderPlayButton() {
        val play_pause = (ImageView) findViewById(R.id.play_pause);
        play_pause.setImageResource(R.drawable.ic_play_32);
    }

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

    private void renderNewPlaylistDialog(String currentPresetName) {
        val input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.playlist_name))
                .setView(input)
                .setPositiveButton(getString(R.string.create), (dialog, which) -> {
                    val playlistName = input.getText().toString();
                    if (playlistName.length() == 0) {
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
                        playlistService.addPlaylist(playlist);
                        val notify = new Intent(EventType.PLAYLIST_NOTIFICATION_ADD.getCode());
                        sendBroadcast(notify);
                        navigateToFragment(getSupportFragmentManager(),
                                new PlaylistSettingsFragment(playlistService, playlist),
                                playlist.getName() + playlist.getId());
                        val msg = MessageFormat.format(getString(R.string.playlist_add_created), playlist.getName());
                        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(getString(R.string.cancel), (dialog, which) -> dialog.cancel())
                .show();
    }


    @Override
    protected void onStop() {
        doUnbindService();
        super.onStop();
    }

    void doUnbindService() {
        val context = getApplicationContext();
        if (serviceIsBound) {
            context.unbindService(mConnection);
            serviceIsBound = false;
        }
        if (playerServiceIsBound) {
            context.unbindService(playerConnection);
            playerServiceIsBound = false;
        }
    }

    private void checkPermission(String permission) {
        int permissionCheck = ContextCompat.checkSelfPermission(
                this, permission);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            requestPermission(permission, permission.length());
        }
    }

    private void requestPermission(String permissionName, int permissionRequestCode) {
        ActivityCompat.requestPermissions(this,
                new String[]{permissionName}, permissionRequestCode);
    }

    private final ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
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
                    Optional.ofNullable(args.getSerializable(PRESET)).ifPresent(serializedPreset -> {
                        val preset = (Preset) serializedPreset;
                        playlistService.removePlaylistsFromPreset(preset.getName());
                    });
                    break;
                case PLAYBACK_NOTIFICATION_PLAY:
                case PLAYLIST_NOTIFICATION_ACTIVE:
                    renderPauseButton();
                    break;
                case PLAYBACK_NOTIFICATION_STOP:
                case PLAYBACK_NOTIFICATION_PAUSE:
                    renderPlayButton();
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
                            .ifPresent((playlist -> {
                                val song = (Song) args.getSerializable(SONG);
                                playlistService.removeSongFromPlaylist((Playlist) playlist, Collections.singletonList(song), true);
                            }));
                    break;
            }
        }
    };

}