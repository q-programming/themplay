package pl.qprogramming.themplay.views;

import static androidx.preference.PreferenceManager.getDefaultSharedPreferences;
import static pl.qprogramming.themplay.playlist.EventType.PLAYER_PLAYING;
import static pl.qprogramming.themplay.playlist.EventType.PLAYER_STOPPED;
import static pl.qprogramming.themplay.playlist.EventType.PLAYLIST_CHANGE_BACKGROUND;
import static pl.qprogramming.themplay.playlist.EventType.PLAYLIST_NOTIFICATION;
import static pl.qprogramming.themplay.playlist.EventType.PLAYLIST_NOTIFICATION_ACTIVE;
import static pl.qprogramming.themplay.playlist.EventType.PLAYLIST_NOTIFICATION_ADD;
import static pl.qprogramming.themplay.playlist.EventType.PLAYLIST_NOTIFICATION_DELETE;
import static pl.qprogramming.themplay.playlist.EventType.PLAYLIST_NOTIFICATION_DELETE_SONGS;
import static pl.qprogramming.themplay.playlist.EventType.PLAYLIST_NOTIFICATION_NEW_ACTIVE;
import static pl.qprogramming.themplay.playlist.EventType.PLAYLIST_NOTIFICATION_NEXT;
import static pl.qprogramming.themplay.playlist.EventType.PLAYLIST_NOTIFICATION_PAUSE;
import static pl.qprogramming.themplay.playlist.EventType.PLAYLIST_NOTIFICATION_PLAY;
import static pl.qprogramming.themplay.playlist.EventType.PLAYLIST_NOTIFICATION_PREV;
import static pl.qprogramming.themplay.playlist.EventType.PLAYLIST_NOTIFICATION_RECREATED_LIST;
import static pl.qprogramming.themplay.playlist.EventType.PLAYLIST_NOTIFICATION_RECREATE_LIST;
import static pl.qprogramming.themplay.playlist.EventType.PLAYLIST_NOTIFICATION_STOP;
import static pl.qprogramming.themplay.util.Utils.ARGS;
import static pl.qprogramming.themplay.util.Utils.PLAYLIST;
import static pl.qprogramming.themplay.util.Utils.POSITION;
import static pl.qprogramming.themplay.util.Utils.navigateToFragment;

import android.annotation.SuppressLint;
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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Optional;

import lombok.val;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.domain.Playlist;
import pl.qprogramming.themplay.logger.Logger;
import pl.qprogramming.themplay.player.Player;
import pl.qprogramming.themplay.playlist.EventType;
import pl.qprogramming.themplay.playlist.PlaylistService;
import pl.qprogramming.themplay.settings.Property;

/**
 * A {@link Fragment} that displays a list of playlist items.
 * <p>
 * This fragment manages the interaction with {@link PlaylistService} to retrieve playlist data
 * and {@link Player} to reflect the current playback state. It uses a {@link RecyclerView}
 * to display the playlist items and a {@link PlaylistItemRecyclerViewAdapter} to bind the data.
 * <p>
 * The fragment also listens for various broadcast events related to playlist and player
 * state changes (e.g., adding/deleting items, starting/stopping playback) and updates
 * the UI accordingly.
 * <p>
 * The current preset name is displayed at the top and clicking it navigates to the
 * {@link PresetsFragment}.
 */
@UnstableApi
public class PlaylistFragment extends Fragment {
    private static final String TAG = PlaylistFragment.class.getSimpleName();
    private PlaylistService playlistService;
    private Player player;
    private boolean playlistServiceBound;
    private boolean playerIsBound;
    private RecyclerView recyclerView;
    private PlaylistItemRecyclerViewAdapter adapter;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public PlaylistFragment() {
    }

    @Override
    public void onStart() {
        super.onStart();
        setupServices();
        setupFilters();
    }

    @Override
    public void onStop() {
        super.onStop();
        doUnbindService();
        try {
            LocalBroadcastManager.getInstance(requireActivity()).unregisterReceiver(receiver);
        } catch (IllegalArgumentException e) {
            Logger.d(TAG, "Receiver not registered");
        }
    }

    void doUnbindService() {
        if (playlistServiceBound) {
            this.requireContext().unbindService(playlistServiceConnection);
            playlistServiceBound = false;
        }
        if (playerIsBound) {
            this.requireContext().unbindService(playerConnection);
            playerIsBound = false;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.playlist_container, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        val context = this.requireContext();
        recyclerView = view.findViewById(R.id.playlist_item_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        val sp = getDefaultSharedPreferences(requireContext());
        val currentPresetName = sp.getString(Property.CURRENT_PRESET, getString(R.string.presets_click_to_create));
        val presetName = (TextView) view.findViewById(R.id.preset_name);
        presetName.setText(currentPresetName);
        presetName.setOnClickListener(click ->
                navigateToFragment(requireActivity()
                        .getSupportFragmentManager(), new PresetsFragment(), "presets"));
    }

    private void setupServices() {
        val context = this.requireContext();
        val intent = new Intent(context, PlaylistService.class);
        intent.putExtra("Requester", TAG);
        context.bindService(intent, playlistServiceConnection, Context.BIND_AUTO_CREATE);
        val playerIntent = new Intent(context, Player.class);
        playerIntent.putExtra("Requester", TAG);
        context.bindService(playerIntent, playerConnection, Context.BIND_AUTO_CREATE);
    }

    private void setupFilters() {
        val filter = new IntentFilter(PLAYLIST_NOTIFICATION.getCode());
        filter.addAction(PLAYLIST_NOTIFICATION_ADD.getCode());
        filter.addAction(PLAYLIST_NOTIFICATION_DELETE.getCode());
        filter.addAction(PLAYLIST_NOTIFICATION_ACTIVE.getCode());
        filter.addAction(PLAYLIST_NOTIFICATION_RECREATE_LIST.getCode());
        filter.addAction(PLAYLIST_NOTIFICATION_RECREATED_LIST.getCode());
        filter.addAction(PLAYLIST_CHANGE_BACKGROUND.getCode());
        filter.addAction(PLAYLIST_NOTIFICATION_NEW_ACTIVE.getCode());
        filter.addAction(PLAYLIST_NOTIFICATION_DELETE_SONGS.getCode());
        filter.addAction(PLAYLIST_NOTIFICATION_PLAY.getCode());
        filter.addAction(PLAYLIST_NOTIFICATION_PAUSE.getCode());
        filter.addAction(PLAYLIST_NOTIFICATION_NEXT.getCode());
        filter.addAction(PLAYLIST_NOTIFICATION_PREV.getCode());
        filter.addAction(PLAYLIST_NOTIFICATION_STOP.getCode());
        filter.addAction(PLAYER_PLAYING.getCode());
        filter.addAction(PLAYER_STOPPED.getCode());
        LocalBroadcastManager.getInstance(requireActivity()).registerReceiver(receiver, filter);
    }

    private final ServiceConnection playlistServiceConnection = new ServiceConnection() {
        @SuppressLint("CheckResult")
        public void onServiceConnected(ComponentName className, IBinder service) {
            Logger.d(TAG, "Connected service within PlaylistFragment ");
            playlistService = ((PlaylistService.LocalBinder) service).getService();
            playlistServiceBound = true;
            adapter = new PlaylistItemRecyclerViewAdapter(playlistService, getActivity());
            recyclerView.setAdapter(adapter);
            adapter.loadPlaylists();
            val callback =
                    new ItemMoveCallback<>(adapter);
            val touchHelper = new ItemTouchHelper(callback);
            touchHelper.attachToRecyclerView(recyclerView);
        }

        public void onServiceDisconnected(ComponentName className) {
            playlistService = null;
        }
    };
    private final ServiceConnection playerConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Logger.d(TAG, "Connected player within PlaylistFragment ");
            player = ((Player.LocalBinder) service).getService();
            playerIsBound = true;
            //prevent race condition
            if(adapter!=null){
                adapter.setPlaying(player.isPlaying());
                adapter.notifyDataSetChanged();
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            player = null;
        }
    };
    /**
     * Redraw recycler view on any action received
     */
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            val event = EventType.getType(intent.getAction());
            Logger.d(TAG, "[EVENT] Received event " + event);
            Bundle args = intent.getBundleExtra(ARGS);
            if (args != null) {
                switch (event) {
                    case PLAYLIST_NOTIFICATION_RECREATED_LIST:
                        Optional.ofNullable(args.getSerializable(PLAYLIST))
                                .ifPresent(playlist -> {
                                    val recreated = (Playlist) playlist;
                                    adapter.replaceItem(recreated);
                                });
                        break;
                    case PLAYLIST_CHANGE_BACKGROUND:
                    case PLAYLIST_NOTIFICATION_PLAY:
                    case PLAYLIST_NOTIFICATION_NEXT:
                    case PLAYLIST_NOTIFICATION_STOP:
                    case PLAYLIST_NOTIFICATION_PREV:
                        Optional.ofNullable(args.getSerializable(POSITION))
                                .ifPresent(position -> {
                                    int activated = (int) position;
                                    adapter.reloadItemAt(activated);
                                    adapter.notifyItemChanged(activated);
                                });
                        break;
                    case PLAYLIST_NOTIFICATION_ACTIVE:
                    case PLAYLIST_NOTIFICATION_ADD:
                        adapter.setPlaying(player.isPlaying());
                        adapter.loadPlaylists();
                        break;
                    case PLAYLIST_NOTIFICATION_DELETE:
                        Optional.ofNullable(args.getSerializable(POSITION))
                                .ifPresent(position -> {
                                    adapter.notifyItemRemoved((Integer) position);
                                    adapter.loadPlaylists();
                                });
                        break;
                    case PLAYER_PLAYING:
                    case PLAYER_STOPPED:
                        Optional.ofNullable(args.getSerializable(POSITION))
                                .ifPresent(position -> {
                                    adapter.setPlaying(PLAYER_PLAYING.equals(event));
                                    int activated = (int) position;
                                    adapter.reloadItemAt(activated);
                                    adapter.notifyItemChanged(activated);
                                });
                        break;
                    default:
                        Logger.d(TAG, "Processing event within playlistFragment, reloading  " + intent.getAction());
                        adapter.notifyDataSetChanged();
                }

            }
        }
    };

}