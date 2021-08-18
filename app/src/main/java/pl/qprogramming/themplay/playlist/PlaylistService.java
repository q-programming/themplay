package pl.qprogramming.themplay.playlist;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

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
        val playlist1 = Playlist.builder().name("Playlist 1").build();
        val playlist2 = Playlist.builder().name("Playlist 2").build();
        playlists.add(playlist1);
        playlists.add(playlist2);
        return mBinder;

    }

    public void addPlaylist(Playlist playlist) {
        Log.d(TAG, "Adding new playlist" + playlist);
        Intent intent = new Intent(EventType.PLAYLIST_NOTIFICATION.getCode());
        sendBroadcast(intent);
        playlists.add(playlist);
    }

    public class LocalBinder extends Binder {
        public PlaylistService getService() {
            return PlaylistService.this;
        }
    }
}
