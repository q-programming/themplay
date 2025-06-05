package pl.qprogramming.themplay.views;

import static pl.qprogramming.themplay.util.Utils.ARGS;
import static pl.qprogramming.themplay.util.Utils.HEIGHT;
import static pl.qprogramming.themplay.util.Utils.PLAYLIST;
import static pl.qprogramming.themplay.util.Utils.POSITION;
import static pl.qprogramming.themplay.util.Utils.WIDTH;
import static pl.qprogramming.themplay.util.Utils.applyPlaylistStyle;
import static pl.qprogramming.themplay.util.Utils.getThemeColor;
import static pl.qprogramming.themplay.util.Utils.isEmpty;
import static pl.qprogramming.themplay.util.Utils.loadColorsArray;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.switchmaterial.SwitchMaterial;

import lombok.val;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.activities.ChangeBackgroundActivity;
import pl.qprogramming.themplay.domain.Playlist;
import pl.qprogramming.themplay.playlist.EventType;
import pl.qprogramming.themplay.playlist.PlaylistService;

/**
 *
 */
public class PlaylistThemeFragment extends Fragment {
    private static final String TAG = PlaylistThemeFragment.class.getSimpleName();
    private Playlist playlist;

    private PlaylistService playlistService;
    private boolean serviceIsBound;
    private int position;
    private View mView;
    private ImageView activeBackground;
    private TextView activeName;
    private TextView activeSong;
    private FrameLayout activeIndicator;
    private ImageView activeMenu;

    private ImageView inactiveBackground;
    private TextView inactiveName;
    private ImageView inactiveMenu;

    private int activeColor;
    private int[] colorArray;

    public PlaylistThemeFragment() {
    }

    public PlaylistThemeFragment(Playlist playlist, int position) {
        this.playlist = playlist;
        this.position = position;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.playlist_theme_fragment_, container, false);
    }

    @Override
    @SuppressLint("CheckResult")
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        val context = this.requireContext();
        val playlistServiceIntent = new Intent(context, PlaylistService.class);
        context.bindService(playlistServiceIntent, mConnection, Context.BIND_AUTO_CREATE);
        if (playlist == null) {
            requireActivity().getSupportFragmentManager().popBackStack();
        } else {
            loadElements(view);
            loadColors();
            loadButtonsAndClickListeners(view, context);
            val filter = new IntentFilter(EventType.PLAYLIST_CHANGE_BACKGROUND.getCode());
            LocalBroadcastManager.getInstance(context).registerReceiver(receiver, filter);
            updatePreview();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            LocalBroadcastManager.getInstance(requireActivity()).unregisterReceiver(receiver);

        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Receiver not registered");
        }
    }

    @Override
    public void onStop() {
        if (serviceIsBound) {
            this.requireContext().unbindService(mConnection);
            serviceIsBound = false;
        }
        super.onStop();
    }

    private void loadButtonsAndClickListeners(@NonNull View view, Context context) {
        view.findViewById(R.id.change_background).setOnClickListener(v -> {
            val intent = new Intent(context, ChangeBackgroundActivity.class);
            val args = new Bundle();
            int width = view.getWidth();
            int playlistMaxHeight = Math.round(getResources().getDimension(R.dimen.playlist_max_height));
            args.putSerializable(PLAYLIST, playlist);
            args.putInt(POSITION, position);
            args.putInt(WIDTH, width);
            args.putInt(HEIGHT, playlistMaxHeight);
            intent.putExtra(ARGS, args);
            context.startActivity(intent);
        });
        view.findViewById(R.id.remove_background).setOnClickListener(v -> {
            playlist.setBackgroundImage(null);
            playlistService.save(playlist,updated->{
                playlist = updated;
                updatePreview();
            });
        });
        view.findViewById(R.id.change_text_color).setOnClickListener(v -> {
            String[] colors = {
                    context.getString(R.string.playlist_look_default),
                    context.getString(R.string.playlist_look_white),
                    context.getString(R.string.playlist_look_black),
                    context.getString(R.string.playlist_look_blue),
                    context.getString(R.string.playlist_look_green),
                    context.getString(R.string.playlist_look_red),
                    context.getString(R.string.playlist_look_yellow),
                    context.getString(R.string.playlist_look_gray),
                    context.getString(R.string.playlist_look_violet)
            };
            colors[playlist.getTextColor()] = colors[playlist.getTextColor()] + " ✓";
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(context.getString(R.string.playlist_look_change_text_color));
            builder.setItems(colors, (dialog, selected) -> {
                playlist.setTextColor(selected);
                playlistService.save(playlist,updated->{
                    playlist = updated;
                    updatePreview();
                });
            });
            builder.show();
        });
        val switchBtn = (SwitchMaterial) view.findViewById(R.id.toggle_text_outline);
        if (playlist.isTextOutline()) {
            switchBtn.toggle();
        }
        switchBtn.setOnCheckedChangeListener((buttonView, isChecked) -> {
            playlist.setTextOutline(isChecked);
            playlistService.save(playlist,updated->{
                playlist = updated;
                updatePreview();
            });
        });
    }

    private void loadColors() {
        activeColor = getThemeColor(mView, R.attr.colorSecondary);
        colorArray = loadColorsArray(mView.getContext());
    }

    /**
     * Loads all elements to properly reflect all changes
     */
    private void loadElements(@NonNull View view) {
        mView = view;
        val textView = (TextView) view.findViewById(R.id.header_title);
        textView.setText(getString(R.string.playlist_change_look));
        view.findViewById(R.id.include).setOnClickListener(clicked -> updateListAndGoBack());

        //active
        val activeView = view.findViewById(R.id.activePlaylist);
        activeName = activeView.findViewById(R.id.playlist_name);
        activeSong = activeView.findViewById(R.id.now_playing);
        activeSong.setText(view.getContext().getString(R.string.playlist_look_song));
        activeBackground = activeView.findViewById(R.id.card_background);
        activeIndicator = activeView.findViewById(R.id.is_active);
        activeMenu = activeView.findViewById(R.id.playlist_menu_btn);
        val inactiveView = view.findViewById(R.id.inactivePlaylist);
        inactiveName = inactiveView.findViewById(R.id.playlist_name);
        inactiveBackground = inactiveView.findViewById(R.id.card_background);
        inactiveMenu = inactiveView.findViewById(R.id.playlist_menu_btn);
    }

    private void updatePreview() {
        val textColor = colorArray[playlist.getTextColor()];
        activeName.setText(playlist.getName());
        activeName.setTextColor(textColor);
        activeSong.setTextColor(textColor);
        DrawableCompat.setTint(
                DrawableCompat.wrap(activeMenu.getDrawable()),
                textColor
        );
        applyPlaylistStyle(textColor, activeName, playlist.isTextOutline());
        applyPlaylistStyle(textColor, activeSong, playlist.isTextOutline());
        val removeBtn = mView.findViewById(R.id.remove_background);
        if (!isEmpty(playlist.getBackgroundImage())) {
            byte[] decodedString = Base64.decode(playlist.getBackgroundImage(), Base64.DEFAULT);
            Bitmap decodedImage = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
            activeBackground.setImageBitmap(decodedImage);
            activeBackground.setScaleType(ImageView.ScaleType.CENTER_CROP);
            removeBtn.setVisibility(View.VISIBLE);
        } else {
            removeBtn.setVisibility(View.GONE);
            activeBackground.setImageBitmap(null);
        }
        activeIndicator.setVisibility(View.VISIBLE);
        activeIndicator.setBackgroundColor(activeColor);

        inactiveName.setText(playlist.getName());
        inactiveName.setTextColor(textColor);
        applyPlaylistStyle(textColor, inactiveName, playlist.isTextOutline());

        DrawableCompat.setTint(
                DrawableCompat.wrap(inactiveMenu.getDrawable()),
                textColor
        );
        if (!isEmpty(playlist.getBackgroundImage())) {
            byte[] decodedString = Base64.decode(playlist.getBackgroundImage(), Base64.DEFAULT);
            Bitmap decodedImage = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
            inactiveBackground.setImageBitmap(decodedImage);
            inactiveBackground.setAlpha(0.5f);
            inactiveBackground.setScaleType(ImageView.ScaleType.CENTER_CROP);

        } else {
            inactiveBackground.setImageBitmap(null);
        }
    }

    private void updateListAndGoBack() {
        requireActivity()
                .getSupportFragmentManager()
                .popBackStack();
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @SuppressLint("CheckResult")
        @Override
        public void onReceive(Context context, Intent intent) {
            playlistService.findById(playlist.getId(), fetchedPlaylist -> {
                playlist = fetchedPlaylist;
                updatePreview();
            }, throwable -> Log.e(TAG, "Error getting playlist", throwable));
        }
    };

    private final ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            val binder = (PlaylistService.LocalBinder) service;
            playlistService = binder.getService();
            serviceIsBound = true;
        }

        public void onServiceDisconnected(ComponentName className) {
            playlistService = null;
        }
    };


}