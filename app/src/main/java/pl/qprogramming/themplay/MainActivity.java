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
import lombok.val;
import pl.qprogramming.themplay.playlist.EventType;
import pl.qprogramming.themplay.playlist.Playlist;
import pl.qprogramming.themplay.playlist.PlaylistService;
import pl.qprogramming.themplay.playlist.ThemPlayDatabase;
import pl.qprogramming.themplay.settings.Property;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    public static final String PL = "pl";
    public static final String EN = "en-US";
    private PlaylistService playlistService;
    private boolean serviceIsBound;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DatabaseConfig appDatabase = new DatabaseConfig.Builder(ThemPlayDatabase.class)
                .build();

        ReActiveAndroid.init(new ReActiveConfig.Builder(this)
                .addDatabaseConfigs(appDatabase)
                .build());

        val intent = new Intent(this, PlaylistService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        setContentView(R.layout.activity_main);
        val darkMode = Boolean.parseBoolean(Property.getProperty(Property.DARK_MODE).getValue());
        if (darkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }

        //menu
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
                } else if (itemId == R.id.nightToggle) {
                    toggleNighMode();
                } else if (itemId == R.id.settings) {
                    Log.d(TAG, "settings");
                } else {
                    Log.d(TAG, "clicked about");
                }
                return true;
            });
            popup.show();
        });
    }

    private void addPlaylist() {
        val playlist = new Playlist();
        playlist.setName("Playlist");
        playlistService.addPlaylist(playlist);
    }

    private void toggleNighMode() {
        val darkMode = Property.getProperty(Property.DARK_MODE);
        val isDarkMode = Boolean.parseBoolean(darkMode.getValue());
        if (isDarkMode) {
            darkMode.setValue("false");
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else {
            darkMode.setValue("true");
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }
        darkMode.save();
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