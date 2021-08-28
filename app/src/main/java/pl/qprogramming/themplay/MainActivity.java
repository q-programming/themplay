package pl.qprogramming.themplay;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.PopupMenu;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import lombok.val;
import pl.qprogramming.themplay.playlist.Playlist;
import pl.qprogramming.themplay.playlist.PlaylistService;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private PlaylistService playlistService;
    private boolean serviceIsBound;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        val intent = new Intent(this, PlaylistService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        setContentView(R.layout.activity_main);

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
        val number = playlistService.getPlaylists().size() + 1;
        playlistService.addPlaylist(Playlist.builder().name("Playlist " + number).id(number).currentFile("song").build());
    }

    private void toggleNighMode() {
        val nightMode = AppCompatDelegate.getDefaultNightMode();
        if (nightMode == AppCompatDelegate.MODE_NIGHT_YES) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }
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
            playlistService = ((PlaylistService.LocalBinder) service).getService();
            serviceIsBound = true;
        }

        public void onServiceDisconnected(ComponentName className) {
            playlistService = null;
        }
    };
}