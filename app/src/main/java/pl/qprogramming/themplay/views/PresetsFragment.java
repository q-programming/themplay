package pl.qprogramming.themplay.views;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.reactiveandroid.query.Select;

import java.text.MessageFormat;
import java.util.List;
import java.util.Optional;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import io.reactivex.android.schedulers.AndroidSchedulers;
import lombok.SneakyThrows;
import lombok.val;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.playlist.EventType;
import pl.qprogramming.themplay.playlist.Playlist;
import pl.qprogramming.themplay.playlist.PlaylistService;
import pl.qprogramming.themplay.preset.AsyncPlaylistZipPacker;
import pl.qprogramming.themplay.preset.Preset;
import pl.qprogramming.themplay.preset.exceptions.PresetAlreadyExistsException;

import static androidx.preference.PreferenceManager.getDefaultSharedPreferences;
import static pl.qprogramming.themplay.playlist.EventType.PRESET_ACTIVATED;
import static pl.qprogramming.themplay.playlist.EventType.PRESET_REMOVED;
import static pl.qprogramming.themplay.playlist.EventType.PRESET_SAVE;
import static pl.qprogramming.themplay.settings.Property.CURRENT_PRESET;
import static pl.qprogramming.themplay.util.Utils.ARGS;
import static pl.qprogramming.themplay.util.Utils.POSITION;
import static pl.qprogramming.themplay.util.Utils.PRESET;

/**
 * A simple {@link Fragment} subclass.
 */
public class PresetsFragment extends Fragment {
    private static final String TAG = PresetsFragment.class.getSimpleName();


    PresetViewAdapter adapter;
    private PlaylistService playlistService;
    private boolean serviceIsBound;
    private List<Playlist> presetContentBuffer;

    public PresetsFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.presets, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        val context = this.requireContext();
        val playlistServiceIntent = new Intent(context, PlaylistService.class);
        context.bindService(playlistServiceIntent, mConnection, Context.BIND_AUTO_CREATE);
        val textView = (TextView) view.findViewById(R.id.header_title);
        textView.setText(getString(R.string.presets));
        view
                .findViewById(R.id.include)
                .setOnClickListener(clicked -> requireActivity()
                        .getSupportFragmentManager()
                        .popBackStack());
        view.findViewById(R.id.add_preset).setOnClickListener(click -> addPreset());
        renderPresetList(view);

    }

    private void renderPresetList(@NonNull View view) {
        val recyclerView = (RecyclerView) view.findViewById(R.id.preset_list);
        val presetsList = Select.from(Preset.class).fetch();
        adapter = new PresetViewAdapter(presetsList);
        recyclerView.setAdapter(adapter);
    }


    private void addPreset() {
        val input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT);

        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.presets_name))
                .setView(input)
                .setPositiveButton(getString(R.string.create), (dialog, which) -> {
                    val presetName = input.getText().toString();
                    if (presetName.length() == 0) {
                        val msg = getString(R.string.presets_add_atLeastOneChar);
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                        input.setError(getString(R.string.playlist_name_atLeastOneChar));
                    } else {
                        input.setError(null);
                        try {
                            createPreset(presetName);
                        } catch (PresetAlreadyExistsException e) {
                            val msg = MessageFormat.format(getString(R.string.presets_already_exists), presetName);
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton(getString(R.string.cancel), (dialog, which) -> dialog.cancel())
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        val filter = new IntentFilter(PRESET_ACTIVATED.getCode());
        filter.addAction(PRESET_REMOVED.getCode());
        filter.addAction(PRESET_SAVE.getCode());
        requireActivity().registerReceiver(receiver, filter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            requireActivity().unregisterReceiver(receiver);
            if (serviceIsBound) {
                this.requireContext().unbindService(mConnection);
                serviceIsBound = false;
            }
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "Receiver not registered");
        }
    }

    @SuppressLint("CheckResult")
    private void createPreset(String presetName) throws PresetAlreadyExistsException {
        Optional.ofNullable(Select.from(Preset.class).where("name = ?", presetName).fetchSingle())
                .ifPresent(preset -> {
                    throw new PresetAlreadyExistsException();
                });
        val preset = Preset.builder().name(presetName).build();
        preset.save();
        val msg = MessageFormat.format(getString(R.string.presets_created), presetName);
        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
        Select.from(Preset.class).fetchAsync()
                .subscribeOn(AndroidSchedulers.mainThread())
                .subscribe((presets) -> {
                    adapter.setPresets(presets);
                    adapter.notifyDataSetChanged();
                });
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            val event = EventType.getType(intent.getAction());
            val args = intent.getBundleExtra(ARGS);
            switch (event) {
                case PRESET_REMOVED:
                    Optional.ofNullable(args.getSerializable(PRESET))
                            .map(serializable -> (Preset) serializable)
                            .ifPresent(removedPreset -> {
                                int position = (int) args.getSerializable(POSITION);
                                val sp = getDefaultSharedPreferences(context);
                                val currentPreset = sp.getString(CURRENT_PRESET, null);
                                if (removedPreset.getName().equals(currentPreset)) {
                                    sp.edit().putString(CURRENT_PRESET, null).apply();
                                    val newIntent = new Intent(PRESET_ACTIVATED.getCode());
                                    context.sendBroadcast(newIntent);
                                }
                                adapter.getPresets().remove(position);
                                adapter.notifyItemRemoved(position);
                            });
                    break;
                case PRESET_SAVE:
                    Optional.ofNullable(args.getSerializable(PRESET))
                            .map(serializable -> (Preset) serializable)
                            .ifPresent(savedPreset -> {
                                presetContentBuffer = null;
                                playlistService
                                        .getByPresetWithPlaylists(savedPreset.getName())
                                        .toList()
                                        .subscribe(presetContent -> {
                                            val saveIntent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                                            saveIntent.addCategory(Intent.CATEGORY_OPENABLE);
                                            saveIntent.setType("application/zip");
                                            saveIntent.putExtra(Intent.EXTRA_TITLE, savedPreset.getName() + ".zip");
                                            presetContentBuffer = presetContent;
                                            fileSaveActivityResultLauncher.launch(saveIntent);
                                        });

                            });
                    break;
                case PRESET_ACTIVATED:
                    adapter.notifyDataSetChanged();
                    break;
            }
        }
    };

    ActivityResultLauncher<Intent> fileSaveActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                @SneakyThrows
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        val uri = result.getData().getData();
                        val logs = new StringBuilder();
                        new AsyncPlaylistZipPacker(uri, logs, requireContext())
                                .execute(presetContentBuffer.toArray(new Playlist[0]));
                    }
                }
            });

    private final ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            val binder = (PlaylistService.LocalBinder) service;
            playlistService = binder.getService();
            serviceIsBound = true;
        }

        public void onServiceDisconnected(ComponentName className) {
            playlistService = null;
        }
    };
}