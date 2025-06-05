package pl.qprogramming.themplay.views;

import static android.app.Activity.RESULT_OK;
import static android.content.Intent.ACTION_OPEN_DOCUMENT;
import static android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION;
import static pl.qprogramming.themplay.util.Utils.ARGS;
import static pl.qprogramming.themplay.util.Utils.PLAYLIST;
import static pl.qprogramming.themplay.views.SongListViewAdapter.MULTIPLE_SELECTED;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.val;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.domain.Playlist;
import pl.qprogramming.themplay.domain.Song;
import pl.qprogramming.themplay.playlist.EventType;
import pl.qprogramming.themplay.playlist.PlaylistService;

/**
 * A simple {@link Fragment} subclass.
 * create an instance of this fragment.
 */
public class PlaylistSettingsFragment extends Fragment {
    private static final String TAG = PlaylistSettingsFragment.class.getSimpleName();
    private PlaylistService playlistService;
    private boolean serviceIsBound;
    private Playlist currentPlaylist;
    private TextInputEditText playlistEditText;
    private TextInputLayout playlistInputLayout;
    private SongListViewAdapter adapter;
    private ListView songsListView;
    private TextView headerTitleTextView;
    private boolean multiple;
    private Button removeBtn;

    public PlaylistSettingsFragment() {
        // Required empty public constructor
    }

    public PlaylistSettingsFragment(Playlist currentPlaylist) {
        this.currentPlaylist = currentPlaylist;
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
        val context = this.requireContext();
        val playlistServiceIntent = new Intent(context, PlaylistService.class);
        context.bindService(playlistServiceIntent, mConnection, Context.BIND_AUTO_CREATE);
        if (currentPlaylist == null) {
            requireActivity().getSupportFragmentManager().popBackStack();
        } else {
            initializeViews(view);
            addInputTextWatcher();
            removeBtn.setVisibility(View.GONE);
            removeBtn.setOnClickListener(clicked -> {
                val songsToRemove = currentPlaylist.getSongs().stream().filter(Song::isSelected).collect(Collectors.toList());
                playlistService.removeSongsFromPlaylist(currentPlaylist.getId(), songsToRemove, updatedPlaylist -> currentPlaylist = updatedPlaylist,
                        throwable -> Log.e(TAG, "Error removing songs from playlist", throwable), () -> {
                            removeBtn.setVisibility(View.GONE);
                            multiple = false;
                            adapter.setMultiple(false);
                            updateAndRenderSongList(false);
                            adapter.clearSelections();
                            Toast.makeText(view.getContext(), getString(R.string.playlist_removed_selected_songs), Toast.LENGTH_SHORT).show();
                        });
            });
            view.findViewById(R.id.include).setOnClickListener(clicked -> updateListAndGoBack());
            headerTitleTextView.setOnClickListener(clicked -> updateListAndGoBack());
            val filter = new IntentFilter(MULTIPLE_SELECTED);
            LocalBroadcastManager.getInstance(requireActivity()).registerReceiver(receiver, filter);
        }
    }

    private void initializeViews(@NonNull View view) {
        removeBtn = view.findViewById(R.id.remove_selected_songs);
        headerTitleTextView = view.findViewById(R.id.header_title);
        songsListView = view.findViewById(R.id.list_songs);
        playlistEditText = view.findViewById(R.id.playlist_name_input);
        playlistInputLayout = view.findViewById(R.id.playlist_name_layout);
        view.findViewById(R.id.add_song).setOnClickListener(clicked -> {
            Intent intent = new Intent(ACTION_OPEN_DOCUMENT)
                    .setData(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
                    .setFlags(FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForSelectedFiles.launch(intent);
        });
    }

    private void updateListAndGoBack() {
        if (playlistEditText != null) {
            val name = Objects.requireNonNull(playlistEditText.getText()).toString();
            if (!currentPlaylist.getName().equals(name)) {
                currentPlaylist.setName(name);
                playlistService.save(currentPlaylist);
            }
        }
        //hide virtual keyboard if open
        val imm = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(requireView().getWindowToken(), 0);
        requireActivity()
                .getSupportFragmentManager()
                .popBackStack();
    }

    /**
     * Start activity for file selection
     */
    private final ActivityResultLauncher<Intent> startActivityForSelectedFiles = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    val data = result.getData();
                    List<Uri> uris = new ArrayList<>();
                    if (data != null && data.getData() != null) {
                        uris.add(data.getData());
                    } else if (data != null && data.getClipData() != null && data.getClipData().getItemCount() > 0) {
                        val clipData = data.getClipData();
                        for (int i = 0; i < clipData.getItemCount(); i++) {
                            val uri = clipData.getItemAt(i).getUri();
                            uris.add(uri);
                        }
                    }
                    processSelectedSongUris(uris);

                }
            }
    );

    /**
     * Process selected song URIs.
     * All uris will be decoded, made readable upon app restart, and added to the playlist.
     *
     * @param uris List of URIs to process
     */
    private void processSelectedSongUris(List<Uri> uris) {
        if (playlistService == null || currentPlaylist == null || !isAdded()) {
            Log.e(TAG, "Service or playlist null, or fragment not added. Cannot process URIs.");
            return;
        }
        List<Song> newSongs = new ArrayList<>();
        for (Uri uri : uris) {
            try {
                val context = this.requireContext();
                context.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                String displayName = getFileNameFromUri(context, uri);
                val file = new File(uri.getPath());
                Log.d(TAG, "Processing URI: " + uri + " path: " + file.getPath() + " filename" + displayName);
                val song = Song.builder()
                        .filename(displayName)
                        .fileUri(uri.toString())
                        .filePath(file.getPath())
                        .build();
                newSongs.add(song);
            } catch (SecurityException e) {
                Log.e(TAG, "Permission denial for URI: " + uri, e);
                if (isAdded()) {
                    Toast.makeText(getContext(), "Permission denied for: " + uri.getLastPathSegment(), Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing URI: " + uri, e);
            }
        }
        if (!newSongs.isEmpty()) {
            playlistService.addSongToPlaylist(currentPlaylist, newSongs,
                    updatedPlaylist -> {
                        currentPlaylist = updatedPlaylist;
                        if (isAdded()) {
                            updateAndRenderSongList(true);
                            Intent intent = new Intent(EventType.PLAYLIST_NOTIFICATION_ADD.getCode());
                            Bundle args = new Bundle();
                            args.putSerializable(PLAYLIST, currentPlaylist);
                            intent.putExtra(ARGS, args);
                            LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(intent);
                        }
                    }
            );
        }
    }

    /**
     * Get the display name of a file from its URI.
     * This is more elaborate way , which will handle some weird names some files may have
     *
     * @param context Context
     * @param uri     URI of the file
     * @return Display name of the file
     */
    private String getFileNameFromUri(Context context, Uri uri) {
        String fileName = null;
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex);
                }
            } catch (Exception e) {
                Log.w(TAG, "Error getting display name from content URI: " + uri, e);
            }
        }
        if (fileName == null)
            fileName = uri.getLastPathSegment() != null ? uri.getLastPathSegment() : "Unknown_Song";
        return fileName;
    }

    /**
     * Updates and renders song list with current playlist.
     *
     * @param notify If true, notifies the adapter that the data set has changed.
     */
    private void updateAndRenderSongList(boolean notify) {
        if (!isAdded() || currentPlaylist == null || songsListView == null) {
            Log.w(TAG, "Cannot update/render song list, fragment not added, playlist null, or listView null.");
            return;
        }
        List<Song> songs = currentPlaylist.getSongs() != null ? currentPlaylist.getSongs() : new ArrayList<>();
        Log.d(TAG, "Rendering song list with " + songs.size() + " songs. Multiple selection: " + multiple);
        if (adapter == null) {
            adapter = new SongListViewAdapter(requireContext(), songs, multiple);
            songsListView.setAdapter(adapter);
        } else {
            adapter.setSongs(songs);
            adapter.setMultiple(multiple);
            if (notify) {
                adapter.notifyDataSetChanged();
            }
        }
    }

    private void addInputTextWatcher() {

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

    @Override
    public void onDestroy() {
        try {
            LocalBroadcastManager.getInstance(requireActivity()).unregisterReceiver(receiver);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Receiver not registered", e);
        }
        super.onDestroy();
    }

    @Override
    public void onStop() {
        if (serviceIsBound) {
            this.requireContext().unbindService(mConnection);
            serviceIsBound = false;
        }
        super.onStop();

    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Multiple selection started");
            removeBtn.setVisibility(View.VISIBLE);
            multiple = true;
            adapter.notifyDataSetChanged();
        }
    };

    private final ServiceConnection mConnection = new ServiceConnection() {
        @SuppressLint("CheckResult")
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(TAG, "Connected service within PlaylistFragment ");
            playlistService = ((PlaylistService.LocalBinder) service).getService();
            serviceIsBound = true;
            playlistService.loadSongs(currentPlaylist, playlistWithSongs -> {
                currentPlaylist = playlistWithSongs;
                headerTitleTextView.setText(currentPlaylist.getName());
                playlistEditText.setText(currentPlaylist.getName());
                updateAndRenderSongList(true);
            }, throwable -> Log.e(TAG, "Error loading playlist", throwable));
        }

        public void onServiceDisconnected(ComponentName className) {
            playlistService = null;
        }
    };
}