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
    PLAYLIST_NOTIFICATION_ACTIVE("q-programming.themplay.playlist.active"),
    PLAYLIST_NOTIFICATION_RECREATE_LIST("q-programming.themplay.playlist.recreate"),
    PLAYLIST_NOTIFICATION_NEW_ACTIVE("q-programming.themplay.playlist.newActive"),
    PLAYLIST_NOTIFICATION_PLAY("q-programming.themplay.playlist.play"),
    PLAYLIST_NOTIFICATION_PAUSE("q-programming.themplay.playlist.play"),
    PLAYLIST_NOTIFICATION_NEXT("q-programming.themplay.playlist.next"),
    PLAYLIST_NOTIFICATION_PREV("q-programming.themplay.playlist.prev"),
    PLAYLIST_NOTIFICATION_STOP("q-programming.themplay.playlist.stop"),
    PRESET_ACTIVATED("q-programming.themplay.preset.activated"),
    PRESET_REMOVED("q-programming.themplay.preset.removed"),
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
