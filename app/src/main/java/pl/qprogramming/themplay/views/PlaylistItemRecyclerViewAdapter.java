package pl.qprogramming.themplay.views;

import static pl.qprogramming.themplay.playlist.EventType.PLAYLIST_NOTIFICATION_NEW_ACTIVE;
import static pl.qprogramming.themplay.settings.Property.COPY_PLAYLIST;
import static pl.qprogramming.themplay.util.Utils.ARGS;
import static pl.qprogramming.themplay.util.Utils.applyPlaylistStyle;
import static pl.qprogramming.themplay.util.Utils.getThemeColor;
import static pl.qprogramming.themplay.util.Utils.isEmpty;
import static pl.qprogramming.themplay.util.Utils.loadColorsArray;
import static pl.qprogramming.themplay.util.Utils.navigateToFragment;
import static pl.qprogramming.themplay.util.Utils.retrieveImageForPlaylist;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.media3.common.util.UnstableApi;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.Setter;
import lombok.val;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.domain.Playlist;
import pl.qprogramming.themplay.logger.Logger;
import pl.qprogramming.themplay.playlist.PlaylistService;
import pl.qprogramming.themplay.util.Utils;

/**
 * {@link RecyclerView.Adapter} that can display a {@link Playlist}.
 * This adapter is responsible for managing the display of playlist items in a RecyclerView.
 * It handles the creation of view holders for each item, binding {@link Playlist} data
 * to the corresponding views (including text, colors, and background images), and setting up
 * user interaction handlers.
 * <p>
 * Its core responsibilities include:
 * <ul>
 *   <li>Populating the views within each item with the data from a {@link Playlist} object.
 *       This involves setting the playlist name, current song (if any), text colors,
 *       and background images.</li>
 *   <li>Managing the visual state of items, such as indicating the active playlist.</li>
 *   <li>Handling user actions performed on playlist items, such as setting a playlist as active,
 *       editing, deleting, copying, or changing its theme. These actions are typically
 *       delegated to a {@link PlaylistService} or trigger navigation to other fragments.</li>
 *   <li>Providing the total count of playlists to the RecyclerView.</li>
 *   <li>Reloading or replacing playlist data when it changes.</li>
 * </ul>
 * It also implements {@link ItemMoveCallback.ItemTouchHelperContract} to support drag-and-drop
 * functionality for reordering playlists. This involves updating the adapter's internal list,
 * notifying the RecyclerView of the move, and persisting the new order via the
 * {@link PlaylistService}.
 */
public class PlaylistItemRecyclerViewAdapter extends RecyclerView.Adapter<PlaylistItemRecyclerViewAdapter.ViewHolder> implements ItemMoveCallback.ItemTouchHelperContract<PlaylistItemRecyclerViewAdapter.ViewHolder> {

    private static final String TAG = PlaylistItemRecyclerViewAdapter.class.getSimpleName();
    private final List<Playlist> playlists = new ArrayList<>();
    private int activeColor;
    private int cardBackgroundColor;
    @Setter
    private boolean isPlaying;


    private final PlaylistService playlistService;
    private final FragmentManager fmanager;
    private int[] colorArray;

    @SuppressLint("CheckResult")
    public PlaylistItemRecyclerViewAdapter(PlaylistService playlistService, FragmentActivity activity) {
        this.playlistService = playlistService;
        if (activity != null) {
            this.fmanager = activity.getSupportFragmentManager();
        } else {
            this.fmanager = null;
        }
    }

    /**
     * Loads all playlists into adapter.
     */
    public void loadPlaylists() {
        playlistService.getAllByPresetName(updatedPlaylists -> {
            this.playlists.clear();
            this.playlists.addAll(updatedPlaylists);
            notifyDataSetChanged();
        }, throwable -> {
            Logger.e("Adapter", "Error loading playlists into adapter", throwable);
            this.playlists.clear();
            notifyDataSetChanged();
        });
    }

    @Override
    @NonNull
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.playlist_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    @UnstableApi
    public void onBindViewHolder(@NonNull final ViewHolder holder, int position) {
        //it might happen service is not yet connected
        val playlist = playlists.get(position);
        loadColors(holder.mView.getContext());
        holder.mPlaylistName.setText(playlist.getName());
        holder.mPlaylistName.setText(MessageFormat.format("{0} ({1})", playlist.getName(), playlist.getSongCount()));
        int textColor = colorArray[playlist.getTextColor()];

        applyPlaylistStyle(textColor, holder.mPlaylistName, playlist.isTextOutline());
        applyPlaylistStyle(textColor, holder.mCurrentFilename, playlist.isTextOutline());
        DrawableCompat.setTint(
                DrawableCompat.wrap(holder.actionMenu.getDrawable()),
                textColor
        );
        holder.playlist = playlist;
        if (playlist.getCurrentSongTitle() != null && isPlaying) {
            holder.mCurrentFilename.setText(playlist.getCurrentSongTitle());
        }else{
            holder.mCurrentFilename.setText("");
        }
        setBackgroundImage(holder, playlist);
//        if (!isEmpty(playlist.getBackgroundImage())) {
//            holder.mCardView.setBackgroundColor(Color.TRANSPARENT);
//            byte[] decodedString = Base64.decode(playlist.getBackgroundImage(), Base64.DEFAULT);
//            Bitmap decodedImage = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
//            holder.background.setImageBitmap(decodedImage);
//            holder.background.setScaleType(ImageView.ScaleType.CENTER_CROP);
//        } else {
//            holder.mCardView.setBackgroundColor(cardBackgroundColor);
//        }
        //render is active
        setActive(holder, playlist);
        //action menu
        configureMenu(holder, position, playlist);
        holder.mTextWrapper.setOnClickListener(contentView -> setActive(playlist));
    }

    private void setBackgroundImage(@NonNull final ViewHolder holder, Playlist playlist) {
        Bitmap decodedBitmap = retrieveImageForPlaylist(playlist);
        if (decodedBitmap != null) {
            holder.mCardView.setBackgroundColor(Color.TRANSPARENT);
            holder.background.setImageBitmap(decodedBitmap);
            holder.background.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else {
            holder.mCardView.setBackgroundColor(cardBackgroundColor);
            holder.background.setImageDrawable(null);
        }
    }

    private void loadColors(Context context) {
        activeColor = getThemeColor(context, R.attr.colorSecondary);
        cardBackgroundColor = getThemeColor(context, R.attr.card_background_color);
        colorArray = loadColorsArray(context);
    }

    private void setActive(@NonNull ViewHolder holder, Playlist playlist) {
        if (playlist.isActive()) {
            if (!isEmpty(playlist.getBackgroundImage())) {
                holder.background.setAlpha(1f);
            }
            holder.active.setBackgroundColor(activeColor);
            holder.active.setVisibility(View.VISIBLE);
            holder.mCurrentFilename.setVisibility(View.VISIBLE);
            holder.mCurrentFilename.setSelected(true);
        } else {
            if (!isEmpty(playlist.getBackgroundImage())) {
                holder.background.setAlpha(0.5f);
            }
            holder.active.setVisibility(View.INVISIBLE);
            holder.mCurrentFilename.setVisibility(View.INVISIBLE);
            holder.mCurrentFilename.setSelected(false);
        }
    }

    @UnstableApi
    private void configureMenu(@NonNull ViewHolder holder, int position, Playlist playlist) {
        holder.actionMenu.setOnClickListener(view -> {
            val popup = new PopupMenu(holder.mView.getContext(), holder.actionMenu);
            popup.getMenuInflater().inflate(R.menu.playlist_menu, popup.getMenu());
            val viewPlaylistItem = popup.getMenu().findItem(R.id.viewPlaylist);
            val viewVisible = playlist.isActive() && isPlaying;
            if (viewPlaylistItem != null) {
                viewPlaylistItem.setVisible(viewVisible);
            }
            popup.setOnMenuItemClickListener(item -> {
                val itemId = item.getItemId();
                val context = holder.mCardView.getContext();
                if (itemId == R.id.editPlaylist) {
                    Logger.d(TAG, "Editing playlist " + playlist.getName());
                    navigateToFragment(
                            fmanager,
                            PlaylistSettingsFragment.newInstance(playlist),
                            playlist.getName() + playlist.getId());
                } else if (itemId == R.id.deletePlaylist) {
                    Logger.d(TAG, "Deleting playlist " + playlist.getName());
                    val msg = MessageFormat.format(context.getString(R.string.playlist_delete_playlist_confirm), playlist.getName());
                    new AlertDialog.Builder(context)
                            .setTitle(context.getString(R.string.playlist_delete_playlist))
                            .setMessage(msg)
                            .setPositiveButton(context.getString(R.string.delete), (dialog, which) -> removePlaylist(playlist))
                            .setNegativeButton(context.getString(R.string.cancel), (dialog, which) -> dialog.cancel())
                            .show();
                } else if (itemId == R.id.change_look) {
                    navigateToFragment(
                            fmanager,
                            new PlaylistThemeFragment(playlist, position),
                            "theme" + playlist.getName() + playlist.getName());
                } else if (itemId == R.id.copy) {
                    Logger.d(TAG, "Copy playlist " + playlist.getId());
                    val spEdit = PreferenceManager.getDefaultSharedPreferences(context).edit();
                    spEdit.putLong(COPY_PLAYLIST, playlist.getId());
                    spEdit.apply();
                    Toast.makeText(context, context.getString(R.string.playlist_copied), Toast.LENGTH_LONG).show();
                } else if (itemId == R.id.viewPlaylist) {
                    Logger.d(TAG, "Viewing playlist " + playlist.getId());
                    navigateToFragment(
                            fmanager,
                            new PlaylistViewFragment(playlist),
                            playlist.getName() + playlist.getId());
                } else {
                    throw new IllegalStateException("Unexpected value: " + itemId);
                }
                return true;
            });
            popup.show();
        });
    }

    private void setActive(Playlist playlist) {
        if (playlistService != null) {
            playlistService.setActive(playlist, false);
        }
    }

    private void removePlaylist(Playlist playlist) {
        if (playlistService != null) {
            playlistService.removePlaylist(playlist);
        }
    }

    @Override
    public int getItemCount() {
        return playlists != null ? playlists.size() : 0;
    }

    public void reloadItemAt(int index) {
        var playlist = playlists.get(index);
        playlistService.findById(playlist.getId(), dbPlaylist -> {
            playlists.set(index, dbPlaylist);
            notifyItemChanged(index);
        }, throwable -> Logger.e(TAG, "Error loading playlist", throwable));
    }

    public void replaceItem(Playlist playlist) {
        val index = playlist.getPosition();
        playlists.set(index, playlist);
        notifyItemChanged(index);
    }

    @Override
    public void onRowMoved(int fromPosition, int toPosition) {
        Collections.swap(playlists, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
    }

    @Override
    public void onRowSelected(ViewHolder viewHolder) {
        if (!viewHolder.playlist.isActive()) {
            viewHolder.background.setAlpha(1f);
        }

    }

    @Override
    public void onRowClear(ViewHolder viewHolder) {
        if (!viewHolder.playlist.isActive()) {
            viewHolder.background.setAlpha(0.5f);
        }
        for (int i = 0; i < playlists.size(); i++) {
            val playlist = playlists.get(i);
            playlist.setPosition(i);
        }
        playlistService.saveAll(playlists);
        playlists
                .stream()
                .filter(Playlist::isActive)
                .findFirst()
                .ifPresent(playlist ->
                        playlistService
                                .loadSongs(playlist, playlistWithSongs -> {
                                    val intent = new Intent(PLAYLIST_NOTIFICATION_NEW_ACTIVE.getCode());
                                    val args = new Bundle();
                                    args.putSerializable(Utils.PLAYLIST, playlistWithSongs);
                                    intent.putExtra(ARGS, args);
                                    LocalBroadcastManager.getInstance(viewHolder.mView.getContext()).sendBroadcast(intent);
                                }, throwable -> Logger.e(TAG, "Error loading playlist", throwable)));
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        public final LinearLayout mTextWrapper;
        public final FrameLayout active;
        public final TextView mPlaylistName;
        public final TextView mCurrentFilename;
        public final CardView mCardView;
        public final ImageView actionMenu;
        public final ImageView background;
        public Playlist playlist;

        public ViewHolder(View view) {
            super(view);
            mView = view;
            mPlaylistName = view.findViewById(R.id.playlist_name);
            mCurrentFilename = view.findViewById(R.id.now_playing);
            mCardView = view.findViewById(R.id.card_view);
            actionMenu = view.findViewById(R.id.playlist_menu_btn);
            mTextWrapper = view.findViewById(R.id.text_wrapper);
            background = view.findViewById(R.id.card_background);
            active = view.findViewById(R.id.is_active);

        }

        @Override
        public String toString() {
            return super.toString() + " '" + mPlaylistName.getText() + "'";
        }
    }
}