package de.danoeh.antennapod.ui.screen.home.settingsdialog;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.ui.screen.home.HomeFragment;
import de.danoeh.antennapod.ui.screen.home.sections.AudiothekChartsSection;
import de.danoeh.antennapod.ui.screen.home.sections.AudiothekFeaturedSection;
import de.danoeh.antennapod.ui.screen.home.sections.AudiothekHotSection;
import de.danoeh.antennapod.ui.screen.home.sections.AudiothekLiveSection;
import de.danoeh.antennapod.ui.screen.home.sections.AudiothekSection;
import de.danoeh.antennapod.ui.screen.home.sections.AudiothekHeuteWichtigSection;
import de.danoeh.antennapod.ui.screen.home.sections.AudiothekStageSection;
import de.danoeh.antennapod.ui.screen.home.sections.DownloadsSection;
import de.danoeh.antennapod.ui.screen.home.sections.EpisodesSurpriseSection;
import de.danoeh.antennapod.ui.screen.home.sections.InboxSection;
import de.danoeh.antennapod.ui.screen.home.sections.QueueSection;
import de.danoeh.antennapod.ui.screen.home.sections.SubscriptionsSection;

public class HomePreferences {
    private static final String PREF_HIDDEN_SECTIONS = "PrefHomeSectionsString";
    private static final String PREF_SECTION_ORDER = "PrefHomeSectionOrder";
    private static HashMap<String, String> sectionTagToName;

    public static String getNameFromTag(Context context, String sectionTag) {
        if (sectionTagToName == null) {
            initializeMap(context);
        }

        return sectionTagToName.get(sectionTag);
    }

    private static void initializeMap(Context context) {
        Resources resources = context.getResources();
        String[] sectionLabels = resources.getStringArray(R.array.home_section_titles);
        String[] sectionTags = resources.getStringArray(R.array.home_section_tags);

        sectionTagToName = new HashMap<>(sectionTags.length);

        for (int i = 0; i < sectionLabels.length; i++) {
            String label = sectionLabels[i];
            String tag = sectionTags[i];

            sectionTagToName.put(tag, label);
        }
    }

    public static List<String> getHiddenSectionTags(Context context) {
        List<String> hiddenSectionTags = getListPreference(context, PREF_HIDDEN_SECTIONS);
        if (hiddenSectionTags.isEmpty()) {
            hiddenSectionTags.add(AudiothekStageSection.TAG);
            hiddenSectionTags.add(AudiothekLiveSection.TAG);
        }
        return hiddenSectionTags;
    }

    public static List<String> getSortedSectionTags(Context context) {
        List<String> storedSectionTagOrder = getListPreference(context, PREF_SECTION_ORDER);
        final List<String> sectionTagOrder = storedSectionTagOrder.isEmpty()
                ? new ArrayList<>(Arrays.asList(
                    AudiothekHotSection.TAG,
                    AudiothekChartsSection.TAG,
                    AudiothekHeuteWichtigSection.TAG,
                    AudiothekFeaturedSection.TAG,
                    AudiothekSection.TAG,
                    QueueSection.TAG,
                    InboxSection.TAG,
                    EpisodesSurpriseSection.TAG,
                    SubscriptionsSection.TAG,
                    DownloadsSection.TAG,
                    AudiothekLiveSection.TAG,
                    AudiothekStageSection.TAG))
                : storedSectionTagOrder;
        List<String> hiddenSectionTags = getHiddenSectionTags(context);
        String[] sectionTags = context.getResources().getStringArray(R.array.home_section_tags);
        Arrays.sort(sectionTags, (String a, String b) -> Integer.signum(
                indexOfOrMaxValue(sectionTagOrder, a) - indexOfOrMaxValue(sectionTagOrder, b)));

        List<String> finalSectionTags = new ArrayList<>();
        for (String sectionTag: sectionTags) {
            if (hiddenSectionTags.contains(sectionTag)) {
                continue;
            }

            finalSectionTags.add(sectionTag);
        }

        return finalSectionTags;
    }

    private static List<String> getListPreference(Context context, String preferenceKey) {
        SharedPreferences prefs = context.getSharedPreferences(HomeFragment.PREF_NAME, Context.MODE_PRIVATE);
        String hiddenSectionsString = prefs.getString(preferenceKey, "");
        return new ArrayList<>(Arrays.asList(TextUtils.split(hiddenSectionsString, ",")));
    }

    private static int indexOfOrMaxValue(List<String> haystack, String needle) {
        int index = haystack.indexOf(needle);
        return index == -1 ? Integer.MAX_VALUE : index;
    }

    public static void saveChanges(Context context, List<String> hiddenSections, List<String> sectionOrder) {
        SharedPreferences prefs = context.getSharedPreferences(HomeFragment.PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = prefs.edit();
        edit.putString(PREF_HIDDEN_SECTIONS, TextUtils.join(",", hiddenSections));
        edit.putString(PREF_SECTION_ORDER, TextUtils.join(",", sectionOrder));
        edit.apply();
    }
}
