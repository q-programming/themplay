package pl.qprogramming.themplay.views;

import static pl.qprogramming.themplay.settings.Property.DEBUG_SECTION_UNLOCKED;
import static pl.qprogramming.themplay.settings.Property.DEBUG_TAPS_COUNT;
import static pl.qprogramming.themplay.settings.Property.ENABLE_DEBUG_LOGS;
import static pl.qprogramming.themplay.settings.Property.LOGS_DIRECTORY_NAME;
import static pl.qprogramming.themplay.settings.Property.TOGGLE_DEBUG_SECTION;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import lombok.val;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.logger.Logger;
import pl.qprogramming.themplay.settings.Property;

public class SettingsFragment extends PreferenceFragmentCompat {
    public static final String TAG = SettingsFragment.class.getSimpleName();
    public static final String KEY_APP_VERSION = "app_version";
    public static final String KEY_DEBUG_CATEGORY = "debug_category";
    public static final String KEY_LOG_DOWNLOAD = "download_logs";

    private static final int TAPS_TO_ENABLE_DEBUG = 5;
    private PreferenceCategory debugCategory;
    private Preference appVersionPreference;
    private Preference downloadLogs;
    private SwitchPreferenceCompat enableDebugLogsSwitch;
    private SwitchPreferenceCompat toggleDebugSectionSwitch;
    private SharedPreferences sharedPreferences;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        debugCategory = findPreference(KEY_DEBUG_CATEGORY);
        appVersionPreference = findPreference(KEY_APP_VERSION);
        enableDebugLogsSwitch = findPreference(ENABLE_DEBUG_LOGS);
        toggleDebugSectionSwitch = findPreference(TOGGLE_DEBUG_SECTION);
        downloadLogs = findPreference(KEY_LOG_DOWNLOAD);
        if (debugCategory == null || appVersionPreference == null || enableDebugLogsSwitch == null || toggleDebugSectionSwitch == null) {
            Logger.e(TAG, "One or more critical preferences not found. Check XML keys.");
            return;
        }

        fadePreference();
        goBackHandling();
        setupAppVersionInteraction();
        setupDebugSectionToggle();
        updateDebugSectionVisibility();
        setupDownloadLogsInteraction();
    }

    private void goBackHandling() {
        final Preference goBack = findPreference(getString(R.string.goBack)); // Assuming R.string.goBack is a key
        if (goBack != null) {
            goBack.setOnPreferenceClickListener(preference -> {
                if (isAdded() && getActivity() != null) {
                    getActivity().getSupportFragmentManager().popBackStack();
                }
                return true;
            });
        }
    }

    private void fadePreference() {
        final EditTextPreference fadePref = findPreference(Property.FADE_DURATION);
        if (fadePref != null) {
            fadePref.setOnBindEditTextListener(
                    editText -> {
                        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                        editText.addTextChangedListener(new TextWatcher() {
                            @Override
                            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                            }

                            @Override
                            public void onTextChanged(CharSequence s, int start, int before, int count) {
                            }

                            @Override
                            public void afterTextChanged(Editable editable) {
                                String validationError = null;
                                if (editable.length() == 0) {
                                    validationError = getString(R.string.settings_fade_error);
                                }
                                editText.setError(validationError);
                                if (editText.getRootView().findViewById(android.R.id.button1) != null) {
                                    editText.getRootView().findViewById(android.R.id.button1)
                                            .setEnabled(validationError == null);
                                }
                            }
                        });
                    });
            fadePref.setSummaryProvider((Preference.SummaryProvider<EditTextPreference>) preference -> {
                val text = preference.getText();
                return (text == null || text.isEmpty()) ? "" : text + " s";
            });
        }
    }

    private void setupAppVersionInteraction() {
        try {
            val pm = requireActivity().getPackageManager();
            val packageName = requireActivity().getPackageName();
            String versionSummary;
            String buildTimeStr = "";
            val version = pm.getPackageInfo(packageName, 0).versionName;
            long buildTimestamp = pl.qprogramming.themplay.BuildConfig.BUILD_TIMESTAMP;if (buildTimestamp > 0) {
                // Format it into a human-readable string
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                buildTimeStr = sdf.format(new Date(buildTimestamp));
            }
            if (buildTimeStr.isEmpty()) {
                versionSummary = version;
            } else {
                versionSummary = version + " \n(" + buildTimeStr + ")";
            }
            appVersionPreference.setSummary(versionSummary);
        } catch (PackageManager.NameNotFoundException e) {
            Logger.e(TAG, "Error fetching app version", e);
            appVersionPreference.setSummary("N/A");
        }
        if (!sharedPreferences.getBoolean(DEBUG_SECTION_UNLOCKED, false)) {
            attachTapListenerToAppVersion();
        } else {
            appVersionPreference.setSummary(appVersionPreference.getSummary());
            toggleDebugSectionSwitch.setChecked(true);
        }
    }

    private void attachTapListenerToAppVersion() {
        appVersionPreference.setOnPreferenceClickListener(preference -> {
            if (!isAdded()) {
                return true;
            }
            int currentTaps = sharedPreferences.getInt(DEBUG_TAPS_COUNT, 0);
            currentTaps++;
            if (currentTaps >= TAPS_TO_ENABLE_DEBUG) {
                sharedPreferences.edit()
                        .putBoolean(DEBUG_SECTION_UNLOCKED, true)
                        .putInt(DEBUG_TAPS_COUNT, 0)
                        .apply();

                toggleDebugSectionSwitch.setChecked(true);
                debugCategory.setVisible(true);
                val unlockedMsg = getString(R.string.settings_enable_developer_mode_unlocked);
                Toast.makeText(getContext(), unlockedMsg, Toast.LENGTH_SHORT).show();
                String currentVersionSummary = "";
                try {
                    val pm = requireActivity().getPackageManager();
                    val packageName = requireActivity().getPackageName();
                    currentVersionSummary = pm.getPackageInfo(packageName, 0).versionName;
                } catch (PackageManager.NameNotFoundException e) {
                    currentVersionSummary = "N/A";
                }
                appVersionPreference.setSummary(currentVersionSummary);
                appVersionPreference.setOnPreferenceClickListener(null);

            } else {
                sharedPreferences.edit().putInt(DEBUG_TAPS_COUNT, currentTaps).apply();
                int tapsRemaining = TAPS_TO_ENABLE_DEBUG - currentTaps;
                if (tapsRemaining <= 3) {
                    val msg = MessageFormat.format(getString(R.string.settings_enable_developer_mode_clicks), tapsRemaining);
                    Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
                }
            }
            return true;
        });
    }

    private void setupDebugSectionToggle() {
        toggleDebugSectionSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean isEnabled = (Boolean) newValue;
            debugCategory.setVisible(isEnabled);

            if (!isEnabled) {
                enableDebugLogsSwitch.setChecked(false);
                sharedPreferences.edit()
                        .putBoolean(DEBUG_SECTION_UNLOCKED, false)
                        .putInt(DEBUG_TAPS_COUNT, 0)
                        .apply();
                attachTapListenerToAppVersion();
                try {
                    val pm = requireActivity().getPackageManager();
                    val packageName = requireActivity().getPackageName();
                    appVersionPreference.setSummary(pm.getPackageInfo(packageName, 0).versionName);
                } catch (PackageManager.NameNotFoundException e) {
                    appVersionPreference.setSummary("N/A");
                }
                val msg = getString(R.string.settings_developer_mode_hidden);
                Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
            } else {
                sharedPreferences.edit().putBoolean(DEBUG_SECTION_UNLOCKED, true).apply();
            }
            return true;
        });
    }


    private void updateDebugSectionVisibility() {
        boolean sectionShouldBeVisible = toggleDebugSectionSwitch.isChecked();
        debugCategory.setVisible(sectionShouldBeVisible);
        if (!sharedPreferences.getBoolean(DEBUG_SECTION_UNLOCKED, false)) {
            sharedPreferences.edit().putInt(DEBUG_TAPS_COUNT, 0).apply();
        }
    }

    private void setupDownloadLogsInteraction() {
        if (downloadLogs != null) {
            downloadLogs.setOnPreferenceClickListener(preference -> {
                Logger.d(TAG, "Download logs clicked. Launching file save intent.");
                Intent saveIntent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                saveIntent.addCategory(Intent.CATEGORY_OPENABLE);
                saveIntent.setType("application/zip");
                saveIntent.putExtra(Intent.EXTRA_TITLE, "app_logs_" + System.currentTimeMillis() + ".zip"); // More unique name
                fileSaveActivityResultLauncher.launch(saveIntent);
                return true;
            });
        }
    }

    ActivityResultLauncher<Intent> fileSaveActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    val uri = result.getData().getData();
                    val context = requireContext();
                    val logsDir = context.getExternalFilesDir(LOGS_DIRECTORY_NAME);
                    if (logsDir == null || !logsDir.exists() || !logsDir.isDirectory()) {
                        Logger.e(TAG, "Unable to access to logs directory");
                        Toast.makeText(context, "External files directory is null.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    val logFiles = logsDir.listFiles();
                    if (logFiles == null || logFiles.length == 0) {
                        Logger.i(TAG, "No log files found in " + logsDir.getAbsolutePath());
                        Toast.makeText(context, "No logs files", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        // Important: Use ContentResolver to open OutputStream for the SAF URI
                        val resolver = context.getContentResolver();
                        try (val outputStream = resolver.openOutputStream(uri);
                             val zipOutputStream = new ZipOutputStream(outputStream)) {
                            Logger.d(TAG, "Starting to zip log files into: " + uri);
                            byte[] buffer = new byte[8192];
                            int filesZippedCount = 0;
                            for (val logFile : logFiles) {
                                var fileZippedSuccessfully = false;
                                if (logFile.isFile() && logFile.length() > 0) { // Check if it's a file and not empty
                                    Logger.d(TAG, "Adding file to zip: " + logFile.getName());
                                    try (val fis = new FileInputStream(logFile);
                                         val bis = new BufferedInputStream(fis)) {
                                        val zipEntry = new ZipEntry(logFile.getName());
                                        zipOutputStream.putNextEntry(zipEntry);
                                        int bytesRead;
                                        while ((bytesRead = bis.read(buffer)) != -1) {
                                            zipOutputStream.write(buffer, 0, bytesRead);
                                        }
                                        zipOutputStream.closeEntry();
                                        fileZippedSuccessfully = true;
                                        filesZippedCount++;
                                    } catch (IOException e) {
                                        Logger.e(TAG, "IOException while adding file to zip: " + logFile.getName(), e);
                                    }
                                }
                                if (fileZippedSuccessfully) {
                                    if (logFile.delete()) {
                                        Logger.d(TAG, "Successfully deleted original log file: " + logFile.getName());
                                    } else {
                                        Logger.w(TAG, "Failed to delete original log file: " + logFile.getName());
                                    }
                                }
                            }
                            zipOutputStream.finish();
                            Logger.d(TAG, "Successfully zipped " + filesZippedCount + " log files.");
                            if (filesZippedCount > 0) {
                                Toast.makeText(context, "Files successfully zipped", Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(context, "No logs to be ziped", Toast.LENGTH_SHORT).show();
                            }
                        }
                    } catch (FileNotFoundException e) {
                        Logger.e(TAG, "FileNotFoundException: Could not open URI for writing: " + uri, e);
                    } catch (IOException e) {
                        Logger.e(TAG, "IOException during zipping process: ", e);
                    } catch (SecurityException e) {
                        Logger.e(TAG, "SecurityException: Permission denied for URI " + uri, e);
                    }
                }
            });


    @Override
    public void onStop() {
        super.onStop();
        val sp = PreferenceManager.getDefaultSharedPreferences(requireContext());
        //dark mode
        val darkMode = sp.getBoolean(Property.DARK_MODE, false);
        AppCompatDelegate.setDefaultNightMode(darkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        //keep screen on
        val keepScreenOn = sp.getBoolean(Property.KEEP_SCREEN_ON, true);
        requireActivity().findViewById(R.id.activity_fragment_layout).setKeepScreenOn(keepScreenOn);
    }
}