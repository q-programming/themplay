package pl.qprogramming.themplay.views;

import static pl.qprogramming.themplay.playlist.EventType.PLAYLIST_NOTIFICATION_MULTIPLE_SELECTED;
import static pl.qprogramming.themplay.playlist.EventType.PLAYLIST_NOTIFICATION_SOME_DELETE_SELECTED;
import static pl.qprogramming.themplay.playlist.EventType.PLAYLIST_NOTIFICATION_SONGS_UPDATE_DONE;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import lombok.val;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.domain.Song;

public class SongRecyclerViewAdapter extends RecyclerView.Adapter<SongRecyclerViewAdapter.SongViewHolder> implements ItemMoveCallback.ItemTouchHelperContract<SongRecyclerViewAdapter.SongViewHolder> {

    public static final String TAG = SongRecyclerViewAdapter.class.getSimpleName();
    private boolean multipleMode;
    private final Context context;
    @Getter
    private final List<Song> songsList;
    @Setter
    private ItemTouchHelper itemTouchHelper;

    public SongRecyclerViewAdapter(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.multipleMode = false;
        this.songsList = new ArrayList<>();
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.song, parent, false);
        return new SongViewHolder(itemView, itemTouchHelper);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        Song currentSong = songsList.get(position);
        holder.bind(currentSong);
    }

    @Override
    public int getItemCount() {
        return songsList == null ? 0 : songsList.size();
    }

    public void setMultipleMode(boolean isMultiple) {
        boolean oldMode = this.multipleMode;
        this.multipleMode = isMultiple;
        if (oldMode && !isMultiple) {
            for (Song song : songsList) {
                song.setSelected(false);
            }
            notifyDataSetChanged();
            broadcastSelectionState();
        } else if (!oldMode && isMultiple) {
            Intent intent = new Intent(PLAYLIST_NOTIFICATION_MULTIPLE_SELECTED.getCode());
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
            notifyItemRangeChanged(0, getItemCount());
            broadcastSelectionState();
        } else {
            notifyItemRangeChanged(0, getItemCount());
        }
    }

    public void clearCurrentSelections() {
        if (songsList != null) {
            boolean changed = false;
            for (Song song : songsList) {
                if (song.isSelected()) {
                    song.setSelected(false);
                    changed = true;
                }
            }
            if (changed) {
                notifyItemRangeChanged(0, getItemCount());
                broadcastSelectionState();
            }
        }
    }

    private void broadcastSelectionState() {
        boolean anySelected = false;
        if (songsList != null) {
            for (Song song : songsList) {
                if (song.isSelected()) {
                    anySelected = true;
                    break;
                }
            }
        }
        Intent selectionIntent;
        if (anySelected) {
            selectionIntent = new Intent(PLAYLIST_NOTIFICATION_SOME_DELETE_SELECTED.getCode());
        } else {
            selectionIntent = new Intent(PLAYLIST_NOTIFICATION_SONGS_UPDATE_DONE.getCode());
        }
        LocalBroadcastManager.getInstance(context).sendBroadcast(selectionIntent);
    }

    public void updateSongs(List<Song> newSongs) {
        Log.d(TAG, "Clearing songs list");
        this.songsList.clear();
        if (newSongs != null) {
            Log.d(TAG, "Updating songs list " + newSongs.size());
            this.songsList.addAll(newSongs);
        }
        notifyDataSetChanged();
    }

    /**
     * When item is moved , swap it in list of songs
     * @param fromPosition source position
     * @param toPosition destination position
     */
    @Override
    public void onRowMoved(int fromPosition, int toPosition) {
        Collections.swap(songsList, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
    }


    @Override
    public void onRowSelected(SongViewHolder viewHolder) {
        Log.d(TAG, "Row selected ");
    }

    /**
     * Upon done, update row positions in song list
     * @param viewHolder view holder
     */
    @Override
    public void onRowClear(SongViewHolder viewHolder) {
        for (int i = 0; i < songsList.size(); i++) {
            val song = songsList.get(i);
            song.setPlaylistPosition(i);
        }
    }


    class SongViewHolder extends RecyclerView.ViewHolder {
        private final TextView fileNameTextView;
        private final CheckBox songCheckBox;
        private final ImageView moveIcon;
        private final ImageView musicIcon;
        private final View songLayout;

        SongViewHolder(@NonNull View itemView, @NotNull final ItemTouchHelper touchHelper) {
            super(itemView);
            fileNameTextView = itemView.findViewById(R.id.song_filename);
            songCheckBox = itemView.findViewById(R.id.song_checkbox);
            moveIcon = itemView.findViewById(R.id.move_icon);
            musicIcon = itemView.findViewById(R.id.music_symbol);
            songLayout = itemView.findViewById(R.id.song_layout);

//            itemView.setOnClickListener(v -> {
//                int position = getBindingAdapterPosition();
//                if (position != RecyclerView.NO_POSITION) {
//                    Song song = songsList.get(position);
//                    if (!multipleMode) {
//                        //play song.
//                    }
//                    // In multipleMode, row click does nothing to selection state here.
//                }
//            });

            itemView.setOnLongClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    if (!multipleMode) {
                        setMultipleMode(true);
                        broadcastSelectionState();
                        return true;
                    }
                }
                return false;
            });
            songCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    Song song = songsList.get(position);
                    if (buttonView.isPressed() || song.isSelected() != isChecked) {
                        song.setSelected(isChecked);
                        broadcastSelectionState();
                    }
                }
            });
            moveIcon.setOnTouchListener((view,event)->{
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    if (touchHelper != null) {
                        touchHelper.startDrag(this); // 'this' is the ViewHolder
                    }
                }
                return false;
            });
        }

        void bind(Song song) {
            fileNameTextView.setText(song.getDisplayName());
            songCheckBox.setChecked(song.isSelected());
            int padding_10dp = (int) (10 * context.getResources().getDisplayMetrics().density + 0.5f);
            if (!multipleMode) {
                songCheckBox.setVisibility(View.GONE);
                moveIcon.setVisibility(View.GONE);
                musicIcon.setVisibility(View.VISIBLE);
                if (songLayout != null) {
                    songLayout.setPadding(0, padding_10dp, 0, padding_10dp);
                }
            } else {
                songCheckBox.setVisibility(View.VISIBLE);
                moveIcon.setVisibility(View.VISIBLE);
                musicIcon.setVisibility(View.GONE);
                if (songLayout != null) {
                    songLayout.setPadding(0, 0, 0, 0);
                }
            }
        }
    }
}