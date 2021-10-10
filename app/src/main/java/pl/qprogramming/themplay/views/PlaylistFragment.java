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
import android.widget.TextView;

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
import pl.qprogramming.themplay.playlist.PlaylistService;
import pl.qprogramming.themplay.settings.Property;

import static androidx.preference.PreferenceManager.getDefaultSharedPreferences;
import static pl.qprogramming.themplay.util.Utils.ARGS;
import static pl.qprogramming.themplay.util.Utils.POSITION;
import static pl.qprogramming.themplay.util.Utils.isEmpty;
import static pl.qprogramming.themplay.util.Utils.navigateToFragment;

/**
 * A fragment representing a list of Items.
 */
public class PlaylistFragment extends Fragment {
    private static final String TAG = PlaylistFragment.class.getSimpleName();
    private PlaylistService playlistService;
    private boolean serviceIsBound;
    private RecyclerView recyclerView;
    private LinearLayout encourage;

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
        recyclerView = (RecyclerView) view.findViewById(R.id.playlist_item_list);
        encourage = (LinearLayout) view.findViewById(R.id.encourage_menu_click);
        //change to custom adapter
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        val intent = new Intent(context, PlaylistService.class);
        context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
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
        @SuppressLint("CheckResult")
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(TAG, "Connected service within PlaylistFragment ");
            playlistService = ((PlaylistService.LocalBinder) service).getService();
            serviceIsBound = true;
            recyclerView.setAdapter(new PlaylistItemRecyclerViewAdapter(playlistService, getActivity()));
            playlistService.getAllAsync().subscribeOn(AndroidSchedulers.mainThread())
                    .subscribe((playlists) -> {
                        val sp = getDefaultSharedPreferences(playlistService);
                        val currentPresetName = sp.getString(Property.CURRENT_PRESET, null);
                        if (playlists.size() == 0 && !isEmpty(currentPresetName)) {
                            encourage.setVisibility(View.VISIBLE);
                        }
                    });
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
                        Log.d(TAG, "Processing event within playlistFragment " + intent.getAction());
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