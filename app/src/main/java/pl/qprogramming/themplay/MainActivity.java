package pl.qprogramming.themplay;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.text.InputType;
import android.widget.EditText;
import android.widget.PopupMenu;

import com.google.android.material.snackbar.Snackbar;
import com.reactiveandroid.ReActiveAndroid;
import com.reactiveandroid.ReActiveConfig;
import com.reactiveandroid.internal.database.DatabaseConfig;

import java.text.MessageFormat;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import lombok.val;
import pl.qprogramming.themplay.playlist.EventType;
import pl.qprogramming.themplay.playlist.Playlist;
import pl.qprogramming.themplay.playlist.PlaylistService;
import pl.qprogramming.themplay.playlist.ThemPlayDatabase;
import pl.qprogramming.themplay.settings.Property;
import pl.qprogramming.themplay.views.AboutFragment;
import pl.qprogramming.themplay.views.PlaylistFragment;
import pl.qprogramming.themplay.views.PlaylistSettingsFragment;
import pl.qprogramming.themplay.views.SettingsFragment;

import static pl.qprogramming.themplay.util.Utils.navigateToFragment;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    public static final String PL = "pl";
    public static final String EN = "en-US";
    private PlaylistService playlistService;
    private boolean serviceIsBound;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupDBConnection();
        setupServices();
        setContentView(R.layout.activity_main);
        setPreferences();
        setMenu();
        checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE);
        //load playlist fragment
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.activity_fragment_layout, new PlaylistFragment())
                .commit();
    }

    private void setupDBConnection() {
        DatabaseConfig appDatabase = new DatabaseConfig.Builder(ThemPlayDatabase.class)
                .build();
        ReActiveAndroid.init(new ReActiveConfig.Builder(this)
                .addDatabaseConfigs(appDatabase)
                .build());
    }

    private void setMenu() {
        val menu = findViewById(R.id.menu);
        menu.setOnClickListener(menuView -> {
            val popup = new PopupMenu(this, menu);
            popup.getMenuInflater().inflate(R.menu.settings_menu, popup.getMenu());
            popup.setOnMenuItemClickListener(item -> {
                val itemId = item.getItemId();
                if (itemId == R.id.addPlaylist) {
                    addPlaylist();
                } else if (itemId == R.id.settings) {
                    navigateToFragment(getSupportFragmentManager(), new SettingsFragment(), "settings");
                } else {
                    navigateToFragment(getSupportFragmentManager(), new AboutFragment(), "about");
                }
                return true;
            });
            popup.show();
        });
    }

    private void setupServices() {
        val intent = new Intent(this, PlaylistService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }


    private void setPreferences() {
        val sp = PreferenceManager.getDefaultSharedPreferences(this);
        val darkMode = sp.getBoolean(Property.DARK_MODE, false);
        AppCompatDelegate.setDefaultNightMode(darkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }

    private void addPlaylist() {
        val input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.playlist_name))
                .setView(input)
                .setPositiveButton(getString(R.string.create), (dialog, which) -> {
                    val playlistName = input.getText().toString();
                    if (playlistName.length() == 0) {
                        val msg = getString(R.string.playlist_add_atLeastOneChar);
                        Snackbar.make(findViewById(R.id.container), msg, Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show();
                        input.setError(getString(R.string.playlist_name_atLeastOneChar));
                    } else {
                        input.setError(null);
                        val playlist = Playlist.builder().name(playlistName).build();
                        playlistService.addPlaylist(playlist);
                        val notify = new Intent(EventType.PLAYLIST_NOTIFICATION_ADD.getCode());
                        sendBroadcast(notify);
                        navigateToFragment(getSupportFragmentManager(),
                                new PlaylistSettingsFragment(playlistService, playlist),
                                playlist.getName() + playlist.getId());
                        val msg = MessageFormat.format(getString(R.string.playlist_add_created), playlist.getName());
                        Snackbar.make(findViewById(R.id.container), msg, Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show();
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
        if (serviceIsBound) {
            unbindService(mConnection);
            serviceIsBound = false;
        }
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
}