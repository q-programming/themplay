package pl.qprogramming.themplay.player;

import static pl.qprogramming.themplay.playlist.EventType.PLAYBACK_NOTIFICATION_NEXT;
import static pl.qprogramming.themplay.playlist.EventType.PLAYBACK_NOTIFICATION_PAUSE;
import static pl.qprogramming.themplay.playlist.EventType.PLAYBACK_NOTIFICATION_PLAY;
import static pl.qprogramming.themplay.playlist.EventType.PLAYBACK_NOTIFICATION_PREV;
import static pl.qprogramming.themplay.playlist.EventType.PLAYBACK_NOTIFICATION_STOP;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.media3.common.util.UnstableApi;

import lombok.val;
import pl.qprogramming.themplay.MainActivity;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.domain.Song;
import pl.qprogramming.themplay.logger.Logger;
import pl.qprogramming.themplay.playlist.EventType;

public class MediaNotificationManager {
    private static final String TAG = MediaNotificationManager.class.getSimpleName();

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
    @UnstableApi
    public void createMediaNotification(Song song, String playlistTitle, boolean pause) {
        Intent openAppIntent = new Intent(mService, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(mService, 0,
                openAppIntent, PendingIntent.FLAG_MUTABLE);
        val builder = new NotificationCompat.Builder(mService, CHANNEL_ID)
                .setContentIntent(pendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSmallIcon(R.drawable.ic_logo)
                .addAction(R.drawable.ic_previous_32, "Previous", createServiceActionIntent(PLAYBACK_NOTIFICATION_PREV))
                .addAction(R.drawable.ic_stop_32, "Stop", createServiceActionIntent(PLAYBACK_NOTIFICATION_STOP));
        if (pause) {
            builder.addAction(R.drawable.ic_play_32, "Play", createServiceActionIntent(PLAYBACK_NOTIFICATION_PLAY));
        } else {
            builder.addAction(R.drawable.ic_pause_32, "Pause", createServiceActionIntent(PLAYBACK_NOTIFICATION_PAUSE));
        }
        builder.addAction(R.drawable.ic_next_32, "Next", createServiceActionIntent(PLAYBACK_NOTIFICATION_NEXT))
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle())
                .setContentTitle(playlistTitle)
                .setContentText(song.getFilename());
        mService.startForeground(NOTIFICATION_ID, builder.build());
        Logger.d(TAG, "Notification created song " + song.getFilename());
    }
    @UnstableApi
    public void createIdleNotification() {
        Logger.d(TAG, "Creating idle notification");
        Intent openAppIntent = new Intent(mService, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(mService, 0,
                openAppIntent, PendingIntent.FLAG_MUTABLE);
        val builder = new NotificationCompat.Builder(mService, CHANNEL_ID)
                .setContentIntent(pendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSmallIcon(R.drawable.ic_logo)
                .setContentTitle("Themplay");
        mService.startForeground(NOTIFICATION_ID, builder.build());
    }

    private void createNotificationChannel() {
        val chan = new NotificationChannel(CHANNEL_ID,
                mService.getString(R.string.playlist_now_playing_notificatoin), NotificationManager.IMPORTANCE_LOW);
        chan.setDescription(mService.getString(R.string.notificatoin_description));
        chan.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        chan.setSound(null, null);
        chan.enableVibration(false);
        chan.setLightColor(Color.BLUE);
        notificationManager.createNotificationChannel(chan);
    }

    @UnstableApi
    private PendingIntent createServiceActionIntent(EventType eventType) {
        Intent intent = new Intent(mService, Player.class);
        intent.setAction(eventType.getCode());
        int requestCode = eventType.ordinal();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return PendingIntent.getService(mService, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            return PendingIntent.getService(mService, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
    }

    public void removeNotification() {
        Logger.d(TAG, "Removing notification");
        mService.stopForeground(true);
        NotificationManagerCompat.from(mService).cancel(NOTIFICATION_ID);
    }
}
