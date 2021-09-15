package pl.qprogramming.themplay.views;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.util.stream.Collectors;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import lombok.val;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.playlist.Playlist;
import pl.qprogramming.themplay.playlist.PlaylistService;
import pl.qprogramming.themplay.playlist.Song;

import static android.app.Activity.RESULT_OK;

/**
 * A simple {@link Fragment} subclass.
 * create an instance of this fragment.
 */
public class PlaylistSettingsFragment extends Fragment {
    private static final String TAG = PlaylistSettingsFragment.class.getSimpleName();
    PlaylistService playlistService;
    Playlist playlist;
    TextInputEditText playlistEditText;
    TextInputLayout playlistInputLayout;
    ArrayAdapter<String> adapter;

    public PlaylistSettingsFragment() {
        // Required empty public constructor
    }

    public PlaylistSettingsFragment(PlaylistService playlistService, Playlist playlist) {
        this.playlistService = playlistService;
        this.playlist = playlist;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        addSongsList(view);
        addNameEditField(view);
    }

    private void addSongsList(@NonNull View view) {
        val listView = (ListView) view.findViewById(R.id.songs_list);
        //change to custom adapter
        adapter = new ArrayAdapter<>(view.getContext(),
                android.R.layout.simple_list_item_1, playlist.getSongs().stream().map(Song::getFilename).collect(Collectors.toList()));
        listView.setAdapter(adapter);
        view.findViewById(R.id.add_song).setOnClickListener(clicked -> {
            Intent intent = new Intent();
            intent.setData(MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult.launch(intent);
        });
    }

    private final ActivityResultLauncher<Intent> startActivityForResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    val data = result.getData();
                    if (data.getData() != null) {
                        val uri = data.getData();
                        songOutOfUri(uri);
                    } else if (data.getClipData() != null && data.getClipData().getItemCount() > 0) {
                        val clipData = data.getClipData();
                        for (int i = 0; i < clipData.getItemCount(); i++) {
                            val uri = clipData.getItemAt(i).getUri();
                            songOutOfUri(uri);
                        }
                    }
                    adapter.clear();
                    adapter.addAll(playlist.getSongs().stream().map(Song::getFilename).collect(Collectors.toList()));
                    adapter.notifyDataSetChanged();
                }
            }
    );

    private void songOutOfUri(Uri uri) {
        val file = new File(uri.getPath());
        val song = Song.builder().filename(file.getName()).filePath(file.getPath()).build();
        song.save();
        playlistService.addSongToPlaylist(playlist, song);
    }

    private void addNameEditField(@NonNull View view) {
        playlistEditText = view.findViewById(R.id.playlist_name_input);
        playlistInputLayout = view.findViewById(R.id.playlist_name_layout);
        playlistEditText.setText(playlist.getName());
        playlistEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

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