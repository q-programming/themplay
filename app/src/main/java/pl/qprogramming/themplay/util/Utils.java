package pl.qprogramming.themplay.util;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Random;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import lombok.val;
import lombok.var;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.playlist.Playlist;
import pl.qprogramming.themplay.playlist.Song;

public class Utils {

    public static final String POSITION = "position";
    public static final String SONG = "song";
    public static final String PLAYLIST = "playlist";
    public static final String PRESET = "preset";
    public static final String ARGS = "args";
    public static final String WIDTH = "width";
    public static final String HEIGHT = "height";


    private Utils() {
    }

    /**
     * Get theme color out of view
     *
     * @param view  view used to extract color
     * @param color color ID to be fetched
     * @return resolved color or 0 if not found
     */
    public static int getThemeColor(View view, int color) {
        return getThemeColor(view.getContext(), color);
    }

    /**
     * Get theme color out of context resources
     *
     * @param context context used to extract color
     * @param color   color ID to be fetched
     * @return resolved color or 0 if not found
     */
    public static int getThemeColor(Context context, int color) {
        val typedValue = new TypedValue();
        context.getTheme().resolveAttribute(color, typedValue, true);
        return typedValue.data;
    }

    /**
     * Checks if collection is null or empty
     *
     * @param coll collection to be tested
     * @return true if collection is null or empty
     */
    public static boolean isEmpty(Collection<?> coll) {
        return (coll == null || coll.isEmpty());
    }

    public static boolean isEmpty(String string) {
        return (string == null || string.isEmpty());
    }

    /**
     * Navigate to fragment using animations and adding name to stack
     *
     * @param fm       fragment maneger
     * @param fragment fragment to which navigation should go
     * @param name     name of new fragment to add to history stack
     */
    public static void navigateToFragment(FragmentManager fm, Fragment fragment, String name) {
        fm.beginTransaction()
                .setCustomAnimations(
                        R.anim.slide_in,
                        R.anim.fade_out,
                        R.anim.fade_in,
                        R.anim.slide_out
                )
                .replace(R.id.activity_fragment_layout, fragment)
                .addToBackStack(name)
                .commit();
    }

    /**
     * Shuffles all songs from playlist into random order. Then takes current playlist song and moves it to the end
     *
     * @param playlist playlists which songs should be shuffled
     */
    public static void createPlaylist(Playlist playlist, boolean shuffle) {
        val list = new ArrayList<>(playlist.getSongs());
        if (shuffle) {
            val shuffledPlaylist = new ArrayList<Song>();
            while (list.size() > 0) {
                var index = list.size() - 1;
                if (index > 0) {
                    index = new Random().nextInt(list.size() - 1);
                }
                shuffledPlaylist.add(list.get(index));
                list.remove(index);
            }
            shuffledPlaylist.remove(playlist.getCurrentSong());
            shuffledPlaylist.add(playlist.getCurrentSong());
            playlist.setPlaylist(shuffledPlaylist);
        } else {
            playlist.setPlaylist(list);
        }
        playlist.getPlaylist().removeAll(Collections.singleton(null));
    }

    public static void applyPlaylistStyle(int textColor, TextView textView, boolean textOutline) {
        textView.setTextColor(textColor);
        if (textOutline) {
            textView.setShadowLayer(1.6f, 1.5f, 1.5f, R.color.black);
        } else {
            textView.setShadowLayer(0f, 0f, 0f, 0);
        }
    }

    public static int[] loadColorsArray(Context context) {
        return new int[]{
                getThemeColor(context, R.attr.text_color_default),
                getThemeColor(context, R.attr.text_color_white),
                getThemeColor(context, R.attr.text_color_black),
                getThemeColor(context, R.attr.text_color_blue),
                getThemeColor(context, R.attr.text_color_green),
                getThemeColor(context, R.attr.text_color_red),
                getThemeColor(context, R.attr.text_color_yellow),
                getThemeColor(context, R.attr.text_color_gray)};
    }

}
