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
    public static final String PLAYLIST_POSITION = "playlist_position";
    public static final String COLUMN_ID = "id";
    public static final String FILENAME = "filename";
    public static final String ARTIST = "artist";
    public static final String TITLE = "title";
    public static final String COLUMN_PLAYLIST_OWNER_ID = "playlist_owner_id";

    @PrimaryKey(autoGenerate = true)
    private Long id;
    @ColumnInfo(name = FILENAME)
    private String filename;
    private String fileUri;
    private String filePath;
    @ColumnInfo(name = ARTIST)
    private String artist;
    @ColumnInfo(name = TITLE)
    private String title;
    @ColumnInfo(name = CURRENT_POSITION)
    private int currentPosition;
    private boolean selected;
    @ColumnInfo(name = PLAYLIST_POSITION)
    private int playlistPosition;
    @ColumnInfo(name = COLUMN_PLAYLIST_OWNER_ID, index = true)
    private Long playlistOwnerId;


    /**
     * Generates a display name for the song based on available metadata.
     * Logic:
     * 1. If Title is present:
     * a. If Artist is also present: "Artist - Title"
     * b. If Artist is NOT present: "Title"
     * 2. If Title is NOT present: "filename"
     *
     * @return A user-friendly display name for the song.
     */
    public String getDisplayName() {
        val currentTitle = (title != null && !title.trim().isEmpty()) ? title.trim() : null;
        val currentArtist = (artist != null && !artist.trim().isEmpty()) ? artist.trim() : null;
        val currentFilename = (filename != null && !filename.trim().isEmpty()) ? filename.trim() : "Unknown File";
        if (currentTitle != null) {
            if (currentArtist != null) {
                return currentArtist + " - " + currentTitle;
            } else {
                return currentTitle;
            }
        } else {
            return currentFilename;
        }
    }

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
