package pl.qprogramming.themplay.util;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import pl.qprogramming.themplay.playlist.Playlist;
import pl.qprogramming.themplay.playlist.Song;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static pl.qprogramming.themplay.util.Utils.createPlaylist;

public class UtilsTest {

    @Test
    public void testShufflePlaylist() {
        Playlist playlist = Playlist.builder().name("playlist").songs(createSongs()).build();
        playlist.setCurrentSong(playlist.getSongs().get(0));
        createPlaylist(playlist, true);
        assertTrue(playlist.getPlaylist().size() > 0);
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