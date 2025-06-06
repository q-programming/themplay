package pl.qprogramming.themplay.playlist;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import lombok.val;
import pl.qprogramming.themplay.db.ThemplayDatabase;
import pl.qprogramming.themplay.domain.Playlist;
import pl.qprogramming.themplay.domain.Song;
import pl.qprogramming.themplay.playlist.exceptions.PlaylistNotFoundException;
import pl.qprogramming.themplay.repository.PlaylistRepository;
import pl.qprogramming.themplay.repository.PresetRepository;
import pl.qprogramming.themplay.repository.SongRepository;
import pl.qprogramming.themplay.settings.Property;
import pl.qprogramming.themplay.util.RxImmediateSchedulerRule;

@Config(sdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@RunWith(RobolectricTestRunner.class)
@SuppressWarnings("unchecked")
public class PlaylistServiceTest {

    @Rule
    public RxImmediateSchedulerRule testSchedulerRule = new RxImmediateSchedulerRule();

    @Mock
    private PlaylistRepository mockPlaylistRepository;

    @Mock
    private PresetRepository mockPresetRepository;
    @Mock
    private SongRepository mockSongRepository;
    @Mock
    private SharedPreferences mockSharedPreferences;
    @Mock
    private SharedPreferences.Editor mockEditor;
    @Mock
    private Toast mockToast;
    @Mock
    private CompositeDisposable mockDisposables;

    @Captor
    private ArgumentCaptor<Playlist> playlistArgumentCaptor;
    @Captor
    private ArgumentCaptor<List<Playlist>> playlistsCaptor;
    @Captor
    private ArgumentCaptor<EventType> eventTypeCaptor;
    @Captor
    private ArgumentCaptor<Object> eventDataCaptor;
    @Captor
    private ArgumentCaptor<Throwable> throwableCaptor;

    private PlaylistService playlistService;

    // Static mock for Log, useful if your code directly uses android.util.Log
    // Be cautious with static mocks; they can be tricky.
    private static MockedStatic<Log> mockedLog;
    private static MockedStatic<androidx.preference.PreferenceManager> mockedPrefManager;
    private static MockedStatic<ThemplayDatabase> mockedStaticThemplayDatabase;
    private AutoCloseable mockitoSession;

    @Before
    public void setUp() {
        mockitoSession = MockitoAnnotations.openMocks(this);
        mockedStaticThemplayDatabase = Mockito.mockStatic(ThemplayDatabase.class);
        ThemplayDatabase mockDatabaseInstance = mock(ThemplayDatabase.class);
        mockedStaticThemplayDatabase.when(() -> ThemplayDatabase.getDatabase(any(Context.class)))
                .thenReturn(mockDatabaseInstance);
        // 2. Configure the mockDatabaseInstance to return our mock repositories
        mockPlaylistRepository = mock(PlaylistRepository.class);
        mockSongRepository = mock(SongRepository.class);
        mockPresetRepository = mock(PresetRepository.class);

        when(mockDatabaseInstance.playlistRepository()).thenReturn(mockPlaylistRepository);
        when(mockDatabaseInstance.songRepository()).thenReturn(mockSongRepository);
        when(mockDatabaseInstance.presetRepository()).thenReturn(mockPresetRepository);
        mockedPrefManager = Mockito.mockStatic(androidx.preference.PreferenceManager.class);
        when(androidx.preference.PreferenceManager.getDefaultSharedPreferences(any()))
                .thenReturn(mockSharedPreferences);
        //create service
        val controller = Robolectric.buildService(PlaylistService.class);
        playlistService = controller.create().get();
        when(mockSharedPreferences.edit()).thenReturn(mockEditor);
    }

    @After
    public void tearDown() throws Exception {
        if (mockedStaticThemplayDatabase != null) {
            mockedStaticThemplayDatabase.close();
        }
        if (mockedPrefManager != null) {
            mockedPrefManager.close();
        }
        if (mockitoSession != null) {
            mockitoSession.close();
        }
    }

    private Playlist createDummyPlaylist(long id, String name, String preset, int position) {
        return Playlist.builder()
                .id(id)
                .name(name)
                .preset(preset)
                .position(position)
                .createdAt(new Date())
                .updatedAt(new Date())
                .active(false)
                .songCount(0)
                .songs(new ArrayList<>())
                .build();
    }

    private Song createDummySong(long id, String filename) {
        Song song = new Song();
        song.setId(id);
        song.setFilename(filename);
        return song;
    }

    @Test
    public void onBind_whenNoPlaylistsAreActive_doesNotUpdatePlaylists() {
        // Arrange
        when(mockPlaylistRepository.findAllByActive()).thenReturn(Maybe.just(Collections.emptyList()));
        // Act
        playlistService.onBind(new Intent()); // Trigger the method
        // Assert
        verify(mockPlaylistRepository).findAllByActive();
        verify(mockPlaylistRepository, never()).update(any(Playlist.class));
    }

    @Test
    public void onBind_whenOnePlaylistIsActive_doesNotUpdatePlaylists() {
        // Arrange
        Playlist activePlaylist = createDummyPlaylist(1L, "Active Only", "PresetA", 0);
        activePlaylist.setActive(true);
        List<Playlist> activePlaylists = Collections.singletonList(activePlaylist);
        when(mockPlaylistRepository.findAllByActive()).thenReturn(Maybe.just(activePlaylists));
        // Act
        playlistService.onBind(new Intent());
        // Assert
        verify(mockPlaylistRepository).findAllByActive();
        verify(mockPlaylistRepository, never()).update(any(Playlist.class));
    }

    @Test
    public void onBind_whenMultiplePlaylistsAreActive_deactivatesExtrasKeepingFirst() throws InterruptedException { // Add InterruptedException
        // Arrange
        Playlist p1 = createDummyPlaylist(1L, "P1 Active", "PresetA", 0);
        p1.setActive(true);
        Playlist p2 = createDummyPlaylist(2L, "P2 To Deactivate", "PresetA", 1);
        p2.setActive(true);
        Playlist p3 = createDummyPlaylist(3L, "P3 To Deactivate", "PresetA", 2);
        p3.setActive(true);

        List<Playlist> multipleActivePlaylists = new ArrayList<>();
        multipleActivePlaylists.add(p1);
        multipleActivePlaylists.add(p2);
        multipleActivePlaylists.add(p3);
        CountDownLatch latch = new CountDownLatch(1);
        when(mockPlaylistRepository.findAllByActive()).thenReturn(Maybe.just(multipleActivePlaylists));
        // Modify the stub for updateAll to count down the latch so that it is not failing in suit run
        when(mockPlaylistRepository.updateAll(anyList())).thenAnswer(invocation -> {
            latch.countDown();
            return Completable.complete();
        });
        // Act
        playlistService.onBind(new Intent());
        // Wait for the latch
        if (!latch.await(5, TimeUnit.SECONDS)) {
            fail("Latch timed out, updateAll was likely not called or didn't count down.");
        }
        // Assert
        verify(mockPlaylistRepository, times(1)).findAllByActive();
        verify(mockPlaylistRepository, times(1)).updateAll(playlistsCaptor.capture());
        List<Playlist> updatedPlaylists = playlistsCaptor.getValue();
        assertEquals(2, updatedPlaylists.size());
        assertFalse(updatedPlaylists.get(0).isActive());
        assertFalse(updatedPlaylists.get(1).isActive());
    }

    @Test
    public void onBind_whenFindAllByActiveFails_logsError() {
        // Arrange
        RuntimeException testException = new RuntimeException("DB error!");
        when(mockPlaylistRepository.findAllByActive()).thenReturn(Maybe.error(testException));
        // Act
        playlistService.onBind(new Intent());
        // Assert
        verify(mockPlaylistRepository).findAllByActive();
        verify(mockPlaylistRepository, never()).update(any(Playlist.class)); // No updates should be attempted
    }

    // find all by preset

    @Test
    public void getAllByPresetName_whenPresetExists_returnsPlaylists() {
        // Arrange
        String testPresetName = "MyTestPreset";
        List<Playlist> expectedPlaylists = new ArrayList<>();
        expectedPlaylists.add(createDummyPlaylist(1L, "Playlist 1", testPresetName, 0));
        expectedPlaylists.add(createDummyPlaylist(2L, "Playlist 2", testPresetName, 1));
        when(mockSharedPreferences.getString(Property.CURRENT_PRESET, null)).thenReturn(testPresetName);
        when(mockPlaylistRepository.findAllByPreset(testPresetName)).thenReturn(Single.just(expectedPlaylists));
        // Act
        Single<List<Playlist>> resultSingle = playlistService.getAllByPresetName();
        // Assert
        val testObserver = resultSingle.test();
        testObserver.assertComplete()
                .assertNoErrors()
                .assertValueCount(1);
        val actualPlaylists = testObserver.values().get(0); // Get the emitted list
        assertEquals("Number of playlists should match", expectedPlaylists.size(), actualPlaylists.size());
        Assert.assertTrue("Actual playlists should contain all expected playlists", actualPlaylists.containsAll(expectedPlaylists));
        verify(mockSharedPreferences).getString(Property.CURRENT_PRESET, null);
        verify(mockPlaylistRepository).findAllByPreset(testPresetName);
    }

    @Test
    public void getAllByPresetName_whenRepositoryErrors_propagatesError() {
        // Arrange
        String testPresetName = "ErrorPreset";
        RuntimeException testException = new RuntimeException("Database connection failed!");
        when(mockSharedPreferences.getString(Property.CURRENT_PRESET, null)).thenReturn(testPresetName);
        when(mockPlaylistRepository.findAllByPreset(testPresetName)).thenReturn(Single.error(testException));
        // Act
        val resultSingle = playlistService.getAllByPresetName();
        // Assert
        val testObserver = resultSingle.test();

        testObserver.assertError(testException)
                .assertNotComplete()
                .assertNoValues();
        verify(mockSharedPreferences).getString(Property.CURRENT_PRESET, null);
        verify(mockPlaylistRepository).findAllByPreset(testPresetName);
    }

    @Test
    public void getAllByPresetName_whenNoPlaylistsForPreset_returnsEmptyList() {
        // Arrange
        String testPresetName = "EmptyPreset";
        List<Playlist> emptyPlaylists = Collections.emptyList();
        when(mockSharedPreferences.getString(Property.CURRENT_PRESET, null)).thenReturn(testPresetName);
        when(mockPlaylistRepository.findAllByPreset(testPresetName)).thenReturn(Single.just(emptyPlaylists));
        // Act
        val resultSingle = playlistService.getAllByPresetName();
        // Assert
        val testObserver = resultSingle.test();
        testObserver.assertComplete()
                .assertNoErrors()
                .assertValue(emptyPlaylists);
        Assert.assertTrue("Emitted list should be empty", testObserver.values().get(0).isEmpty());
        verify(mockPlaylistRepository).findAllByPreset(testPresetName);
    }

    @Test
    public void getAllByPresetName_whenPresetNameIsNull_callsRepositoryWithNull() {
        // Arrange
        List<Playlist> expectedPlaylistsForNullPreset = Collections.emptyList(); // Or handle error
        when(mockSharedPreferences.getString(Property.CURRENT_PRESET, null)).thenReturn(null);
        when(mockPlaylistRepository.findAllByPreset(null)).thenReturn(Single.just(expectedPlaylistsForNullPreset));
        // Act
        val resultSingle = playlistService.getAllByPresetName();
        verify(mockPlaylistRepository).findAllByPreset(null);
        // Assert
        val testObserver = resultSingle.test();
        testObserver.assertComplete()
                .assertNoErrors()
                .assertValue(expectedPlaylistsForNullPreset);
    }

    // get all by preset

    @Test
    public void getAllByPresetName_withCallbacks_success_callsOnPlaylistsReceived() {
        String testPresetName = "TestPreset";
        Playlist p1 = createDummyPlaylist(1L, "P1", testPresetName, 0);
        Playlist p2 = createDummyPlaylist(2L, "P2", testPresetName, 1);
        List<Playlist> initialPlaylists = Arrays.asList(p1, p2);
        Song song1ForP1 = createDummySong(101L, "SongA");
        Song song2ForP1 = createDummySong(102L, "SongB");
        Song song1ForP2 = createDummySong(201L, "SongC");
        // Arrange
        when(mockPlaylistRepository.findAllByPreset(testPresetName)).thenReturn(Single.just(initialPlaylists));
        when(mockSongRepository.getSongsForPlaylist(p1.getId())).thenReturn(Single.just(Arrays.asList(song1ForP1, song2ForP1)));
        when(mockSongRepository.getSongsForPlaylist(p2.getId())).thenReturn(Single.just(Collections.singletonList(song1ForP2)));

        Consumer<List<Playlist>> mockOnPlaylistsReceived = mock(Consumer.class);
        Consumer<Throwable> mockOnError = mock(Consumer.class);

        // Act
        playlistService.getAllByPresetName(testPresetName, mockOnPlaylistsReceived, mockOnError);

        // Assert

        verify(mockOnPlaylistsReceived).accept(playlistsCaptor.capture());
        verify(mockOnError, never()).accept(any(Throwable.class));

        List<Playlist> receivedPlaylists = playlistsCaptor.getValue();
        assertEquals(2, receivedPlaylists.size());

        Playlist resultP1 = receivedPlaylists.stream().filter(p -> p.getId().equals(p1.getId())).findFirst().orElse(null);
        Playlist resultP2 = receivedPlaylists.stream().filter(p -> p.getId().equals(p2.getId())).findFirst().orElse(null);

        Assert.assertNotNull(resultP1);
        assertEquals(2, resultP1.getSongs().size());
        Assert.assertTrue(resultP1.getSongs().contains(song1ForP1));
        Assert.assertTrue(resultP1.getSongs().contains(song2ForP1));

        Assert.assertNotNull(resultP2);
        assertEquals(1, resultP2.getSongs().size());
        Assert.assertTrue(resultP2.getSongs().contains(song1ForP2));
    }

    @Test
    public void getAllByPresetName_withCallbacks_initialFindAllFails_callsOnError() {
        String testPresetName = "ErrorPreset";
        RuntimeException testException = new RuntimeException("DB findAll error");
        // Arrange
        when(mockPlaylistRepository.findAllByPreset(testPresetName)).thenReturn(Single.error(testException));
        val mockOnPlaylistsReceived = mock(Consumer.class);
        val mockOnError = mock(Consumer.class);
        // Act
        playlistService.getAllByPresetName(testPresetName, mockOnPlaylistsReceived, mockOnError);
        // Assert
        verify(mockOnError).accept(throwableCaptor.capture());
        verify(mockOnPlaylistsReceived, never()).accept(anyList());
        assertEquals(testException, throwableCaptor.getValue());
    }

    @Test
    public void getAllByPresetName_withCallbacks_loadSongsFailsForOnePlaylist_callsOnError() {
        String testPresetName = "MixedSuccessPreset";
        Playlist p1 = createDummyPlaylist(1L, "P1-Success", testPresetName, 0);
        Playlist p2 = createDummyPlaylist(2L, "P2-FailLoad", testPresetName, 1);
        List<Playlist> initialPlaylists = Arrays.asList(p1, p2);
        RuntimeException songLoadException = new RuntimeException("Song loading failed for P2");
        Song song1ForP1 = createDummySong(101L, "SongA");
        // Arrange
        when(mockPlaylistRepository.findAllByPreset(testPresetName)).thenReturn(Single.just(initialPlaylists));
        when(mockSongRepository.getSongsForPlaylist(p1.getId())).thenReturn(Single.just(Collections.singletonList(song1ForP1)));
        when(mockSongRepository.getSongsForPlaylist(p2.getId())).thenReturn(Single.error(songLoadException)); // p2 song loading fails
        Consumer<List<Playlist>> mockOnPlaylistsReceived = mock(Consumer.class);
        Consumer<Throwable> mockOnError = mock(Consumer.class);
        // Act
        playlistService.getAllByPresetName(testPresetName, mockOnPlaylistsReceived, mockOnError);
        // Assert
        verify(mockOnError).accept(throwableCaptor.capture());
        verify(mockOnPlaylistsReceived, never()).accept(anyList());
        Assert.assertEquals(songLoadException, throwableCaptor.getValue());
    }


    @Test
    public void getAllByPresetName_withCallbacks_noInitialPlaylists_callsOnPlaylistsReceivedWithEmptyList() {
        String testPresetName = "EmptyPreset";
        // Arrange
        when(mockPlaylistRepository.findAllByPreset(testPresetName)).thenReturn(Single.just(Collections.emptyList()));
        Consumer<List<Playlist>> mockOnPlaylistsReceived = mock(Consumer.class);
        Consumer<Throwable> mockOnError = mock(Consumer.class);
        // Act
        playlistService.getAllByPresetName(testPresetName, mockOnPlaylistsReceived, mockOnError);
        // Assert
        verify(mockOnPlaylistsReceived).accept(playlistsCaptor.capture());
        verify(mockOnError, never()).accept(any(Throwable.class));
        Assert.assertTrue(playlistsCaptor.getValue().isEmpty());
    }


    // Paste

    @Test
    public void paste_whenPresetNotSet_throwsIllegalStateException() {
        when(mockSharedPreferences.getString(Property.CURRENT_PRESET, null)).thenReturn(null);
        try {
            playlistService.paste(1L, playlist -> fail("Callback should not be called"));
            fail("Expected IllegalStateException was not thrown");
        } catch (IllegalStateException e) {
            assertEquals("Current preset name is not set.", e.getMessage());
        } catch (Exception e) {
            fail("Unexpected exception thrown: " + e.getClass().getName());
        }
        verify(mockPlaylistRepository, never()).findOneById(anyLong());
    }

    @Test
    public void paste_whenOriginalPlaylistNotFound_logsError() {
        String currentPreset = "TestPreset";
        long playlistToCopyId = 1L;
        when(mockSharedPreferences.getString(Property.CURRENT_PRESET, null)).thenReturn(currentPreset);
        when(mockPlaylistRepository.findOneById(playlistToCopyId)).thenReturn(Maybe.empty());
        verify(mockPlaylistRepository, never()).create(any(Playlist.class));
    }


    @Test
    public void paste_successful_clonesAndSavesWithUniqueName() throws CloneNotSupportedException, PlaylistNotFoundException, InterruptedException {
        String currentPreset = "TestPreset";
        long originalId = 1L;
        String originalName = "My Playlist";
        Playlist originalPlaylist = createDummyPlaylist(originalId, originalName, "OldPreset", 0);
        originalPlaylist.setSongs(Collections.singletonList(createDummySong(101L, "song1.mp3")));
        originalPlaylist.setSongCount(1);
        when(mockSharedPreferences.getString(Property.CURRENT_PRESET, null)).thenReturn(currentPreset);
        when(mockPlaylistRepository.findOneById(originalId)).thenReturn(Maybe.just(originalPlaylist));

        // Scenario: "My Playlist" exists, "My Playlist_1" does not.
        when(mockPlaylistRepository.countByPresetNameAndName(currentPreset, "My Playlist")).thenReturn(Single.just(1)); // Exists
        when(mockPlaylistRepository.countByPresetNameAndName(currentPreset, "My Playlist_1")).thenReturn(Single.just(0)); // Available

        when(mockPlaylistRepository.countAllByPreset(currentPreset)).thenReturn(Single.just(5)); // 5 existing playlists, new one is 6th (pos 5)
        when(mockPlaylistRepository.create(any(Playlist.class))).thenReturn(Single.just(2L));
        // Mock cloneSongs
        List<Song> originalSongs = new ArrayList<>();
        originalSongs.add(createDummySong(10L, "songA.mp3"));
        when(mockSongRepository.getSongsForPlaylist(originalId)).thenReturn(Single.just(originalSongs));
        when(mockSongRepository.createAll(anyList())).thenReturn(Single.just(List.of(11L)));
        when(mockPlaylistRepository.update(any(Playlist.class))).thenAnswer(invocation -> Completable.complete());
        AtomicReference<Playlist> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        playlistService.paste(originalId, playlist -> {
            result.set(playlist);
            latch.countDown();
        });

        if (!latch.await(5, TimeUnit.SECONDS)) {
            fail("Paste operation did not complete in time");
        }
        // Verification
        verify(mockPlaylistRepository, times(1)).findOneById(1L);
        verify(mockPlaylistRepository, times(2)).countByPresetNameAndName(anyString(), anyString());
        verify(mockPlaylistRepository, times(1)).create(any(Playlist.class));
        Playlist updatedPlaylist = result.get();
        assertEquals(originalSongs.size(), updatedPlaylist.getSongCount());
        assertEquals("My Playlist_1", updatedPlaylist.getName());
        assertEquals(currentPreset, updatedPlaylist.getPreset());
        assertEquals(5, updatedPlaylist.getPosition());
    }

    @Test
    public void paste_successful_whenOriginalNameIsAlreadyUnique() throws CloneNotSupportedException, PlaylistNotFoundException, InterruptedException {
        String currentPreset = "TestPreset";
        long originalId = 1L;
        String originalName = "Unique Original";
        Playlist originalPlaylist = createDummyPlaylist(originalId, originalName, "OldPreset", 0);

        when(mockSharedPreferences.getString(Property.CURRENT_PRESET, null)).thenReturn(currentPreset);
        when(mockPlaylistRepository.findOneById(originalId)).thenReturn(Maybe.just(originalPlaylist));

        when(mockPlaylistRepository.countByPresetNameAndName(currentPreset, "Unique Original")).thenReturn(Single.just(0));
        when(mockPlaylistRepository.countAllByPreset(currentPreset)).thenReturn(Single.just(2));
        when(mockPlaylistRepository.create(any(Playlist.class))).thenAnswer(invocation -> Single.just(3L));
        when(mockSongRepository.getSongsForPlaylist(originalId)).thenReturn(Single.just(new ArrayList<>())); // No songs for simplicity
        when(mockPlaylistRepository.update(any(Playlist.class))).thenReturn(Completable.complete());
        AtomicReference<Playlist> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        playlistService.paste(originalId, newValue -> {
            result.set(newValue);
            latch.countDown();
        });
        if (!latch.await(5, TimeUnit.SECONDS)) {
            fail("Paste operation did not complete in time");
        }
        verify(mockPlaylistRepository, times(1)).findOneById(1L);
        verify(mockPlaylistRepository, times(1)).countByPresetNameAndName(anyString(), anyString());
        verify(mockPlaylistRepository, times(1)).create(any(Playlist.class));
        Playlist createdPlaylist = result.get();
        Assert.assertEquals("Unique Original", createdPlaylist.getName()); // Should use original name
        Assert.assertEquals(2, createdPlaylist.getPosition());
    }
}