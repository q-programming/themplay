package pl.qprogramming.themplay.views;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import lombok.val;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.playlist.Playlist;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link PlaylistSettingsFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class PlaylistSettingsFragment extends Fragment {

    Playlist playlist;
    TextInputEditText playlistEditText;
    TextInputLayout playlistInputLayout;

    private static final String ARG_PLAYLIST = "playlist";


    public PlaylistSettingsFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment PlaylistSettings.
     */
    public static PlaylistSettingsFragment newInstance(Playlist playlist) {
        PlaylistSettingsFragment fragment = new PlaylistSettingsFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_PLAYLIST, playlist);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            playlist = (Playlist) getArguments().getSerializable(ARG_PLAYLIST);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.playlist_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        val textView = (TextView) view.findViewById(R.id.header_title);
        view.findViewById(R.id.back_arrow).setOnClickListener(clicked -> getActivity()
                .getSupportFragmentManager()
                .popBackStack());
        textView.setText(playlist.getName());
        textView.setOnClickListener(clicked -> getActivity()
                .getSupportFragmentManager()
                .popBackStack());
        //populate edit fields
        addNameEditField(view);
    }

    private void addNameEditField(@NonNull View view) {
        playlistEditText = view.findViewById(R.id.playlist_name_input);
        playlistInputLayout = view.findViewById(R.id.playlist_name_layout);
        playlistEditText.setText(playlist.getName());
        playlistEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() == 0) {
                    playlistInputLayout.setError(getString(R.string.playlist_name_atLeastOneChar));
                } else {
                    playlistInputLayout.setError(null);
                }
            }
        });
    }
}