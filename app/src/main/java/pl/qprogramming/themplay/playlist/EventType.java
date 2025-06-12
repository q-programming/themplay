package pl.qprogramming.themplay.playlist;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;

import lombok.Getter;


@Getter
public enum EventType {
    PLAYLIST_NOTIFICATION("q-programming.themplay.playlist"),
    PLAYLIST_NOTIFICATION_ADD("q-programming.themplay.playlist.added"),
    PLAYLIST_NOTIFICATION_DELETE("q-programming.themplay.playlist.delete"),
    PLAYLIST_NOTIFICATION_DELETE_SONGS("q-programming.themplay.playlist.delete.songs"),
    PLAYLIST_NOTIFICATION_MULTIPLE_SELECTED("q-programming.themplay.playlist.multiple"),
    PLAYLIST_NOTIFICATION_SOME_DELETE_SELECTED("q-programming.themplay.playlist.delete.songs.selected"),
    PLAYLIST_NOTIFICATION_SONGS_UPDATE_DONE("q-programming.themplay.playlist.update.songs.done"),
    PLAYLIST_NOTIFICATION_ACTIVE("q-programming.themplay.playlist.active"),
    PLAYLIST_NOTIFICATION_IS_ACTIVE_PLAYING("q-programming.themplay.playlist.is.active.playing"),
    PLAYLIST_NOTIFICATION_RECREATE_LIST("q-programming.themplay.playlist.recreate"),
    PLAYLIST_NOTIFICATION_NEW_ACTIVE("q-programming.themplay.playlist.newActive"),
    PLAYLIST_NOTIFICATION_PLAY("q-programming.themplay.playlist.play"),
    PLAYLIST_NOTIFICATION_PLAY_NO_SONGS("q-programming.themplay.playlist.play.nosongs"),
    PLAYLIST_NOTIFICATION_PAUSE("q-programming.themplay.playlist.pause"),
    PLAYLIST_NOTIFICATION_NEXT("q-programming.themplay.playlist.next"),
    PLAYLIST_NOTIFICATION_PREV("q-programming.themplay.playlist.prev"),
    PLAYLIST_NOTIFICATION_STOP("q-programming.themplay.playlist.stop"),
    PLAYLIST_CHANGE_BACKGROUND("q-programming.themplay.playlist.background"),
    PLAYBACK_NOTIFICATION_PLAY("q-programming.themplay.player.play"),
    PLAYBACK_NOTIFICATION_PAUSE("q-programming.themplay.player.pause"),
    PLAYBACK_NOTIFICATION_NEXT("q-programming.themplay.player.next"),
    PLAYBACK_NOTIFICATION_PREV("q-programming.themplay.player.prev"),
    PLAYBACK_NOTIFICATION_STOP("q-programming.themplay.player.stop"),
    PLAYBACK_NOTIFICATION_DELETE_NOT_FOUND("q-programming.themplay.player.delete.song"),
    PRESET_ACTIVATED("q-programming.themplay.preset.activated"),
    PRESET_REMOVED("q-programming.themplay.preset.removed"),
    PRESET_SAVE("q-programming.themplay.preset.save"),
    OPERATION_STARTED("q-programming.themplay.operation.started"),
    OPERATION_FINISHED("q-programming.themplay.operation.finished"),
    PLAYER_INIT_ACTION("q-programming.themplay.player.init"),
    UNKNOWN("q-programming.themplay.n/a");

    private static final Map<String, EventType> BY_CODE = new HashMap<>();

    static {
        for (EventType eType : values()) {
            BY_CODE.put(eType.code, eType);
        }
    }

    private final String code;

    EventType(String code) {
        this.code = code;
    }

    public static EventType getType(String type) {
        return BY_CODE.computeIfAbsent(type, s -> {
            Log.e("EventType", "Unknown type of Event " + type);
            return UNKNOWN;
        });
    }
}
