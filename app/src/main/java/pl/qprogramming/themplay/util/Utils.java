package pl.qprogramming.themplay.util;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;

import java.util.Collection;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import lombok.val;
import pl.qprogramming.themplay.R;

public class Utils {

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
}
