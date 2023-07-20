package pl.qprogramming.themplay;

import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import java.io.PrintWriter;
import java.io.StringWriter;

import lombok.val;

public class ThemplayApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
            // Get the stack trace.
            val sw = new StringWriter();
            val pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            // Add it to the clip board and close the app
            val clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            val clip = ClipData.newPlainText("Stack trace", sw.toString());
            clipboard.setPrimaryClip(clip);
            System.exit(1);
        });
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
    }


}
