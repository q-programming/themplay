package pl.qprogramming.themplay.playlist;

import com.reactiveandroid.Model;
import com.reactiveandroid.annotation.Column;
import com.reactiveandroid.annotation.ForeignKeyAction;
import com.reactiveandroid.annotation.PrimaryKey;
import com.reactiveandroid.annotation.Table;

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
@Table(name = "playlist_songs", database = ThemPlayDatabase.class)
public class PlaylistSongs extends Model {

    public static final String PLAYLIST = "playlist";
    public static final String SONG = "song";

    @PrimaryKey
    private Long id;
    @Column(name = PLAYLIST, onDelete = ForeignKeyAction.CASCADE)
    private Playlist playlist;
    @Column(name = SONG, onDelete = ForeignKeyAction.CASCADE)
    private Song song;

}
