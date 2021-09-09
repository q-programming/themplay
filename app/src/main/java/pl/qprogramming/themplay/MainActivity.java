package pl.qprogramming.themplay;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.PopupMenu;

import com.reactiveandroid.ReActiveAndroid;
import com.reactiveandroid.ReActiveConfig;
import com.reactiveandroid.internal.database.DatabaseConfig;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;
import lombok.val;
import pl.qprogramming.themplay.playlist.EventType;
import pl.qprogramming.themplay.playlist.Playlist;
import pl.qprogramming.themplay.playlist.PlaylistService;
import pl.qprogramming.themplay.playlist.ThemPlayDatabase;
import pl.qprogramming.themplay.settings.Property;
import pl.qprogramming.themplay.views.AboutFragment;
import pl.qprogramming.themplay.views.PlaylistFragment;
import pl.qprogramming.themplay.views.SettingsFragment;

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
        setupMenu();
        //load playlist fragment
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.activity_fragment_layout, new PlaylistFragment())
                .addToBackStack("home")
                .commit();
    }

    private void setupDBConnection() {
        DatabaseConfig appDatabase = new DatabaseConfig.Builder(ThemPlayDatabase.class)
                .build();
        ReActiveAndroid.init(new ReActiveConfig.Builder(this)
                .addDatabaseConfigs(appDatabase)
                .build());
    }

    private void setupMenu() {
        val menu = findViewById(R.id.menu);
        menu.setOnClickListener(menuView -> {
            val popup = new PopupMenu(this, menu);
            popup.getMenuInflater().inflate(R.menu.settings_menu, popup.getMenu());
            popup.setOnMenuItemClickListener(item -> {
                Log.d(TAG, "clicked item " + item);
                val itemId = item.getItemId();
                if (itemId == R.id.addPlaylist) {
                    addPlaylist();
                    val notify = new Intent(EventType.PLAYLIST_NOTIFICATION_ADD.getCode());
                    sendBroadcast(notify);
                } else if (itemId == R.id.settings) {
                    getSupportFragmentManager()
                            .beginTransaction()
                            .setCustomAnimations(
                                    R.anim.slide_in,
                                    R.anim.fade_out,
                                    R.anim.fade_in,
                                    R.anim.slide_out
                            )
                            .replace(R.id.activity_fragment_layout, new SettingsFragment())
                            .addToBackStack("settings")
                            .commit();
                } else {
                    getSupportFragmentManager()
                            .beginTransaction()
                            .setCustomAnimations(
                                    R.anim.slide_in,
                                    R.anim.fade_out,
                                    R.anim.fade_in,
                                    R.anim.slide_out
                            )
                            .replace(R.id.activity_fragment_layout, new AboutFragment())
                            .addToBackStack("about")
                            .commit();
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
        val playlist = new Playlist();
        playlist.setName("Playlist");
        playlistService.addPlaylist(playlist);
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
}