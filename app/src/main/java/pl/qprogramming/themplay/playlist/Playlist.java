package pl.qprogramming.themplay.playlist;


import com.reactiveandroid.Model;
import com.reactiveandroid.annotation.Column;
import com.reactiveandroid.annotation.PrimaryKey;
import com.reactiveandroid.annotation.Table;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import androidx.annotation.NonNull;
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
@Table(name = "playlists", database = ThemPlayDatabase.class)
public class Playlist extends Model implements Serializable {
    public static final String CURRENT_SONG = "currentSong";

    public static final String ACTIVE = "active";
    public static final String CREATED_AT = "created_at";
    public static final String UPDATED_AT = "updated_at";
    public static final String SONG_COUNT = "songs_count";
    public static final String NAME = "name";
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

    private List<Song> songs;

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

}
