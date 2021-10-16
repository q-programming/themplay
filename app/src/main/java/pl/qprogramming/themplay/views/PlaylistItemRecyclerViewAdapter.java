package pl.qprogramming.themplay.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import java.text.MessageFormat;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;
import lombok.val;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.playlist.EventType;
import pl.qprogramming.themplay.playlist.Playlist;
import pl.qprogramming.themplay.playlist.PlaylistService;

import static pl.qprogramming.themplay.util.Utils.applyPlaylistStyle;
import static pl.qprogramming.themplay.util.Utils.getThemeColor;
import static pl.qprogramming.themplay.util.Utils.isEmpty;
import static pl.qprogramming.themplay.util.Utils.loadColorsArray;
import static pl.qprogramming.themplay.util.Utils.navigateToFragment;

/**
 * {@link RecyclerView.Adapter} that can display a {@link Playlist}.
 */
public class PlaylistItemRecyclerViewAdapter extends RecyclerView.Adapter<PlaylistItemRecyclerViewAdapter.ViewHolder> {

    private static final String TAG = PlaylistItemRecyclerViewAdapter.class.getSimpleName();
    private List<Playlist> playlists;
    private int activeColor;


    private final PlaylistService playlistService;
    private FragmentManager fmanager;
    private int[] colorArray;

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
        if (playlist.getCurrentSong() != null) {
            holder.mCurrentFilename.setText(playlist.getCurrentSong().getFilename());
        }
        if (!isEmpty(playlist.getBackgroundImage())) {
            byte[] decodedString = Base64.decode(playlist.getBackgroundImage(), Base64.DEFAULT);
            Bitmap decodedImage = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
            holder.background.setImageBitmap(decodedImage);
            holder.background.setScaleType(ImageView.ScaleType.CENTER_CROP);

        }
        //render is active
        setActive(holder, position, playlist);
        //action menu
        configureMenu(holder, position, playlist);
        holder.mTextWrapper.setOnClickListener(contentView -> setActive(position, playlist));
    }

    private void loadColors(Context context) {
        activeColor = getThemeColor(context, R.attr.colorSecondary);
        colorArray = loadColorsArray(context);
    }

    private void setActive(@NonNull ViewHolder holder, int position, Playlist playlist) {
        if (playlist.isActive()) {
            if (!isEmpty(playlist.getBackgroundImage())) {
                holder.background.setAlpha(1f);
            }
            holder.active.setBackgroundColor(activeColor);
            holder.active.setVisibility(View.VISIBLE);
            holder.mCurrentFilename.setVisibility(View.VISIBLE);
            playlistService.setActivePlaylistPosition(position);
        } else {
            if (!isEmpty(playlist.getBackgroundImage())) {
                holder.background.setAlpha(0.5f);
            }
            holder.active.setVisibility(View.INVISIBLE);
            holder.mCurrentFilename.setVisibility(View.INVISIBLE);
        }
    }

    @SuppressLint("CheckResult")
    private void configureMenu(@NonNull ViewHolder holder, int position, Playlist playlist) {
        holder.actionMenu.setOnClickListener(view -> {
            val popup = new PopupMenu(holder.mView.getContext(), holder.actionMenu);
            popup.getMenuInflater().inflate(R.menu.playlist_menu, popup.getMenu());
            popup.setOnMenuItemClickListener(item -> {
                val itemId = item.getItemId();
                val context = holder.mCardView.getContext();
                if (itemId == R.id.editPlaylist) {
                    holder.mView.getContext().sendBroadcast(new Intent(EventType.OPERATION_STARTED.getCode()));
                    playlistService.fetchSongsByPlaylistAsync(playlist)
                            .subscribe(songs -> {
                                context.sendBroadcast(new Intent(EventType.OPERATION_FINISHED.getCode()));
                                playlist.setSongs(songs);
                                navigateToFragment(
                                        fmanager,
                                        new PlaylistSettingsFragment(playlistService, playlist),
                                        playlist.getName() + playlist.getId());

                            });
                    Log.d(TAG, "Editing playlist " + playlist);
                } else if (itemId == R.id.deletePlaylist) {
                    val msg = MessageFormat.format(context.getString(R.string.playlist_delete_playlist_confirm), playlist.getName());
                    new AlertDialog.Builder(context)
                            .setTitle(context.getString(R.string.playlist_delete_playlist))
                            .setMessage(msg)
                            .setPositiveButton(context.getString(R.string.delete), (dialog, which) -> removePlaylist(position, playlist))
                            .setNegativeButton(context.getString(R.string.cancel), (dialog, which) -> dialog.cancel())
                            .show();
                } else if (itemId == R.id.change_look) {
                    navigateToFragment(
                            fmanager,
                            new PlaylistThemeFragment(playlist, position),
                            "theme" + playlist.getName() + playlist.getId());
                } else {
                    throw new IllegalStateException("Unexpected value: " + itemId);
                }
                return true;
            });
            popup.show();
        });
    }

    private void setActive(int position, Playlist playlist) {
        if (playlistService != null) {
            playlistService.setActive(playlist, position);
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