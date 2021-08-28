package pl.qprogramming.themplay.playlist;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import java.util.List;

import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import lombok.Getter;
import lombok.val;
import pl.qprogramming.themplay.R;

import static pl.qprogramming.themplay.playlist.util.Utils.getThemeColor;

/**
 * {@link RecyclerView.Adapter} that can display a {@link Playlist}.
 */
@Getter
public class PlaylistItemRecyclerViewAdapter extends RecyclerView.Adapter<PlaylistItemRecyclerViewAdapter.ViewHolder> {

    private static final String TAG = PlaylistItemRecyclerViewAdapter.class.getSimpleName();
    private List<Playlist> playlists;

    private PlaylistService playlistService;
    private boolean serviceIsBound;

    public PlaylistItemRecyclerViewAdapter(List<Playlist> items) {
        playlists = items;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        val intent = new Intent(parent.getContext(), PlaylistService.class);
        parent.getContext().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.playlist_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        val playlist = playlists.get(position);
        holder.playlist = playlist;
        holder.mPlaylistName.setText(playlist.getName());
        holder.mCurrentFilename.setText(playlist.getCurrentFile());
        //render is active
        if (playlist.isActive()) {
            holder.mCardView.setBackgroundColor(getThemeColor(holder.mCardView, R.attr.colorSecondary));
            holder.mCurrentFilename.setVisibility(View.VISIBLE);
        } else {
            holder.mCardView.setBackgroundColor(getThemeColor(holder.mCardView, R.attr.colorOnPrimary));
            holder.mCurrentFilename.setVisibility(View.INVISIBLE);
        }
        //action menu
        holder.actionMenu.setOnClickListener(view -> {
            val popup = new PopupMenu(holder.mView.getContext(), holder.actionMenu);
            popup.getMenuInflater().inflate(R.menu.playlist_menu, popup.getMenu());
            popup.setOnMenuItemClickListener(item -> {
                val itemId = item.getItemId();
                if (itemId == R.id.editPlaylist) {
                    Log.d(TAG, "Editing playlist " + playlist);
                } else if (itemId == R.id.deletePlaylist) {
                    Log.d(TAG, "Deleting playlist " + playlist);
                } else {
                    throw new IllegalStateException("Unexpected value: " + itemId);
                }
                return true;
            });
            popup.show();
        });
        holder.mTextWrapper.setOnClickListener(contentView -> playlistService.setActive(playlist, holder.mView));
    }


    @Override
    public int getItemCount() {
        return playlists.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        public final LinearLayout mTextWrapper;
        public final TextView mPlaylistName;
        public final TextView mCurrentFilename;
        public final CardView mCardView;
        public final ImageView actionMenu;
        public Playlist playlist;

        public ViewHolder(View view) {
            super(view);
            mView = view;
            mPlaylistName = (TextView) view.findViewById(R.id.playlist_name);
            mCurrentFilename = (TextView) view.findViewById(R.id.now_playing);
            mCardView = (CardView) view.findViewById(R.id.card_view);
            actionMenu = (ImageView) view.findViewById(R.id.item_menu);
            mTextWrapper = (LinearLayout) view.findViewById(R.id.text_wrapper);
        }

        @Override
        public String toString() {
            return super.toString() + " '" + mPlaylistName.getText() + "'";
        }
    }

    private final ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            playlistService = ((PlaylistService.LocalBinder) service).getService();
            serviceIsBound = true;
            playlists = playlistService.getPlaylists();
        }

        public void onServiceDisconnected(ComponentName className) {
            playlistService = null;
        }
    };
}