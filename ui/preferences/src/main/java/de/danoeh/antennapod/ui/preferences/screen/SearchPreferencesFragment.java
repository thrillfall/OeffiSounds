package de.danoeh.antennapod.ui.preferences.screen;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreferenceCompat;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.preferences.R;

import java.util.HashSet;
import java.util.Set;

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
        PreferenceCategory category = findPreference("searchProvidersCategory");
        if (category == null) {
            return;
        }

        String[] names = getResources().getStringArray(R.array.search_provider_names);
        String[] values = getResources().getStringArray(R.array.search_provider_values);
        Set<String> enabled = UserPreferences.getEnabledSearchProviders();

        for (int i = 0; i < names.length; i++) {
            SwitchPreferenceCompat pref = new SwitchPreferenceCompat(requireContext());
            pref.setTitle(names[i]);
            pref.setKey("searchProvider_" + values[i]);
            pref.setChecked(enabled.contains(values[i]));
            final String value = values[i];
            pref.setOnPreferenceChangeListener((preference, newValue) -> {
                Set<String> current = new HashSet<>(UserPreferences.getEnabledSearchProviders());
                if ((boolean) newValue) {
                    current.add(value);
                } else {
                    current.remove(value);
                }
                UserPreferences.setEnabledSearchProviders(current);
                return true;
            });
            category.addPreference(pref);
        }
    }
}
