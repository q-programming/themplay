package pl.qprogramming.themplay.domain;


import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.val;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
@Entity(tableName = Playlist.PLAYLIST_TABLE_NAME)
public class Playlist implements Serializable, Cloneable {
    public static final String COLUMN_ID = "id";
    public static final String CURRENT_SONG = "currentSong";
    public static final String CURRENT_SONG_ID = "current_song_Id";
    public static final String PLAYLIST_TABLE_NAME = "playlists";
    public static final String ACTIVE = "active";
    public static final String CREATED_AT = "created_at";
    public static final String UPDATED_AT = "updated_at";
    public static final String SONG_COUNT = "songs_count";
    public static final String PRESET = "preset";
    public static final String BACKGROUND = "background";
    public static final String TEXT_COLOR = "text_color";
    public static final String NAME = "name";
    public static final String TEXT_OUTLINE = "text_outline";
    public static final String POSITION = "position";
    @PrimaryKey
    private Long id;
    private String name;
    @Ignore
    private Song currentSong;
    @ColumnInfo(name = CURRENT_SONG_ID)
    private Long currentSongId;
    private boolean active;
    @ColumnInfo(name = CREATED_AT)
    @Builder.Default
    private Date createdAt = new Date();
    @ColumnInfo(name = UPDATED_AT)
    @Builder.Default
    private Date updatedAt = new Date();
    @ColumnInfo(name = SONG_COUNT)
    private int songCount;
    private String preset;
    @ColumnInfo(name = BACKGROUND)
    private transient String backgroundImage;
    @ColumnInfo(name = TEXT_COLOR)
    private int textColor;
    @ColumnInfo(name = TEXT_OUTLINE)
    private boolean textOutline;
    @ColumnInfo(name = POSITION)
    private int position;

    /**
     * All songs in this playlist
     */
    @Ignore
    private List<Song> songs;
    /**
     * Contains all songs which were shuffled or ordered upon loading this playlist
     * Application will consume this list to play songs one by one, upon reaching end ,
     * new playlists will be generated accordingly
     */
    @Ignore
    private List<Song> playlist;

    public List<Song> getSongs() {
        if (songs == null) {
            songs = new ArrayList<>();
        }
        return songs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Playlist playlist = (Playlist) o;
        return active == playlist.active &&
                id.equals(playlist.id) &&
                name.equals(playlist.name) &&
                createdAt.equals(playlist.createdAt) &&
                Objects.equals(updatedAt, playlist.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, active, createdAt, updatedAt);
    }

    @NonNull
    @Override
    public Playlist clone() throws CloneNotSupportedException {
        val playlist = (Playlist) super.clone();
        playlist.setId(null);
        playlist.setCurrentSongId(null);
        playlist.setCurrentSong(null);
        playlist.setCreatedAt(new Date());
        playlist.setActive(false);
        return playlist;
    }

}
