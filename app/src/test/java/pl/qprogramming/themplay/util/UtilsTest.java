package pl.qprogramming.themplay.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static pl.qprogramming.themplay.util.Utils.createPlaylist;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import pl.qprogramming.themplay.domain.Playlist;
import pl.qprogramming.themplay.domain.Song;

public class UtilsTest {

    @Test
    public void testShufflePlaylist() {
        Playlist playlist = Playlist.builder().name("playlist").songs(createSongs()).build();
        playlist.setCurrentSong(playlist.getSongs().get(0));
        createPlaylist(playlist, true);
        assertFalse(playlist.getPlaylist().isEmpty());
        assertEquals(playlist.getPlaylist().size(), playlist.getSongs().size());
        assertEquals(0L, (long) playlist.getPlaylist().get(playlist.getPlaylist().size() - 1).getId());
    }


    private List<Song> createSongs() {
        List<Song> songs = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            songs.add(Song.builder().id((long) i).filename("file" + i).build());
        }
        return songs;
    }
}