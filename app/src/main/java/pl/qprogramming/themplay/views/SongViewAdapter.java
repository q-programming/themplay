package pl.qprogramming.themplay.views;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import androidx.recyclerview.widget.RecyclerView;
import lombok.Getter;
import lombok.Setter;
import lombok.val;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.playlist.Song;

public class SongViewAdapter extends RecyclerView.Adapter<SongViewAdapter.ViewHolder> {
    public static final String MULTIPLE_SELECTED = "q-programming.themplay.playlist.multiple";
    @Setter
    private List<Song> songs;
    @Getter
    @Setter
    private boolean multiple;

    public SongViewAdapter(List<Song> items) {
        songs = items;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.song, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        val song = songs.get(position);
        holder.song = song;
        holder.fileName.setText(songs.get(position).getFilename());
        holder.checkBox.setChecked(song.isSelected());
        holder.mView.setOnLongClickListener(click -> {
            holder.song.setSelected(true);
            holder.checkBox.setChecked(true);
            multiple = true;
            notifyItemRangeChanged(0, songs.size());
            Intent intent = new Intent(MULTIPLE_SELECTED);
            holder.mView.getContext().sendBroadcast(intent);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        public final TextView fileName;
        public final CheckBox checkBox;
        public final ImageView music;
        public Song song;

        public ViewHolder(View view) {
            super(view);
            mView = view;
            fileName = view.findViewById(R.id.song_filename);
            checkBox = view.findViewById(R.id.song_checkbox);
            music = view.findViewById(R.id.music_symbol);
            if (!multiple) {
                checkBox.setVisibility(View.GONE);
                music.setVisibility(View.VISIBLE);
            } else {
                checkBox.setVisibility(View.VISIBLE);
                music.setVisibility(View.GONE);
            }
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> song.setSelected(isChecked));
        }

        @Override
        public String toString() {
            return super.toString() + " '" + fileName.getText() + "'";
        }
    }
}