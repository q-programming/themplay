package pl.qprogramming.themplay.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import lombok.val;
import pl.qprogramming.themplay.MainActivity;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.playlist.Song;
import pl.qprogramming.themplay.settings.Property;

import static androidx.preference.PreferenceManager.getDefaultSharedPreferences;
import static pl.qprogramming.themplay.playlist.EventType.PLAYBACK_NOTIFICATION_NEXT;
import static pl.qprogramming.themplay.playlist.EventType.PLAYBACK_NOTIFICATION_PAUSE;
import static pl.qprogramming.themplay.playlist.EventType.PLAYBACK_NOTIFICATION_PLAY;
import static pl.qprogramming.themplay.playlist.EventType.PLAYBACK_NOTIFICATION_PREV;
import static pl.qprogramming.themplay.playlist.EventType.PLAYBACK_NOTIFICATION_STOP;

public class MediaNotificationManager {

    private static final int NOTIFICATION_ID = 7;
    private static final String CHANNEL_ID = "themplay_player";

    private final Service mService;
    private final NotificationManager notificationManager;

    public MediaNotificationManager(Service mService) {
        this.mService = mService;
        notificationManager = (NotificationManager) mService.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    /**
     * Creates new notification for passed song
     *
     * @param song  Song that will be played now/paused etc
     * @param pause if true , pause button be rendered, otherwise play
     */
    public void createMediaNotification(Song song, boolean pause) {
        Intent openAppIntent = new Intent(mService, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(mService, 0,
                openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        val sp = getDefaultSharedPreferences(mService);
        val currentPresetName = sp.getString(Property.CURRENT_PRESET, null);
        val builder = new NotificationCompat.Builder(mService, CHANNEL_ID)
                .setContentIntent(pendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSmallIcon(R.drawable.ic_logo)
                .addAction(R.drawable.ic_previous_32, "Previous", PendingIntent.getBroadcast(mService, NOTIFICATION_ID, new Intent(PLAYBACK_NOTIFICATION_PREV.getCode()), PendingIntent.FLAG_UPDATE_CURRENT))
                .addAction(R.drawable.ic_stop_32, "Stop", PendingIntent.getBroadcast(mService, NOTIFICATION_ID, new Intent(PLAYBACK_NOTIFICATION_STOP.getCode()), PendingIntent.FLAG_UPDATE_CURRENT));
        if (pause) {
            builder.addAction(R.drawable.ic_play_32, "Play", PendingIntent.getBroadcast(mService, NOTIFICATION_ID, new Intent(PLAYBACK_NOTIFICATION_PLAY.getCode()), PendingIntent.FLAG_UPDATE_CURRENT));
        } else {
            builder.addAction(R.drawable.ic_pause_32, "Pause", PendingIntent.getBroadcast(mService, NOTIFICATION_ID, new Intent(PLAYBACK_NOTIFICATION_PAUSE.getCode()), PendingIntent.FLAG_UPDATE_CURRENT));
        }
        builder.addAction(R.drawable.ic_next_32, "Next", PendingIntent.getBroadcast(mService, NOTIFICATION_ID, new Intent(PLAYBACK_NOTIFICATION_NEXT.getCode()), PendingIntent.FLAG_UPDATE_CURRENT))
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle())
                .setContentTitle(currentPresetName)
                .setContentText(song.getFilename());
        mService.startForeground(NOTIFICATION_ID, builder.build());
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private void createNotificationChannel() {
        val chan = new NotificationChannel(CHANNEL_ID,
                mService.getString(R.string.playlist_now_playing_notificatoin), NotificationManager.IMPORTANCE_HIGH);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        chan.setLightColor(Color.BLUE);
        notificationManager.createNotificationChannel(chan);
    }

    public void removeNotification() {
        mService.stopForeground(false);
        NotificationManagerCompat.from(mService).cancel(NOTIFICATION_ID);
    }
}
