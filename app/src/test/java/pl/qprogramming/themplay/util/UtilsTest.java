package pl.qprogramming.themplay.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static pl.qprogramming.themplay.util.Utils.createPlaylist;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

import pl.qprogramming.themplay.domain.Playlist;
import pl.qprogramming.themplay.domain.Song;
import pl.qprogramming.themplay.util.Utils.VersionComparisonResult;

@RunWith(RobolectricTestRunner.class)
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

    @Test
    public void compareVersions_nullOrEmpty_returnsError() {
        assertEquals(VersionComparisonResult.ERROR_PARSING, Utils.compareVersions(null, "1.0.0"));
        assertEquals(VersionComparisonResult.ERROR_PARSING, Utils.compareVersions("1.0.0", null));
        assertEquals(VersionComparisonResult.ERROR_PARSING, Utils.compareVersions("", "1.0.0"));
        assertEquals(VersionComparisonResult.ERROR_PARSING, Utils.compareVersions("1.0.0", ""));
    }

    @Test
    public void compareVersions_nonNumericPartsAreTreatedAsZero_leadingToSameOrDifferent() {
        assertEquals(VersionComparisonResult.VERSIONS_ARE_SAME, Utils.compareVersions("a.b.c", "d.e.f")); // "0.0.0" vs "0.0.0"
        assertEquals(VersionComparisonResult.STORED_IS_NEWER, Utils.compareVersions("1.alpha.2", "1.0.1"));   // "1.0.2" vs "1.0.1"
        assertEquals(VersionComparisonResult.CURRENT_IS_NEWER, Utils.compareVersions("1.0.1", "1.alpha.2"));   // "1.0.1" vs "1.0.2"
    }

    // Test for VERSIONS_ARE_SAME
    @Test
    public void compareVersions_identicalStrings_returnsSame() {
        assertEquals(VersionComparisonResult.VERSIONS_ARE_SAME, Utils.compareVersions("1.0.0", "1.0.0"));
        assertEquals(VersionComparisonResult.VERSIONS_ARE_SAME, Utils.compareVersions("1.2.3-beta", "1.2.3-beta"));
    }

    @Test
    public void compareVersions_semanticallySame_returnsSame() {
        assertEquals(VersionComparisonResult.VERSIONS_ARE_SAME, Utils.compareVersions("1.0", "1.0.0"));
        assertEquals(VersionComparisonResult.VERSIONS_ARE_SAME, Utils.compareVersions("1.0.0", "1.0"));
        assertEquals(VersionComparisonResult.VERSIONS_ARE_SAME, Utils.compareVersions("1", "1.0.0"));
        assertEquals(VersionComparisonResult.VERSIONS_ARE_SAME, Utils.compareVersions("1.0.0", "1"));
    }

    @Test
    public void compareVersions_sameWithSuffixes_returnsSame() {
        assertEquals(VersionComparisonResult.VERSIONS_ARE_SAME, Utils.compareVersions("1.2.3-beta", "1.2.3"));
        assertEquals(VersionComparisonResult.VERSIONS_ARE_SAME, Utils.compareVersions("1.2.3", "1.2.3-alpha"));
        assertEquals(VersionComparisonResult.VERSIONS_ARE_SAME, Utils.compareVersions("1.0.0-rc1", "1.0.0-rc2"));
    }

    @Test
    public void compareVersions_differentLengthsButSemanticallySame_returnsSame() {
        assertEquals(VersionComparisonResult.VERSIONS_ARE_SAME, Utils.compareVersions("1.2", "1.2.0.0"));
        assertEquals(VersionComparisonResult.VERSIONS_ARE_SAME, Utils.compareVersions("1.2.0.0", "1.2"));
        assertEquals(VersionComparisonResult.VERSIONS_ARE_SAME, Utils.compareVersions("1.0.0-beta", "1.0"));
    }

    // Test for CURRENT_IS_NEWER (version2 > version1)
    @Test
    public void compareVersions_currentIsNewer_major() {
        assertEquals(VersionComparisonResult.CURRENT_IS_NEWER, Utils.compareVersions("1.0.0", "2.0.0"));
    }

    @Test
    public void compareVersions_currentIsNewer_minor() {
        assertEquals(VersionComparisonResult.CURRENT_IS_NEWER, Utils.compareVersions("1.0.0", "1.1.0"));
    }

    @Test
    public void compareVersions_currentIsNewer_patch() {
        assertEquals(VersionComparisonResult.CURRENT_IS_NEWER, Utils.compareVersions("1.0.0", "1.0.1"));
    }

    @Test
    public void compareVersions_currentIsNewer_withSuffixes() {
        assertEquals(VersionComparisonResult.CURRENT_IS_NEWER, Utils.compareVersions("1.0.0-beta", "1.0.1-alpha"));
        assertEquals(VersionComparisonResult.CURRENT_IS_NEWER, Utils.compareVersions("1.0.0", "1.0.1-rc1"));
    }

    @Test
    public void compareVersions_currentIsNewer_differentLengths() {
        assertEquals(VersionComparisonResult.CURRENT_IS_NEWER, Utils.compareVersions("1.0", "1.0.1"));
        assertEquals(VersionComparisonResult.CURRENT_IS_NEWER, Utils.compareVersions("1.2.3", "1.3"));
    }

    // Test for STORED_IS_NEWER (version1 > version2)
    @Test
    public void compareVersions_storedIsNewer_major() {
        assertEquals(VersionComparisonResult.STORED_IS_NEWER, Utils.compareVersions("2.0.0", "1.0.0"));
    }

    @Test
    public void compareVersions_storedIsNewer_minor() {
        assertEquals(VersionComparisonResult.STORED_IS_NEWER, Utils.compareVersions("1.1.0", "1.0.0"));
    }

    @Test
    public void compareVersions_storedIsNewer_patch() {
        assertEquals(VersionComparisonResult.STORED_IS_NEWER, Utils.compareVersions("1.0.1", "1.0.0"));
    }

    @Test
    public void compareVersions_storedIsNewer_withSuffixes() {
        assertEquals(VersionComparisonResult.STORED_IS_NEWER, Utils.compareVersions("1.0.1-alpha", "1.0.0-beta"));
        assertEquals(VersionComparisonResult.STORED_IS_NEWER, Utils.compareVersions("1.0.1-rc1", "1.0.0"));
    }

    @Test
    public void compareVersions_storedIsNewer_differentLengths() {
        assertEquals(VersionComparisonResult.STORED_IS_NEWER, Utils.compareVersions("1.0.1", "1.0"));
        assertEquals(VersionComparisonResult.STORED_IS_NEWER, Utils.compareVersions("1.3", "1.2.3"));
    }

    // Edge cases implicitly covered by the above, e.g. leading zeros, empty parts.
    @Test
    public void compareVersions_leadingZerosInParts_handledAsNumbers() {
        assertEquals(VersionComparisonResult.VERSIONS_ARE_SAME, Utils.compareVersions("1.01.0", "1.1.0"));
        assertEquals(VersionComparisonResult.CURRENT_IS_NEWER, Utils.compareVersions("1.01.0", "1.2.0"));
    }

    @Test
    public void compareVersions_emptyPartBetweenDots_treatedAsZero() {
        assertEquals(VersionComparisonResult.VERSIONS_ARE_SAME, Utils.compareVersions("1..2", "1.0.2"));
        assertEquals(VersionComparisonResult.CURRENT_IS_NEWER, Utils.compareVersions("1..2", "1.0.3")); // 1.0.2 vs 1.0.3
        assertEquals(VersionComparisonResult.STORED_IS_NEWER, Utils.compareVersions("1.1.2", "1..2"));   // 1.1.2 vs 1.0.2
    }
}