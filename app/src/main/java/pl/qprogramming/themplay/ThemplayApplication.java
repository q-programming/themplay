package pl.qprogramming.themplay;

import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import lombok.val;
import pl.qprogramming.themplay.logger.Logger;

public class ThemplayApplication extends Application  {

    private static final String TAG = "ThemplayAppCrashHandler";
    public static final String CRASH_LOG_DIR_NAME = "logs";
    public static final String CRASH_LOG_FILE_PREFIX = "crash_";
    public static final String CRASH_LOG_FILE_SUFFIX = ".txt";


    @Override
    public void onCreate() {
        super.onCreate();
        Logger.initialize(this);
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
            // Get the stack trace.
            val sw = new StringWriter();
            val pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            String stackTraceString = sw.toString();
            saveCrashLogToFile(this, stackTraceString);
            Logger.e(TAG, "UNCAUGHT EXCEPTION on thread " + thread.getName() + ": ", e);
            // Add it to the clip board and close the app
            val clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            val clip = ClipData.newPlainText("Stack trace", sw.toString());
            clipboard.setPrimaryClip(clip);
            System.exit(1);
        });
    }
    private static void saveCrashLogToFile(Context context, String stackTrace) {
        try {
            File baseDir = context.getExternalFilesDir(null);
            if (baseDir == null) {
                Logger.w(TAG, "External storage not available, attempting to use internal storage for crash log.");
                baseDir = context.getFilesDir();
                if (baseDir == null) {
                    Logger.e(TAG, "Internal storage also not available. Cannot save crash log.");
                    return;
                }
            }
            File crashLogDir = new File(baseDir, CRASH_LOG_DIR_NAME);
            if (!crashLogDir.exists()) {
                if (!crashLogDir.mkdirs()) {
                    Logger.e(TAG, "Failed to create crash log directory: " + crashLogDir.getAbsolutePath());
                    return; // Can't proceed if directory creation fails
                }
            }

            String logFileName = CRASH_LOG_FILE_PREFIX + (System.currentTimeMillis() / 1000) + CRASH_LOG_FILE_SUFFIX;
            File logFile = new File(crashLogDir, logFileName);

            try (FileWriter fw = new FileWriter(logFile);
                 BufferedWriter bw = new BufferedWriter(fw)) {

                SimpleDateFormat sdfReadableDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.getDefault());
                bw.write("Crash Timestamp: " + sdfReadableDate.format(new Date()));
                bw.newLine();
                try {
                    bw.write("App Version: " + context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName +
                            " (" + context.getPackageManager().getPackageInfo(context.getPackageName(), 0).getLongVersionCode() + ")");
                    bw.newLine();
                } catch (Exception e) {
                    Logger.w(TAG, "Could not retrieve app version for crash log.", e);
                }
                bw.write("Device: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL +
                        " (Android " + android.os.Build.VERSION.RELEASE + ", API " + android.os.Build.VERSION.SDK_INT + ")");
                bw.newLine();
                bw.write("------------------------------------");
                bw.newLine();
                bw.write(stackTrace);
                bw.newLine();
                Logger.i(TAG, "Crash log successfully saved to: " + logFile.getAbsolutePath());
                Logger.shutdown();
            }
        } catch (IOException ioException) {
            Logger.e(TAG, "IOException while writing crash log to file.", ioException);
        } catch (Exception ex) {
            Logger.e(TAG, "Unexpected critical error during crash log saving itself.", ex);
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
    }


    @Override
    public void onTerminate() {
        super.onTerminate();
        Logger.shutdown();
    }
}
