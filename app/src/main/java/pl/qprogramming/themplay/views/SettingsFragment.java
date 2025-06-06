package pl.qprogramming.themplay.views;

import static pl.qprogramming.themplay.settings.Property.DEBUG_SECTION_UNLOCKED;
import static pl.qprogramming.themplay.settings.Property.DEBUG_TAPS_COUNT;
import static pl.qprogramming.themplay.settings.Property.ENABLE_DEBUG_LOGS;
import static pl.qprogramming.themplay.settings.Property.TOGGLE_DEBUG_SECTION;

import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import java.text.MessageFormat;

import lombok.val;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.logger.Logger;
import pl.qprogramming.themplay.settings.Property;

public class SettingsFragment extends PreferenceFragmentCompat {
    public static final String TAG = SettingsFragment.class.getSimpleName();
    public static final String KEY_APP_VERSION = "app_version";
    public static final String KEY_DEBUG_CATEGORY = "debug_category";

    private static final int TAPS_TO_ENABLE_DEBUG = 5;
    private PreferenceCategory debugCategory;
    private Preference appVersionPreference;
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
        if (debugCategory == null || appVersionPreference == null || enableDebugLogsSwitch == null || toggleDebugSectionSwitch == null) {
            Logger.e(TAG, "One or more critical preferences not found. Check XML keys.");
            return;
        }

        fadePreference();
        goBackHandling();
        setupAppVersionInteraction();
        setupDebugSectionToggle();
        updateDebugSectionVisibility();
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
            val version = pm.getPackageInfo(packageName, 0).versionName;
            appVersionPreference.setSummary(version);
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