package pl.qprogramming.themplay.views;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import lombok.val;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.settings.Property;

public class SettingsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);
        fadePreference();
        goBackHandling();

    }

    private void goBackHandling() {
        val goBack = findPreference(getString(R.string.goBack));
        if (goBack != null) {
            goBack.setOnPreferenceClickListener(preference -> {
                requireActivity().getSupportFragmentManager().popBackStack();
                return true;
            });
        }
    }

    private void fadePreference() {
        val fadePreference = (EditTextPreference) findPreference(Property.FADE_DURATION);
        if (fadePreference != null) {
            fadePreference.setOnBindEditTextListener(
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
                                editText.getRootView().findViewById(android.R.id.button1)
                                        .setEnabled(validationError == null);
                            }
                        });
                    });
            fadePreference.setSummaryProvider((Preference.SummaryProvider<EditTextPreference>) preference -> {
                String text = preference.getText();
                return text + " s";
            });
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
        //notifications
        val notifications = sp.getBoolean(Property.NOTIFICATIONS, false);
        if (notifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkAndRequestPermission();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void checkAndRequestPermission() {
        String permission = Manifest.permission.POST_NOTIFICATIONS;
        int permissionCheck = ContextCompat.checkSelfPermission(
                this.requireContext(), permission);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this.requireActivity(),
                    new String[]{permission}, permission.length());
        }
    }

}