package pl.qprogramming.themplay.views;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.reactiveandroid.query.Select;

import java.text.MessageFormat;
import java.util.Optional;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import io.reactivex.android.schedulers.AndroidSchedulers;
import lombok.val;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.preset.Preset;
import pl.qprogramming.themplay.preset.exceptions.PresetAlreadyExistsException;

import static pl.qprogramming.themplay.playlist.EventType.PRESET_ACTIVATED;

/**
 * A simple {@link Fragment} subclass.
 */
public class PresetsFragment extends Fragment {
    private static final String TAG = PresetsFragment.class.getSimpleName();

    PresetViewAdapter adapter;

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
        requireActivity().registerReceiver(receiver, filter);
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
            adapter.notifyDataSetChanged();
        }
    };

}