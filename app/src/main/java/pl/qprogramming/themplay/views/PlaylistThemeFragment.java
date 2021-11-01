package pl.qprogramming.themplay.views;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.reactiveandroid.query.Select;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;
import lombok.val;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.activities.ChangeBackgroundActivity;
import pl.qprogramming.themplay.playlist.EventType;
import pl.qprogramming.themplay.playlist.Playlist;

import static pl.qprogramming.themplay.util.Utils.ARGS;
import static pl.qprogramming.themplay.util.Utils.HEIGHT;
import static pl.qprogramming.themplay.util.Utils.PLAYLIST;
import static pl.qprogramming.themplay.util.Utils.POSITION;
import static pl.qprogramming.themplay.util.Utils.WIDTH;
import static pl.qprogramming.themplay.util.Utils.applyPlaylistStyle;
import static pl.qprogramming.themplay.util.Utils.getThemeColor;
import static pl.qprogramming.themplay.util.Utils.isEmpty;
import static pl.qprogramming.themplay.util.Utils.loadColorsArray;

/**
 *
 */
public class PlaylistThemeFragment extends Fragment {
    private static final String TAG = PlaylistThemeFragment.class.getSimpleName();
    private Playlist playlist;
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
        if (playlist == null) {
            requireActivity().getSupportFragmentManager().popBackStack();
        } else {
            val context = view.getContext();
            loadElements(view);
            loadColors();
            loadButtonsAndClickListeners(view, context);
            val filter = new IntentFilter(EventType.PLAYLIST_CHANGE_BACKGROUND.getCode());
            context.registerReceiver(receiver, filter);
            updatePreview();
        }
    }

    private void loadButtonsAndClickListeners(@NonNull View view, Context context) {
        view.findViewById(R.id.change_background).setOnClickListener(v -> {
            val intent = new Intent(context, ChangeBackgroundActivity.class);
            val args = new Bundle();
            val width = view.getWidth();
            val height = getResources().getDimension(R.dimen.playlist_max_height);
            args.putSerializable(PLAYLIST, playlist);
            args.putSerializable(POSITION, position);
            args.putSerializable(WIDTH, width);
            args.putSerializable(HEIGHT, Math.round(height));
            intent.putExtra(ARGS, args);
            context.startActivity(intent);
        });
        view.findViewById(R.id.remove_background).setOnClickListener(v -> {
            playlist.setBackgroundImage(null);
            playlist.save();
            updatePreview();
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
            colors[playlist.getTextColor()] = colors[playlist.getTextColor()] + " \u2713";
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(context.getString(R.string.playlist_look_change_text_color));
            builder.setItems(colors, (dialog, selected) -> {
                playlist.setTextColor(selected);
                playlist.save();
                updatePreview();
            });
            builder.show();
        });
        val switchBtn = (SwitchMaterial) view.findViewById(R.id.toggle_text_outline);
        if (playlist.isTextOutline()) {
            switchBtn.toggle();
        }
        switchBtn.setOnCheckedChangeListener((buttonView, isChecked) -> {
            playlist.setTextOutline(isChecked);
            playlist.save();
            updatePreview();
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
            playlist = Select.from(Playlist.class).where("id = ?", playlist.getId()).fetchSingle();
            updatePreview();
        }
    };


}