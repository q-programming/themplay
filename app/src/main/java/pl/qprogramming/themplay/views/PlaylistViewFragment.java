package pl.qprogramming.themplay.views;

import static pl.qprogramming.themplay.playlist.EventType.PLAYER_PLAYING;
import static pl.qprogramming.themplay.playlist.EventType.PLAYLIST_NOTIFICATION_MULTIPLE_SELECTED;
import static pl.qprogramming.themplay.util.Utils.ARGS;
import static pl.qprogramming.themplay.util.Utils.SONG;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.val;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.domain.Playlist;
import pl.qprogramming.themplay.domain.Song;
import pl.qprogramming.themplay.logger.Logger;
import pl.qprogramming.themplay.player.Player;
import pl.qprogramming.themplay.playlist.EventType;
import pl.qprogramming.themplay.playlist.PlaylistService;

/**
 * A {@link Fragment} subclass responsible for displaying and managing a playlist.
 * This fragment allows users to view the songs in a playlist, reorder them,
 * and interact with playback controls.
 *
 * <p>It communicates with {@link Player} and {@link PlaylistService} to manage
 * playback and playlist data respectively. It also uses a {@link SongRecyclerViewAdapter}
 * to display the list of songs and handle user interactions like drag-and-drop for reordering.
 *
 * <p>The fragment receives updates via {@link LocalBroadcastManager} for events such as
 * song changes, playback status changes, and playlist modifications.
 */
@UnstableApi
public class PlaylistViewFragment extends Fragment {
    private static final String TAG = PlaylistViewFragment.class.getSimpleName();
    private PlaylistService playlistService;
    private Player player;
    private boolean playerBound;
    private boolean playlistServiceBound;
    private Playlist currentPlaylist;
    private SongRecyclerViewAdapter adapter;
    private TextView headerTitleTextView;
    private boolean multiple;
    private Button readyBtn;
    private Button updateBtn;

    public PlaylistViewFragment() {
        // Required empty public constructor
    }

    public PlaylistViewFragment(Playlist currentPlaylist) {
        this.currentPlaylist = currentPlaylist;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.playlist_view, container, false);
    }

    /**
     * Creates all listeners and initializes views.
     * Adds click listener to start and finish playlist order edit
     *
     * @param view               The View returned by {@link #onCreateView(LayoutInflater, ViewGroup, Bundle)}.
     * @param savedInstanceState If non-null, this fragment is being re-constructed
     *                           from a previous saved state as given here.
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupServices();
        if (currentPlaylist == null) {
            requireActivity().getSupportFragmentManager().popBackStack();
        } else {
            initializeViews(view);
            stopEdit();
            updateBtn.setOnClickListener((clicked) -> startEdit());
            readyBtn.setOnClickListener(clicked -> {
                val songs = adapter.getSongsList();
                playlistService.updateSongsPlaylistPositions(currentPlaylist, songs);
                stopEdit();
                multiple = false;
                adapter.clearCurrentSelections();
            });
            view.findViewById(R.id.include).setOnClickListener(clicked -> updateListAndGoBack());
            headerTitleTextView.setText(currentPlaylist.getName());
            headerTitleTextView.setOnClickListener(clicked -> updateListAndGoBack());

        }
    }

    private void createFilters() {
        val filter = new IntentFilter(PLAYLIST_NOTIFICATION_MULTIPLE_SELECTED.getCode());
        filter.addAction(EventType.PLAYLIST_NOTIFICATION_SOME_DELETE_SELECTED.getCode());
        filter.addAction(EventType.PLAYLIST_NOTIFICATION_SONGS_UPDATE_DONE.getCode());
        filter.addAction(EventType.PLAYLIST_NOTIFICATION_NEXT.getCode());
        filter.addAction(EventType.PLAYLIST_NOTIFICATION_PREV.getCode());
        filter.addAction(EventType.PLAYLIST_NOTIFICATION_RECREATED_LIST.getCode());
        //PLAYBACK has separate controlls
        filter.addAction(EventType.PLAYER_PAUSED.getCode());
        filter.addAction(EventType.PLAYER_PLAYING.getCode());
        filter.addAction(EventType.PLAYER_STOPPED.getCode());
        LocalBroadcastManager.getInstance(requireActivity()).registerReceiver(receiver, filter);
    }

    @Override
    public void onStart() {
        super.onStart();
        setupServices();
    }

    private void setupServices() {
        val context = this.requireContext();
        val playerServiceIntent = new Intent(context, Player.class);
        context.bindService(playerServiceIntent, playerConnection, Context.BIND_AUTO_CREATE);
        val playlistServiceIntent = new Intent(context, PlaylistService.class);
        context.bindService(playlistServiceIntent, playlistServiceConnection, Context.BIND_AUTO_CREATE);
    }

    private void startEdit() {
        readyBtn.setVisibility(View.VISIBLE);
        updateBtn.setVisibility(View.GONE);
        adapter.setEditMode(true);
    }

    private void stopEdit() {
        readyBtn.setVisibility(View.GONE);
        updateBtn.setVisibility(View.VISIBLE);
        adapter.setEditMode(false);
    }


    private void initializeViews(@NonNull View view) {
        readyBtn = view.findViewById(R.id.songs_update_done);
        updateBtn = view.findViewById(R.id.songs_update);
        headerTitleTextView = view.findViewById(R.id.header_title);
        RecyclerView songsRecyclerView = view.findViewById(R.id.view_list_songs_recycler);
        songsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new SongRecyclerViewAdapter(requireContext());
        val callback = new ItemMoveCallback<>(adapter);
        val touchHelper = new ItemTouchHelper(callback);
        touchHelper.attachToRecyclerView(songsRecyclerView);
        adapter.setItemTouchHelper(touchHelper);
        songsRecyclerView.setAdapter(adapter);

    }

    /**
     * Updates playlist name and saves it to database when user navigates back from this fragment.
     */
    private void updateListAndGoBack() {
        requireActivity()
                .getSupportFragmentManager()
                .popBackStack();
    }


    private void updateAndRenderSongList(Playlist playlist) {
        if (!isAdded() || playlist == null) {
            Logger.w(TAG, "Cannot update/render song list, fragment not added, playlist null, or listView null.");
            return;
        }
        currentPlaylist = playlist;
        List<Song> songs = new ArrayList<>(playlist.getPlaylist());
        Logger.d(TAG, "Rendering song list with " + songs.size() + " songs. Multiple selection: " + multiple);
        if (playlist.getSongs().isEmpty()) {
            updateBtn.setVisibility(View.GONE);
        } else {
            updateBtn.setVisibility(View.VISIBLE);
        }
        adapter.updateSongs(songs);
        adapter.setEditMode(false);
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onResume() {
        super.onResume();
        Logger.d(TAG, "onResume CALLED. Activity instance: ");
        createFilters();
    }

    @Override
    public void onDestroy() {
        try {
            LocalBroadcastManager.getInstance(requireActivity()).unregisterReceiver(receiver);
        } catch (IllegalArgumentException e) {
            Logger.w(TAG, "Receiver not registered", e);
        }
        super.onDestroy();
    }

    @Override
    public void onStop() {
        Logger.d(TAG, "onStop");
        if (playerBound) {
            this.requireContext().unbindService(playerConnection);
            playerBound = false;
        }
        if (playlistServiceBound) {
            this.requireContext().unbindService(playlistServiceConnection);
            playlistServiceBound = false;
        }
        super.onStop();
    }

    /**
     * Receiver to handle events like next/prev/play/stop to update animated indicator
     * It will also receive recreated list to update all items
     */
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            val event = EventType.getType(intent.getAction());
            Bundle args = intent.getBundleExtra(ARGS);
            Logger.d(TAG, "[EVENT] received action " + event);
            switch (event) {
                case PLAYLIST_NOTIFICATION_NEXT:
                case PLAYLIST_NOTIFICATION_PREV:
                    Optional.ofNullable(args.getSerializable(SONG))
                            .ifPresent(songId -> {
                                val newSongId = (long) songId;
                                adapter.setCurrentSongId(newSongId);
                                Logger.d(TAG, "Notify that new song should play" + newSongId);
                                adapter.notifyDataSetChanged();
                            });
                    break;
                case PLAYER_PLAYING:
                case PLAYER_PAUSED:
                case PLAYER_STOPPED:
                    adapter.setPlaying(PLAYER_PLAYING.equals(event));
                    adapter.notifyDataSetChanged();
                    break;
                case PLAYLIST_NOTIFICATION_RECREATED_LIST:
                    updateAndRenderSongList(player.getActivePlaylist());
            }
        }
    };
    /**
     * Player connector to grab current playing status
     */
    private final ServiceConnection playerConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Logger.d(TAG, "Player service connected");
            val binder = (Player.LocalBinder) service;
            player = binder.getService();
            playerBound = true;
            if (player.getActivePlaylist() != null) {
                currentPlaylist = player.getActivePlaylist();
                adapter.setCurrentSongId(currentPlaylist.getCurrentSongId());
                adapter.setPlaying(player.isPlaying());
                updateAndRenderSongList(currentPlaylist);
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            player = null;
        }
    };

    private final ServiceConnection playlistServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Logger.d(TAG, "Connected service within PlaylistFragment ");
            if (currentPlaylist != null) {
                playlistService = ((PlaylistService.LocalBinder) service).getService();
                playlistServiceBound = true;
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            playlistService = null;
        }
    };
}