package pl.qprogramming.themplay.views;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.List;

import lombok.Setter;
import lombok.val;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.domain.Song;

public class SongListViewAdapter extends ArrayAdapter<Song> {
    public static final String MULTIPLE_SELECTED = "q-programming.themplay.playlist.multiple";
    @Setter
    private List<Song> songs;
    @Setter
    private boolean multiple;
    private final Context context;

    public SongListViewAdapter(@NonNull Context context, List<Song> songs, boolean multiple) {
        super(context, R.layout.song, songs);
        this.songs = songs;
        this.context = context;
        this.multiple = multiple;
    }


    @NonNull
    @Override
    public View getView(int position, @Nullable View view, @NonNull ViewGroup parent) {
        val rowView = LayoutInflater.from(context).inflate(R.layout.song, null, true);
        val song = songs.get(position);
        val fileName = (TextView) rowView.findViewById(R.id.song_filename);
        val checkBox = (CheckBox) rowView.findViewById(R.id.song_checkbox);
        val music = rowView.findViewById(R.id.music_symbol);
        if (!multiple) {
            checkBox.setVisibility(View.GONE);
            music.setVisibility(View.VISIBLE);
        } else {
            checkBox.setVisibility(View.VISIBLE);
            music.setVisibility(View.GONE);
        }
        checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> song.setSelected(isChecked));
        fileName.setText(song.getDisplayName());
        checkBox.setChecked(song.isSelected());
        rowView.setOnLongClickListener(click -> {
            song.setSelected(true);
            checkBox.setChecked(true);
            multiple = true;
            Intent intent = new Intent(MULTIPLE_SELECTED);
            LocalBroadcastManager.getInstance(rowView.getContext()).sendBroadcast(intent);
            return true;
        });
        return rowView;
    }

    @Override
    public int getCount() {
        return this.songs != null ? this.songs.size() : 0;
    }

    public void clearSelections() {
        if (songs != null) {
            for (Song song : songs) {
                song.setSelected(false);
            }
        }
        notifyDataSetChanged();
    }
}