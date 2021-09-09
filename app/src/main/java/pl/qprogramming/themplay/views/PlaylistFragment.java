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
            getActivity().unbindService(mConnection);
            serviceIsBound = false;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.playlist_item_list, container, false);

        // Set the adapter
        if (view instanceof RecyclerView) {
            Context context = view.getContext();
            recyclerView = (RecyclerView) view;
            recyclerView.setLayoutManager(new LinearLayoutManager(context));
            val intent = new Intent(getActivity(), PlaylistService.class);
            getActivity().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        }
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        val filter = new IntentFilter(EventType.PLAYLIST_NOTIFICATION.getCode());
        filter.addAction(EventType.PLAYLIST_NOTIFICATION_DELETE.getCode());
        filter.addAction(EventType.PLAYLIST_NOTIFICATION_ADD.getCode());
        filter.addAction(EventType.PLAYLIST_NOTIFICATION_PLAY.getCode());
        getActivity().registerReceiver(receiver, filter);
    }

    private final ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(TAG, "Connected service within PlaylistFragment ");
            playlistService = ((PlaylistService.LocalBinder) service).getService();
            serviceIsBound = true;
            recyclerView.setAdapter(new PlaylistItemRecyclerViewAdapter(playlistService));
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
            Log.d(TAG, "Received event within playlistFragment " + intent.getAction());
            val event = EventType.getType(intent.getAction());
            Bundle args = intent.getBundleExtra(PlaylistService.ARGS);
            if (args != null) {
                val position = (int) args.getSerializable(POSITION);
                if (event == EventType.PLAYLIST_NOTIFICATION_DELETE) {
                    recyclerView.getAdapter().notifyItemRemoved(position);
                } else {
                    recyclerView.getAdapter().notifyDataSetChanged();
                }
            }
        }
    };
}