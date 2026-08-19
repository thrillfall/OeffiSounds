package de.danoeh.antennapod;

import android.content.Context;
import android.content.SharedPreferences;

import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.system.CrashReportWriter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PreferenceUpgrader {
    private static final String PREF_CONFIGURED_VERSION = "version_code";
    private static final String PREF_NAME = "app_version";

    public static void checkUpgrades(Context context) {
        SharedPreferences upgraderPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int oldVersion = upgraderPrefs.getInt(PREF_CONFIGURED_VERSION, -1);
        int newVersion = BuildConfig.VERSION_CODE;

        if (oldVersion != newVersion) {
            CrashReportWriter.getFile().delete();

            upgrade(oldVersion, newVersion, context);
            upgraderPrefs.edit().putInt(PREF_CONFIGURED_VERSION, newVersion).apply();
        }
    }

    private static void upgrade(int oldVersion, int newVersion, Context context) {
        if (oldVersion == -1) {
            //New installation
            return;
        }
        if (oldVersion < 29) {
            // Previous versions had a bug where upstream migrations reset streaming to false
            // on every update. Fix by enabling streaming for all affected users.
            UserPreferences.setStreamOverDownload(true);
        }
        if (oldVersion < 38) {
            renameSearchProviders();
        }
    }

    /**
     * Search providers are stored by class name. Some of those classes were renamed
     * (BBCSounds... -> BbcSounds...), so the stored names have to follow.
     */
    private static void renameSearchProviders() {
        Map<String, String> renamed = new HashMap<>();
        renamed.put("BBCSoundsPodcastSearcher", "BbcSoundsPodcastSearcher");
        renamed.put("SRFPlayPodcastSearcher", "SrfPlayPodcastSearcher");
        renamed.put("ORFSoundPodcastSearcher", "OrfSoundPodcastSearcher");
        renamed.put("DLFPodcastSearcher", "DlfPodcastSearcher");
        renamed.put("RTVEPodcastSearcher", "RtvePodcastSearcher");

        Set<String> enabled = new HashSet<>(UserPreferences.getEnabledSearchProviders());
        Set<String> updated = new HashSet<>();
        for (String provider : enabled) {
            updated.add(renamed.containsKey(provider) ? renamed.get(provider) : provider);
        }
        if (!updated.equals(enabled)) {
            UserPreferences.setEnabledSearchProviders(updated);
        }
    }
}
