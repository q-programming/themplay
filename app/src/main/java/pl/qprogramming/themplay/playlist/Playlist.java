package pl.qprogramming.themplay.playlist;


import com.reactiveandroid.Model;
import com.reactiveandroid.annotation.Column;
import com.reactiveandroid.annotation.PrimaryKey;
import com.reactiveandroid.annotation.Table;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import androidx.annotation.NonNull;
import io.reactivex.Single;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@Table(name = Playlist.PLAYLIST_TABLE_NAME, database = ThemPlayDatabase.class)
public class Playlist extends Model implements Serializable {
    public static final String CURRENT_SONG = "currentSong";

    public static final String PLAYLIST_TABLE_NAME = "playlists";
    public static final String ACTIVE = "active";
    public static final String CREATED_AT = "created_at";
    public static final String UPDATED_AT = "updated_at";
    public static final String SONG_COUNT = "songs_count";
    public static final String PRESET = "preset";
    public static final String BACKGROUND = "background";
    public static final String TEXT_COLOR = "text_color";
    public static final String NAME = "name";
    public static final String TEXT_OUTLINE = "text_outline" ;
    @PrimaryKey
    private Long id;
    @Column(name = NAME)
    private String name;
    @Column(name = CURRENT_SONG)
    private Song currentSong;

    @Column(name = ACTIVE)
    private boolean active;
    @Column(name = CREATED_AT)
    private Date createdAt;
    @Column(name = UPDATED_AT)
    private Date updatedAt;
    @Column(name = SONG_COUNT)
    private int songCount;
    @Column(name = PRESET)
    private String preset;
    @Column(name = BACKGROUND)
    private transient String backgroundImage;
    @Column(name = TEXT_COLOR)
    private int textColor;
    @Column(name = TEXT_OUTLINE)
    private boolean textOutline;


    private List<Song> songs;

    private List<Song> playlist;

    public List<Song> getSongs() {
        if (songs == null) {
            songs = new ArrayList<>();
        }
        return songs;
    }

    public void addSong(Song song) {
        getSongs().add(song);
        songCount = getSongs().size();
    }

    @NonNull
    @Override
    public Long save() {
        if (id == null) {
            createdAt = new Date();
        }
        updatedAt = new Date();
        return super.save();
    }

    @NonNull
    @Override
    public Single<Long> saveAsync() {
        if (id == null) {
            createdAt = new Date();
        }
        updatedAt = new Date();
        return super.saveAsync();
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
}
