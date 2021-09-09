package pl.qprogramming.themplay.views;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import lombok.val;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.settings.Property;

public class SettingsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);
        findPreference(getString(R.string.goBack))
                .setOnPreferenceClickListener(preference -> {
                    getActivity().getSupportFragmentManager().popBackStack();
                    return true;
                });
    }

    @Override
    public void onStop() {
        super.onStop();
        val sp = PreferenceManager.getDefaultSharedPreferences(this.getContext());
        val darkMode = sp.getBoolean(Property.DARK_MODE, false);
        AppCompatDelegate.setDefaultNightMode(darkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }
}