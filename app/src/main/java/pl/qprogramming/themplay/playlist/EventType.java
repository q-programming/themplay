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
    PLAYLIST_NOTIFICATION_PLAY("q-programming.themplay.playlist.play"),
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
