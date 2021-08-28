package pl.qprogramming.themplay.playlist;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import lombok.Getter;
import lombok.val;
import pl.qprogramming.themplay.R;

/**
 * {@link RecyclerView.Adapter} that can display a {@link Playlist}.
 * TODO: Replace the implementation with code for your data type.
 */
@Getter
public class PlaylistItemRecyclerViewAdapter extends RecyclerView.Adapter<PlaylistItemRecyclerViewAdapter.ViewHolder> {

    private List<Playlist> playlists = new ArrayList<>();

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
        holder.mContentView.setText(pItem.getName());
        holder.mContentView.setOnClickListener(contentView -> playlistService.setActive(pItem, holder.mView));
        if (pItem.isActive()) {
            holder.mCardView.setBackgroundColor(ContextCompat.getColor(holder.mCardView.getContext(), R.color.teal_200));
        } else {
            holder.mCardView.setBackgroundColor(ContextCompat.getColor(holder.mCardView.getContext(), R.color.design_default_color_background));
        }
    }

    @Override
    public int getItemCount() {
        return playlists.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        public final TextView mContentView;
        public final CardView mCardView;
        public final ImageView actionMenu;
        public Playlist pItem;

        public ViewHolder(View view) {
            super(view);
            mView = view;
            mContentView = (TextView) view.findViewById(R.id.content);
            mCardView = (CardView) view.findViewById(R.id.card_view);
            actionMenu = (ImageView) view.findViewById(R.id.item_menu);
        }

        @Override
        public String toString() {
            return super.toString() + " '" + mContentView.getText() + "'";
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