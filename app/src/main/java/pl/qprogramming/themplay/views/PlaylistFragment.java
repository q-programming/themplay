package pl.qprogramming.themplay.views;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import lombok.val;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.playlist.EventType;
import pl.qprogramming.themplay.playlist.PlaylistService;
import pl.qprogramming.themplay.settings.Property;

import static androidx.preference.PreferenceManager.getDefaultSharedPreferences;
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
import static pl.qprogramming.themplay.playlist.EventType.PLAYLIST_NOTIFICATION_RECREATE_LIST;
import static pl.qprogramming.themplay.playlist.EventType.PLAYLIST_NOTIFICATION_STOP;
import static pl.qprogramming.themplay.util.Utils.ARGS;
import static pl.qprogramming.themplay.util.Utils.POSITION;
import static pl.qprogramming.themplay.util.Utils.navigateToFragment;

/**
 * A fragment representing a list of Items.
 */
public class PlaylistFragment extends Fragment {
    private static final String TAG = PlaylistFragment.class.getSimpleName();
    private PlaylistService playlistService;
    private boolean serviceIsBound;
    private RecyclerView recyclerView;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public PlaylistFragment() {
    }

    @Override
    public void onStop() {
        doUnbindService();
        super.onStop();
    }

    void doUnbindService() {
        if (serviceIsBound) {
            requireActivity().getApplicationContext().unbindService(mConnection);
            serviceIsBound = false;
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
        bindRecyclerViewAndService(view);
        val sp = getDefaultSharedPreferences(requireContext());
        val currentPresetName = sp.getString(Property.CURRENT_PRESET, getString(R.string.presets_click_to_create));
        val presetName = (TextView) view.findViewById(R.id.preset_name);
        presetName.setText(currentPresetName);
        presetName.setOnClickListener(click ->
                navigateToFragment(requireActivity()
                        .getSupportFragmentManager(), new PresetsFragment(), "presets"));

    }

    private void bindRecyclerViewAndService(@NonNull View view) {
        val context = requireActivity().getApplicationContext();
        recyclerView = view.findViewById(R.id.playlist_item_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        val intent = new Intent(context, PlaylistService.class);
        context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onResume() {
        super.onResume();
        val filter = new IntentFilter(PLAYLIST_NOTIFICATION.getCode());
        filter.addAction(PLAYLIST_NOTIFICATION_ADD.getCode());
        filter.addAction(PLAYLIST_NOTIFICATION_DELETE.getCode());
        filter.addAction(PLAYLIST_NOTIFICATION_ACTIVE.getCode());
        filter.addAction(PLAYLIST_NOTIFICATION_RECREATE_LIST.getCode());
        filter.addAction(PLAYLIST_CHANGE_BACKGROUND.getCode());
        filter.addAction(PLAYLIST_NOTIFICATION_NEW_ACTIVE.getCode());
        filter.addAction(PLAYLIST_NOTIFICATION_DELETE_SONGS.getCode());
        filter.addAction(PLAYLIST_NOTIFICATION_PLAY.getCode());
        filter.addAction(PLAYLIST_NOTIFICATION_PAUSE.getCode());
        filter.addAction(PLAYLIST_NOTIFICATION_NEXT.getCode());
        filter.addAction(PLAYLIST_NOTIFICATION_PREV.getCode());
        filter.addAction(PLAYLIST_NOTIFICATION_STOP.getCode());
        requireActivity().registerReceiver(receiver, filter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            requireActivity().unregisterReceiver(receiver);
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "Receiver not registered");
        }
    }

    private final ServiceConnection mConnection = new ServiceConnection() {
        @SuppressLint("CheckResult")
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(TAG, "Connected service within PlaylistFragment ");
            playlistService = ((PlaylistService.LocalBinder) service).getService();
            serviceIsBound = true;
            recyclerView.setAdapter(new PlaylistItemRecyclerViewAdapter(playlistService, getActivity()));
        }

        public void onServiceDisconnected(ComponentName className) {
            playlistService = null;
        }
    };
    /**
     * Redraw recycler view on any action recived
     */
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            val event = EventType.getType(intent.getAction());
            Bundle args = intent.getBundleExtra(ARGS);
            if (args != null) {
                switch (event) {
                    case PLAYLIST_CHANGE_BACKGROUND:
                        Objects.requireNonNull(recyclerView.getAdapter()).notifyItemChanged((Integer) args.getSerializable(POSITION));
                        //TODO once playlist will have position , we can just refresh single ones
//                    case PLAYLIST_NOTIFICATION_PLAY:
//                    case PLAYLIST_NOTIFICATION_NEXT:
//                    case PLAYLIST_NOTIFICATION_STOP:
//                    case PLAYLIST_NOTIFICATION_PREV:
//                        Log.d(TAG, "Processing event within playlistFragment " + intent.getAction());
//                        Optional.ofNullable(args.getSerializable(POSITION))
//                                .ifPresent(position -> Objects.requireNonNull(recyclerView.getAdapter())
//                                        .notifyItemChanged((int) position));
//                        break;
                    default:
                        Log.d(TAG, "Processing event within playlistFragment " + intent.getAction());
                        Objects.requireNonNull(recyclerView.getAdapter()).notifyDataSetChanged();
                }
            }
        }
    };
}