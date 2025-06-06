package pl.qprogramming.themplay.domain;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

import java.io.Serializable;
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
@Entity(tableName = Song.SONG_TABLE_NAME,
        foreignKeys = @ForeignKey(entity = Playlist.class,
                parentColumns = Playlist.COLUMN_ID,
                childColumns = Song.COLUMN_PLAYLIST_OWNER_ID,
                onDelete = ForeignKey.CASCADE
        )
)
public class Song implements Serializable, Cloneable {
    public static final String SONG_TABLE_NAME = "song";
    public static final String CURRENT_POSITION = "currentPosition";
    public static final String COLUMN_ID = "id";
    public static final String FILENAME = "filename";
    public static final String COLUMN_PLAYLIST_OWNER_ID = "playlist_owner_id";

    @PrimaryKey(autoGenerate = true)
    private Long id;
    @ColumnInfo(name = FILENAME)
    private String filename;
    private String fileUri;
    private String filePath;
    @ColumnInfo(name = CURRENT_POSITION)
    private int currentPosition;
    private boolean selected;
    @ColumnInfo(name = COLUMN_PLAYLIST_OWNER_ID, index = true)
    private Long playlistOwnerId;


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Song song = (Song) o;
        return id.equals(song.id) &&
                filename.equals(song.filename);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, filename);
    }

    @NonNull
    @Override
    public Song clone() throws CloneNotSupportedException {
        val song = (Song) super.clone();
        song.setId(null);
        song.setPlaylistOwnerId(null);
        return song;
    }
}
