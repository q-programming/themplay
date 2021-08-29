package pl.qprogramming.themplay.playlist.util;

import android.util.TypedValue;
import android.view.View;

import java.util.Collection;

import lombok.val;

public class Utils {
    public static int getThemeColor(View view, int color) {
        val typedValue = new TypedValue();
        view.getContext().getTheme().resolveAttribute(color, typedValue, true);
        return typedValue.data;
    }

    public static boolean isEmpty(Collection<?> coll) {
        return (coll == null || coll.isEmpty());
    }

}
