package pl.qprogramming.themplay.playlist;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.view.View;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.Nullable;
import lombok.Getter;
import lombok.val;

@Getter
public class PlaylistService extends Service {
    private static final String TAG = PlaylistService.class.getSimpleName();

    private final IBinder mBinder = new LocalBinder();
    private final List<Playlist> playlists = new ArrayList<>();


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Binding service to " + intent);
        val context = getApplicationContext();
        //TODO remove later on
        val filename = "Some song";
        val playlist1 = Playlist.builder().name("Playlist 1").id(0).currentFile(filename).build();
        val playlist2 = Playlist.builder().name("Playlist 2").id(1).currentFile(filename).build();
        playlists.add(playlist1);
        playlists.add(playlist2);
        return mBinder;

    }

    public void addPlaylist(Playlist playlist) {
        Log.d(TAG, "Adding new playlist" + playlist);
        playlists.add(playlist);
        Intent intent = new Intent(EventType.PLAYLIST_NOTIFICATION.getCode());
        sendBroadcast(intent);
    }

    public void setActive(Playlist item, View view) {
        playlists.forEach(playlist -> {
            if (playlist.getId() == item.getId()) {
                if (!playlist.isActive()) {
                    playlist.setActive(true);
                    Log.d(TAG, "Playing now :" + playlist.getName());
                    Intent intent = new Intent(EventType.PLAYLIST_PLAY_NOTIFICATION.getCode());
                    sendBroadcast(intent);
                    Snackbar.make(view, "Now playing " + playlist.getName(), Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                    // TODO play
                }
            } else {
                playlist.setActive(false);
            }
        });
    }


    public class LocalBinder extends Binder {
        public PlaylistService getService() {
            return PlaylistService.this;
        }
    }
}
