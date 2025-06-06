package pl.qprogramming.themplay.util;

import android.content.Context;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Random;

import lombok.val;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.domain.Playlist;
import pl.qprogramming.themplay.domain.Song;

public class Utils {

    public enum VersionComparisonResult {
        CURRENT_IS_NEWER,
        VERSIONS_ARE_SAME,
        STORED_IS_NEWER,
        ERROR_PARSING
    }

    public static final String POSITION = "position";
    public static final String SONG = "song";
    public static final String PLAYLIST = "playlist";
    public static final String PRESET = "preset";
    public static final String ARGS = "args";
    public static final String WIDTH = "width";
    public static final String HEIGHT = "height";
    public static final String TAG = Utils.class.getSimpleName();


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
            while (!list.isEmpty()) {
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
                getThemeColor(context, R.attr.text_color_gray),
                getThemeColor(context, R.attr.text_color_violet)
        };
    }

    /**
     * Compares two semantic version strings (e.g., "1.0.0", "0.2.1-beta").
     * Handles versions like "X.Y.Z", "X.Y", or "X".
     * Suffixes like "-beta", "-rc" in a version segment are ignored for comparison,
     * meaning "1.2.3-beta" is treated as "1.2.3".
     *
     * @param version1 The first version string.
     * @param version2 The second version string.
     * @return VersionComparisonResult indicating the relationship of version2 relative to version1.
     */
    public static VersionComparisonResult compareVersions(String version1, String version2) {
        if (version1 == null || version2 == null || version1.isEmpty() || version2.isEmpty()) {
            Log.w(TAG, "Cannot compare versions: one or both are null/empty. V1: " + version1 + ", V2: " + version2);
            return VersionComparisonResult.ERROR_PARSING;
        }
        if (version1.equals(version2)) {
            return VersionComparisonResult.VERSIONS_ARE_SAME;
        }

        String[] parts1 = version1.split("\\.");
        String[] parts2 = version2.split("\\.");

        try {
            int length = Math.max(parts1.length, parts2.length);
            for (int i = 0; i < length; i++) {
                int num1 = 0;
                if (i < parts1.length && !parts1[i].isEmpty()) {
                    num1 = parseVersionPart(parts1[i]);
                }

                int num2 = 0;
                if (i < parts2.length && !parts2[i].isEmpty()) {
                    num2 = parseVersionPart(parts2[i]);
                }

                if (num2 > num1) {
                    return VersionComparisonResult.CURRENT_IS_NEWER; // version2 is newer
                }
                if (num2 < num1) {
                    return VersionComparisonResult.STORED_IS_NEWER;  // version2 is older (version1 is newer)
                }
                // If parts are equal, continue to the next part
            }
            // If all parsed numeric parts are identical
            return VersionComparisonResult.VERSIONS_ARE_SAME;
        } catch (NumberFormatException e) {
            Log.e(TAG, "Failed to parse version parts after sanitization. V1: " + version1 + ", V2: " + version2, e);
            return VersionComparisonResult.ERROR_PARSING;
        }
    }

    /**
     * Parses a single part of a version string (e.g., "2-beta" or "3").
     * Extracts the leading numeric characters before any non-digit (like '-').
     * @param part The version string part.
     * @return The parsed integer, or 0 if the part is empty or has no leading digits.
     * @throws NumberFormatException if the extracted numeric string is still invalid (should be rare).
     */
    private static int parseVersionPart(String part) throws NumberFormatException {
        if (part == null || part.isEmpty()) {
            return 0;
        }
        StringBuilder numericPart = new StringBuilder();
        for (char c : part.toCharArray()) {
            if (Character.isDigit(c)) {
                numericPart.append(c);
            } else {
                // Stop at the first non-digit character (e.g., '-', 'b', 'r')
                break;
            }
        }
        if (numericPart.length() == 0) {
            // This happens if the part starts with a non-digit, e.g., "-beta" (which is unusual for a part)
            // Or if the part was something like "alpha"
            return 0;
        }
        return Integer.parseInt(numericPart.toString());
    }

}
