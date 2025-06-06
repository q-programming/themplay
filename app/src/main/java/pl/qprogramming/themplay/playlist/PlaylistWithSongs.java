package pl.qprogramming.themplay.playlist;

import androidx.room.Embedded;
import androidx.room.Relation;

import java.util.List;

import pl.qprogramming.themplay.domain.Playlist;
import pl.qprogramming.themplay.domain.Song;

public class PlaylistWithSongs {
    @Embedded
    public Playlist playlist;

    @Relation(
            parentColumn = Playlist.COLUMN_ID,
            entityColumn = Song.COLUMN_PLAYLIST_OWNER_ID,
            entity = Song.class
    )
    public List<Song> songs;
}
