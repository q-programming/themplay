package pl.qprogramming.themplay.views;

import android.annotation.SuppressLint;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import java.text.MessageFormat;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;
import lombok.val;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.playlist.Playlist;
import pl.qprogramming.themplay.playlist.PlaylistService;

import static pl.qprogramming.themplay.util.Utils.getThemeColor;
import static pl.qprogramming.themplay.util.Utils.navigateToFragment;

/**
 * {@link RecyclerView.Adapter} that can display a {@link Playlist}.
 */
public class PlaylistItemRecyclerViewAdapter extends RecyclerView.Adapter<PlaylistItemRecyclerViewAdapter.ViewHolder> {

    private static final String TAG = PlaylistItemRecyclerViewAdapter.class.getSimpleName();
    private List<Playlist> playlists;

    private final PlaylistService playlistService;
    private FragmentManager fmanager;

    @SuppressLint("CheckResult")
    public PlaylistItemRecyclerViewAdapter(PlaylistService playlistService, FragmentActivity activity) {
        this.playlistService = playlistService;
        if (activity != null) {
            this.fmanager = activity.getSupportFragmentManager();
        }
    }


    @Override
    @NonNull
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.playlist_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    @SuppressLint("CheckResult")
    public void onBindViewHolder(@NonNull final ViewHolder holder, int position) {
        //it might happen service is not yet connected
        val playlist = playlists.get(position);
        if (playlistService != null) {
            playlistService.fetchSongsByPlaylistAsync(playlist)
                    .subscribe(playlistSongs -> {
                        playlist.setSongs(playlistSongs);
                        holder.mPlaylistName.setText(MessageFormat.format("{0} {1} ({2})", playlist.getId(), playlist.getName(), playlist.getSongs().size()));
                    });
        }
        holder.playlist = playlist;
        if (playlist.getCurrentSong() != null) {
            holder.mCurrentFilename.setText(MessageFormat.format("{0} - {1}", playlist.getCurrentSong().getFilename(), playlist.getCurrentSong().getId()));
        }
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
                    playlistService.fetchSongsByPlaylistAsync(playlist)
                            .subscribe(songs -> {
                                playlist.setSongs(songs);
                                navigateToFragment(
                                        fmanager,
                                        new PlaylistSettingsFragment(playlistService, playlist),
                                        playlist.getName() + playlist.getId());

                            });
                    Log.d(TAG, "Editing playlist " + playlist);
                } else if (itemId == R.id.deletePlaylist) {
                    val context = holder.mCardView.getContext();
                    val msg = MessageFormat.format(context.getString(R.string.playlist_delete_playlist_confirm), playlist.getName());
                    new AlertDialog.Builder(context)
                            .setTitle(context.getString(R.string.playlist_delete_playlist))
                            .setMessage(msg)
                            .setPositiveButton(context.getString(R.string.delete), (dialog, which) -> removePlaylist(position, playlist))
                            .setNegativeButton(context.getString(R.string.cancel), (dialog, which) -> dialog.cancel())
                            .show();
                } else {
                    throw new IllegalStateException("Unexpected value: " + itemId);
                }
                return true;
            });
            popup.show();
        });
        holder.mTextWrapper.setOnClickListener(contentView -> setActive(holder, position, playlist));
    }

    private void setActive(@NonNull ViewHolder holder, int position, Playlist playlist) {
        if (playlistService != null) {
            playlistService.setActive(playlist, position, holder.mView);
        }
    }

    private void removePlaylist(int position, Playlist playlist) {
        if (playlistService != null) {
            playlistService.removePlaylist(playlist, position);
        }
    }

    @Override
    public int getItemCount() {
        if (playlistService != null) {
            playlists = playlistService.getAll();
        }
        return playlists.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
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
            mPlaylistName = view.findViewById(R.id.playlist_name);
            mCurrentFilename = view.findViewById(R.id.now_playing);
            mCardView = view.findViewById(R.id.card_view);
            actionMenu = view.findViewById(R.id.item_menu);
            mTextWrapper = view.findViewById(R.id.text_wrapper);
        }

        @Override
        public String toString() {
            return super.toString() + " '" + mPlaylistName.getText() + "'";
        }
    }
}