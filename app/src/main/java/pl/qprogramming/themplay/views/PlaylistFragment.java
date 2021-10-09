package pl.qprogramming.themplay.views;

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

import java.util.Optional;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import lombok.val;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.playlist.EventType;
import pl.qprogramming.themplay.playlist.PlaylistService;

import static pl.qprogramming.themplay.playlist.PlaylistService.POSITION;

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
        View view = inflater.inflate(R.layout.playlist_item_list, container, false);

        // Set the adapter
        if (view instanceof RecyclerView) {
            val context = requireActivity().getApplicationContext();
            recyclerView = (RecyclerView) view;
            recyclerView.setLayoutManager(new LinearLayoutManager(context));
            val intent = new Intent(context, PlaylistService.class);
            context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        }
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        val filter = new IntentFilter();
        for (EventType eventType : EventType.values()) {
            filter.addAction(eventType.getCode());
        }
        getActivity().registerReceiver(receiver, filter);
    }

    private final ServiceConnection mConnection = new ServiceConnection() {
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
            Bundle args = intent.getBundleExtra(PlaylistService.ARGS);
            if (args != null) {
                switch (event) {
                    case PLAYLIST_NOTIFICATION_PLAY:
                    case PLAYLIST_NOTIFICATION_NEXT:
                    case PLAYLIST_NOTIFICATION_PREV:
                        Log.d(TAG, "Processing event within playlistFragment " + intent.getAction());
                        Optional.ofNullable(args.getSerializable(POSITION))
                                .ifPresent(position -> recyclerView.getAdapter()
                                        .notifyItemChanged((int) position));
                        break;
                    default:
                        recyclerView.getAdapter().notifyDataSetChanged();
                }
            }
        }
    };
}