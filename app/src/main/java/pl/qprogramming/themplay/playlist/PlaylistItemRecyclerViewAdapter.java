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
 * TODO: Replace the implementation with code for your data type.
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
        val pItem = playlists.get(position);
        holder.pItem = pItem;
        holder.mPlaylistName.setText(pItem.getName());
        holder.mCurrentFilename.setText(pItem.getCurrentFile());
        if (pItem.isActive()) {
            holder.mCardView.setBackgroundColor(getThemeColor(holder.mCardView, R.attr.colorSecondary));
            holder.mCurrentFilename.setVisibility(View.VISIBLE);
        } else {
            holder.mCardView.setBackgroundColor(getThemeColor(holder.mCardView, R.attr.colorOnPrimary));
            holder.mCurrentFilename.setVisibility(View.INVISIBLE);
        }
        holder.actionMenu.setOnClickListener(view -> {
            Log.d(TAG, "Show menu");
            view.getParent().showContextMenuForChild(view);
        });
        holder.mTextWrapper.setOnClickListener(contentView -> playlistService.setActive(pItem, holder.mView));
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
        public Playlist pItem;

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