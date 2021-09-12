package pl.qprogramming.themplay.util;

import android.util.TypedValue;
import android.view.View;

import java.util.Collection;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import lombok.val;
import pl.qprogramming.themplay.R;

public class Utils {

    private Utils(){
    }

    public static int getThemeColor(View view, int color) {
        val typedValue = new TypedValue();
        view.getContext().getTheme().resolveAttribute(color, typedValue, true);
        return typedValue.data;
    }

    public static boolean isEmpty(Collection<?> coll) {
        return (coll == null || coll.isEmpty());
    }

    public static void navigateToFragment(FragmentManager fm, Fragment fragment, String name){
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
