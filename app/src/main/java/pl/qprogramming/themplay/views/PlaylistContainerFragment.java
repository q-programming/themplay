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
import android.widget.LinearLayout;

import com.reactiveandroid.query.Select;

import java.util.Objects;
import java.util.Optional;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import io.reactivex.android.schedulers.AndroidSchedulers;
import lombok.val;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.playlist.EventType;
import pl.qprogramming.themplay.playlist.Playlist;
import pl.qprogramming.themplay.playlist.PlaylistService;
import pl.qprogramming.themplay.settings.Property;

import static androidx.preference.PreferenceManager.getDefaultSharedPreferences;
import static pl.qprogramming.themplay.util.Utils.ARGS;
import static pl.qprogramming.themplay.util.Utils.POSITION;
import static pl.qprogramming.themplay.util.Utils.navigateToFragment;

/**
 * A simple {@link Fragment} subclass.
 */
public class PlaylistContainerFragment extends Fragment {

    private static final String TAG = PlaylistContainerFragment.class.getSimpleName();
    private PlaylistService playlistService;
    private boolean serviceIsBound;
    private RecyclerView recyclerView;
    private LinearLayout encourage;

    public PlaylistContainerFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.playlist_container, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerView = (RecyclerView) view.findViewById(R.id.playlist_item_list);
        bindRecyclerViewAndService(view);
        view.findViewById(R.id.preset_name)
                .setOnClickListener(click ->
                        navigateToFragment(requireActivity()
                                .getSupportFragmentManager(), new PresetsFragment(), "presets"));

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


    @SuppressLint("CheckResult")
    private void bindRecyclerViewAndService(@NonNull View view) {
        val context = requireActivity().getApplicationContext();
        encourage = (LinearLayout) view.findViewById(R.id.encourage_menu_click);
        //change to custom adapter
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        val intent = new Intent(context, PlaylistService.class);
        context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        val sp = getDefaultSharedPreferences(view.getContext());
        val currentPresetName = sp.getString(Property.CURRENT_PRESET, null);
        Select.from(Playlist.class).where(Playlist.PRESET + " =?", currentPresetName).fetchAsync().subscribeOn(AndroidSchedulers.mainThread())
                .subscribe((playlists) -> {
                    if (playlists.size() == 0) {
                        encourage.setVisibility(View.VISIBLE);
                    }
                });
    }

    @Override
    public void onResume() {
        super.onResume();
        val filter = new IntentFilter();
        for (EventType eventType : EventType.values()) {
            filter.addAction(eventType.getCode());
        }
        requireActivity().registerReceiver(receiver, filter);
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
            Bundle args = intent.getBundleExtra(ARGS);
            if (args != null) {
                switch (event) {
                    case PLAYLIST_NOTIFICATION_PLAY:
                    case PLAYLIST_NOTIFICATION_NEXT:
                    case PLAYLIST_NOTIFICATION_PREV:
                        Log.d(TAG, "Processing event within PlaylistContainerFragment " + intent.getAction());
                        Optional.ofNullable(args.getSerializable(POSITION))
                                .ifPresent(position -> Objects.requireNonNull(recyclerView.getAdapter())
                                        .notifyItemChanged((int) position));
                        break;
                    default:
                        Objects.requireNonNull(recyclerView.getAdapter()).notifyDataSetChanged();
                }
            }
        }
    };
}