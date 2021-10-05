package pl.qprogramming.themplay.views;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
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
import androidx.recyclerview.widget.RecyclerView;
import lombok.val;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.playlist.Playlist;
import pl.qprogramming.themplay.playlist.PlaylistService;
import pl.qprogramming.themplay.playlist.Song;

import static android.app.Activity.RESULT_OK;
import static android.content.Intent.ACTION_OPEN_DOCUMENT;
import static pl.qprogramming.themplay.views.SongViewAdapter.MULTIPLE_SELECTED;

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
    SongViewAdapter adapter;
    Button removeBtn;

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
        removeBtn = view.findViewById(R.id.remove_selected_songs);
        removeBtn.setVisibility(View.GONE);
        removeBtn.setOnClickListener(clicked -> {
            val songsToRemove = playlist.getSongs().stream().filter(Song::isSelected).collect(Collectors.toList());
            playlistService.removeSongFromPlaylist(playlist, songsToRemove);
            adapter.setMultiple(false);
            adapter.setSongs(playlist.getSongs());
            adapter.notifyDataSetChanged();
            removeBtn.setVisibility(View.GONE);
        });
        val textView = (TextView) view.findViewById(R.id.header_title);
        view.findViewById(R.id.back_arrow).setOnClickListener(clicked -> updateListAndGoBack());
        textView.setText(playlist.getName());
        textView.setOnClickListener(clicked -> updateListAndGoBack());
        addSongsList(view);
        addNameEditField(view);
        val filter = new IntentFilter(MULTIPLE_SELECTED);
        getActivity().registerReceiver(receiver, filter);
    }

    private void updateListAndGoBack() {
        if (playlistEditText != null) {
            val name = playlistEditText.getText().toString();
            if (!playlist.getName().equals(name)) {
                playlist.setName(name);
                playlistService.save(playlist);
            }
        }
        //hide virtual keyboard if open
        val imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(getView().getWindowToken(), 0);
        getActivity()
                .getSupportFragmentManager()
                .popBackStack();
    }

    private void addSongsList(@NonNull View view) {
        val recyclerView = (RecyclerView) view.findViewById(R.id.songs_list);
        //change to custom adapter
        adapter = new SongViewAdapter(playlist.getSongs());
        recyclerView.setAdapter(adapter);
        view.findViewById(R.id.add_song).setOnClickListener(clicked -> {
            Intent intent = new Intent(ACTION_OPEN_DOCUMENT)
                    .setData(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
                    .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForSelectedFIles.launch(intent);
        });
    }

//    public String getPath(Uri uri) {
//        String path;
//        String[] projection = {MediaStore.Files.FileColumns.DATA};
//        try (Cursor cursor = requireActivity().getContentResolver().query(uri, projection, null, null, null)) {
//            if (cursor == null) {
//                path = uri.getPath();
//            } else {
//                cursor.moveToFirst();
//                int column_index = cursor.getColumnIndexOrThrow(projection[0]);
//                path = cursor.getString(column_index);
//                cursor.close();
//            }
//            return ((path == null || path.isEmpty()) ? (uri.getPath()) : path);
//        }
//
//    }


    private final ActivityResultLauncher<Intent> startActivityForSelectedFIles = registerForActivityResult(
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
                    adapter.setSongs(playlist.getSongs());
                    adapter.notifyDataSetChanged();
                }
            }
    );

    private void songOutOfUri(Uri uri) {
        getActivity().getApplicationContext().getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        val file = new File(uri.getPath());
        val song = Song.builder()
                .filename(file.getName())
                .fileUri(uri.toString())
                .filePath(file.getPath())
                .build();
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
            public void afterTextChanged(Editable editable) {
                if (editable.length() == 0) {
                    playlistInputLayout.setError(getString(R.string.playlist_name_atLeastOneChar));
                } else {
                    playlistInputLayout.setError(null);
                }
            }
        });
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Multiple selection started");
            removeBtn.setVisibility(View.VISIBLE);
        }
    };
}