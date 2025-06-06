package pl.qprogramming.themplay.logger;

import static pl.qprogramming.themplay.settings.Property.ENABLE_DEBUG_LOGS;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pl.qprogramming.themplay.BuildConfig;

public class Logger {

    private static final String TAG = "Logger";
    private static final String LOG_FILE_NAME = "app_debug_log.txt";
    private static final int MAX_LOG_FILE_SIZE_BYTES = 5 * 1024 * 1024;
    private static final SimpleDateFormat FILE_LOG_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    private static boolean isDebugModeEnabled = false;
    private static File logFile;
    private static ExecutorService logWriterExecutor;

    private static Context applicationContext = null; // Static context

    public static synchronized void initialize(Context context) {
        if(context == null){
            Log.e(TAG, "Logger .initialize called with a null context. Logger cannot be initialized.");
            return;
        }
        if (applicationContext == null) {
            applicationContext = context.getApplicationContext();
        }
        if (logWriterExecutor == null) {
            logWriterExecutor = Executors.newSingleThreadExecutor();
        }
        Context currentContext = (applicationContext != null) ? applicationContext : context.getApplicationContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(currentContext);
        isDebugModeEnabled = prefs.getBoolean(ENABLE_DEBUG_LOGS, false);
        prefs.registerOnSharedPreferenceChangeListener(mPrefListener);

        if (isDebugModeEnabled) {
            setupLogFile(currentContext);
            d(TAG, "Debug mode initialized and enabled. Logging to file.");
        } else {
            d(TAG, "Debug mode initialized and disabled.");
        }
    }

    private static final SharedPreferences.OnSharedPreferenceChangeListener mPrefListener =
            (sharedPreferences, key) -> {
                if (ENABLE_DEBUG_LOGS.equals(key)) {
                    boolean previousState = isDebugModeEnabled;
                    isDebugModeEnabled = sharedPreferences.getBoolean(ENABLE_DEBUG_LOGS, false);
                    if (isDebugModeEnabled && !previousState) {
                        Log.i(TAG, "Debug mode has been ENABLED by user.");
                        if (applicationContext != null) { // Check if context is available
                            setupLogFile(applicationContext); // Re-initialize file logging
                            Log.i(TAG, "Log file setup re-initialized due to preference change.");
                        } else {
                            Log.w(TAG, "Application context not available to re-initialize log file.");
                        }
                    } else if (!isDebugModeEnabled && previousState) {
                        Log.i(TAG, "Debug mode has been DISABLED by user.");
                    }
                }
            };


    private static void setupLogFile(Context context) {
        if (!isExternalStorageWritable()) {
            Log.e(TAG, "External storage not writable. Cannot write log file.");
            logFile = null;
            return;
        }
        File logDir = new File(context.getExternalFilesDir(null), "logs");
        if (!logDir.exists()) {
            if (!logDir.mkdirs()) {
                Log.e(TAG, "Failed to create log directory.");
                logFile = null;
                return;
            }
        }
        logFile = new File(logDir, LOG_FILE_NAME);
        // Optional: Implement log rotation or size check here
        checkAndRotateLogFile();
        Log.i(TAG, "Log file path: " + logFile.getAbsolutePath());
    }

    private static void checkAndRotateLogFile() {
        if (logFile != null && logFile.exists() && logFile.length() > MAX_LOG_FILE_SIZE_BYTES) {
            File oldLogFile = new File(logFile.getParentFile(), LOG_FILE_NAME + ".old");
            if (oldLogFile.exists()) {
                oldLogFile.delete();
            }
            logFile.renameTo(oldLogFile);
            logFile = new File(logFile.getParentFile(), LOG_FILE_NAME); // Create new empty log file
            Log.i(TAG, "Log file rotated. Old log: " + oldLogFile.getName());
        }
    }


    public static void v(String tag, String message) {
        Log.v(tag, message);
        writeToFileIfEnabled("V", tag, message, null);
    }

    public static void v(String tag, String message, Throwable tr) {
        Log.v(tag, message, tr);
        writeToFileIfEnabled("V", tag, message, tr);
    }

    public static void d(String tag, String message) {
        // BuildConfig.DEBUG is true for debug builds, false for release builds.
        // You might want to always show .d logs in Logcat if user enabled debug mode,
        // regardless of BuildConfig.DEBUG.
        if (isDebugModeEnabled || BuildConfig.DEBUG) {
            Log.d(tag, message);
        }
        writeToFileIfEnabled("D", tag, message, null);
    }

    public static void d(String tag, String message, Throwable tr) {
        if (isDebugModeEnabled || BuildConfig.DEBUG) {
            Log.d(tag, message, tr);
        }
        writeToFileIfEnabled("D", tag, message, tr);
    }

    public static void i(String tag, String message) {
        Log.i(tag, message);
        writeToFileIfEnabled("I", tag, message, null);
    }

    public static void i(String tag, String message, Throwable tr) {
        Log.i(tag, message, tr);
        writeToFileIfEnabled("I", tag, message, tr);
    }

    public static void w(String tag, String message) {
        Log.w(tag, message);
        writeToFileIfEnabled("W", tag, message, null);
    }

    public static void w(String tag, String message, Throwable tr) {
        Log.w(tag, message, tr);
        writeToFileIfEnabled("W", tag, message, tr);
    }

    public static void e(String tag, String message) {
        Log.e(tag, message);
        writeToFileIfEnabled("E", tag, message, null);
    }

    public static void e(String tag, String message, Throwable tr) {
        Log.e(tag, message, tr);
        writeToFileIfEnabled("E", tag, message, tr);
    }

    // --- File Writing Logic ---

    private static void writeToFileIfEnabled(final String level, final String tag, final String message, final Throwable tr) {
        if (!isDebugModeEnabled || logFile == null || logWriterExecutor == null || logWriterExecutor.isShutdown()) {
            return;
        }

        logWriterExecutor.execute(() -> {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
                writer.append(FILE_LOG_FORMAT.format(new Date()));
                writer.append(" ");
                writer.append(level);
                writer.append("/");
                writer.append(tag);
                writer.append(": ");
                writer.append(message);
                writer.newLine();
                if (tr != null) {
                    writer.append(Log.getStackTraceString(tr)); // Get full stack trace
                    writer.newLine();
                }
            } catch (IOException e) {
                Log.e(Logger.TAG, "Error writing to log file", e);
            }
        });
    }

    /* Checks if external storage is available for read and write */
    private static boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    public static synchronized void shutdown() {
        if (logWriterExecutor != null) {
            logWriterExecutor.shutdown();
            try {
                if (!logWriterExecutor.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS)) {
                    logWriterExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                logWriterExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            logWriterExecutor = null;
        }

    }
}