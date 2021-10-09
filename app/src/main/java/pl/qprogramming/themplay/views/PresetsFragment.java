package pl.qprogramming.themplay.views;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.text.MessageFormat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import lombok.val;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.settings.Property;

import static androidx.preference.PreferenceManager.getDefaultSharedPreferences;

/**
 * A simple {@link Fragment} subclass.
 */
public class PresetsFragment extends Fragment {

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
                        val spEdit = getDefaultSharedPreferences(requireContext()).edit();
                        spEdit.putString(Property.CURRENT_PRESET, presetName);
                        spEdit.apply();
                        //TODO do magic of creation
                        val msg = MessageFormat.format(getString(R.string.playlist_add_created), presetName);
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(getString(R.string.cancel), (dialog, which) -> dialog.cancel())
                .show();
    }

}