package de.danoeh.antennapod.ui.screen.preferences;

import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Bundle;
import android.os.UserManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;

import com.bytehamster.lib.preferencesearch.SearchConfiguration;
import com.bytehamster.lib.preferencesearch.SearchPreference;

import de.danoeh.antennapod.BuildConfig;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.common.IntentUtils;
import de.danoeh.antennapod.ui.preferences.screen.AnimatedPreferenceFragment;
import de.danoeh.antennapod.ui.preferences.screen.ParentalControlDialog;
import de.danoeh.antennapod.ui.preferences.screen.about.AboutFragment;
import de.danoeh.antennapod.ui.preferences.screen.bugreport.BugReportFragment;


public class MainPreferencesFragment extends AnimatedPreferenceFragment {

    private static final String PREF_SCREEN_USER_INTERFACE = "prefScreenInterface";
    private static final String PREF_SCREEN_PLAYBACK = "prefScreenPlayback";
    private static final String PREF_SCREEN_DOWNLOADS = "prefScreenDownloads";
    private static final String PREF_SCREEN_IMPORT_EXPORT = "prefScreenImportExport";
    private static final String PREF_SCREEN_SYNCHRONIZATION = "prefScreenSynchronization";
    private static final String PREF_SCREEN_SEARCH = "prefScreenSearch";
    private static final String PREF_DOCUMENTATION = "prefDocumentation";
    private static final String PREF_VIEW_FORUM = "prefViewForum";
    private static final String PREF_SEND_BUG_REPORT = "prefSendBugReport";
    private static final String PREF_CATEGORY_PROJECT = "project";
    private static final String PREF_ABOUT = "prefAbout";
    private static final String PREF_NOTIFICATION = "notifications";
    private static final String PREF_CONTRIBUTE = "prefContribute";
    private static final String PREF_SCREEN_PARENTAL_CONTROL = "prefScreenParentalControl";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.preferences);
        setupMainScreen();
        setupSearch();
        setParentalControlsVisibility();
    }

    @Override
    public void onStart() {
        super.onStart();
        ((PreferenceActivity) getActivity()).getSupportActionBar().setTitle(R.string.settings_label);
    }

    private void setupMainScreen() {
        findPreference(PREF_SCREEN_USER_INTERFACE).setOnPreferenceClickListener(preference -> {
            ((PreferenceActivity) getActivity()).openScreen(R.xml.preferences_user_interface);
            return true;
        });
        findPreference(PREF_SCREEN_PLAYBACK).setOnPreferenceClickListener(preference -> {
            ((PreferenceActivity) getActivity()).openScreen(R.xml.preferences_playback);
            return true;
        });
        findPreference(PREF_SCREEN_DOWNLOADS).setOnPreferenceClickListener(preference -> {
            ((PreferenceActivity) getActivity()).openScreen(R.xml.preferences_downloads);
            return true;
        });
        findPreference(PREF_SCREEN_SYNCHRONIZATION).setOnPreferenceClickListener(preference -> {
            ((PreferenceActivity) getActivity()).openScreen(R.xml.preferences_synchronization);
            return true;
        });
        findPreference(PREF_SCREEN_IMPORT_EXPORT).setOnPreferenceClickListener(preference -> {
            ((PreferenceActivity) getActivity()).openScreen(R.xml.preferences_import_export);
            return true;
        });
        findPreference(PREF_SCREEN_SEARCH).setOnPreferenceClickListener(preference -> {
            ((PreferenceActivity) getActivity()).openScreen(R.xml.preferences_search);
            return true;
        });
        findPreference(PREF_NOTIFICATION).setOnPreferenceClickListener(preference -> {
            ((PreferenceActivity) getActivity()).openScreen(R.xml.preferences_notifications);
            return true;
        });
        findPreference(PREF_ABOUT).setOnPreferenceClickListener(
                preference -> {
                    getParentFragmentManager().beginTransaction()
                            .replace(R.id.settingsContainer, new AboutFragment())
                            .addToBackStack(getString(R.string.about_pref)).commit();
                    return true;
                }
        );
        findPreference(PREF_DOCUMENTATION).setOnPreferenceClickListener(preference -> {
            IntentUtils.openInBrowser(getContext(), "https://antennapod.org/documentation/");
            return true;
        });
        findPreference(PREF_VIEW_FORUM).setOnPreferenceClickListener(preference -> {
            IntentUtils.openInBrowser(getContext(), "https://forum.antennapod.org/");
            return true;
        });
        findPreference(PREF_CONTRIBUTE).setOnPreferenceClickListener(preference -> {
            IntentUtils.openInBrowser(getContext(), "https://antennapod.org/contribute/");
            return true;
        });
        findPreference(PREF_SEND_BUG_REPORT).setOnPreferenceClickListener(preference -> {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.settingsContainer, new BugReportFragment())
                    .addToBackStack(getString(R.string.report_bug_title)).commit();
            return true;
        });
        findPreference(PREF_SCREEN_PARENTAL_CONTROL).setOnPreferenceClickListener(preference -> {
            if (UserPreferences.isParentalControlPasswordSet()) {
                ParentalControlDialog.show(requireContext(), () ->
                        ((PreferenceActivity) getActivity()).openScreen(R.xml.preferences_parental_control));
            } else {
                ((PreferenceActivity) getActivity()).openScreen(R.xml.preferences_parental_control);
            }
            return true;
        });
    }

    // show the 'parental controls' preference if we're on a 'child' device (family link) or if it's a debug build
    private void setParentalControlsVisibility() {
        UserManager um = requireContext().getSystemService(UserManager.class);
        // Family Link child devices have DISALLOW_FACTORY_RESET set (among other restrictions).
        // AccountManager-based checks don't work: supervised users have no visible Google accounts.
        boolean isChildDevice = um.hasUserRestriction(UserManager.DISALLOW_FACTORY_RESET);
        findPreference(PREF_SCREEN_PARENTAL_CONTROL).setVisible(
                isChildDevice || BuildConfig.DEBUG || UserPreferences.isParentalControlPasswordSet());
    }

    private void setupSearch() {
        SearchPreference searchPreference = findPreference("searchPreference");
        SearchConfiguration config = searchPreference.getSearchConfiguration();
        config.setActivity((AppCompatActivity) getActivity());
        config.setFragmentContainerViewId(R.id.settingsContainer);
        config.setBreadcrumbsEnabled(true);

        config.index(R.xml.preferences_user_interface)
                .addBreadcrumb(PreferenceActivity.getTitleOfPage(R.xml.preferences_user_interface));
        config.index(R.xml.preferences_playback)
                .addBreadcrumb(PreferenceActivity.getTitleOfPage(R.xml.preferences_playback));
        config.index(R.xml.preferences_downloads)
                .addBreadcrumb(PreferenceActivity.getTitleOfPage(R.xml.preferences_downloads));
        config.index(R.xml.preferences_import_export)
                .addBreadcrumb(PreferenceActivity.getTitleOfPage(R.xml.preferences_import_export));
        config.index(R.xml.preferences_autodownload)
                .addBreadcrumb(PreferenceActivity.getTitleOfPage(R.xml.preferences_downloads))
                .addBreadcrumb(R.string.automation)
                .addBreadcrumb(PreferenceActivity.getTitleOfPage(R.xml.preferences_autodownload));
        config.index(R.xml.preferences_synchronization)
                .addBreadcrumb(PreferenceActivity.getTitleOfPage(R.xml.preferences_synchronization));
        config.index(R.xml.preferences_notifications)
                .addBreadcrumb(PreferenceActivity.getTitleOfPage(R.xml.preferences_notifications));
        config.index(R.xml.preferences_search)
                .addBreadcrumb(PreferenceActivity.getTitleOfPage(R.xml.preferences_search));
        config.index(R.xml.feed_settings)
                .addBreadcrumb(PreferenceActivity.getTitleOfPage(R.xml.feed_settings));
        config.index(R.xml.preferences_swipe)
                .addBreadcrumb(PreferenceActivity.getTitleOfPage(R.xml.preferences_user_interface))
                .addBreadcrumb(PreferenceActivity.getTitleOfPage(R.xml.preferences_swipe));
    }
}
