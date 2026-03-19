package de.danoeh.antennapod.ui.preferences.screen;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.MultiSelectListPreference;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.preferences.R;

public class SearchPreferencesFragment extends AnimatedPreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.preferences_search);
        setupSearchProviders();
    }

    @Override
    public void onStart() {
        super.onStart();
        ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.search_pref);
    }

    private void setupSearchProviders() {
        MultiSelectListPreference pref = findPreference(UserPreferences.PREF_ENABLED_SEARCH_PROVIDERS);
        if (pref != null) {
            pref.setValues(UserPreferences.getEnabledSearchProviders());
            pref.setOnPreferenceChangeListener((preference, newValue) -> {
                //noinspection unchecked
                UserPreferences.setEnabledSearchProviders((java.util.Set<String>) newValue);
                return true;
            });
        }
    }
}
