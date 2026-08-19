package de.danoeh.antennapod.ui.screen.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.databinding.HomeFragmentBinding;
import de.danoeh.antennapod.event.FeedListUpdateEvent;
import de.danoeh.antennapod.event.FeedUpdateRunningEvent;
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.echo.EchoConfig;
import de.danoeh.antennapod.ui.screen.SearchFragment;
import de.danoeh.antennapod.ui.screen.home.sections.AudiothekChartsSection;
import de.danoeh.antennapod.ui.screen.home.sections.BbcAudioRecommendedTodaySection;
import de.danoeh.antennapod.ui.screen.home.sections.BbcSoundsFeaturedSection;
import de.danoeh.antennapod.ui.screen.home.sections.AudiothekFeaturedSection;
import de.danoeh.antennapod.ui.screen.home.sections.AudiothekHotSection;
import de.danoeh.antennapod.ui.screen.home.sections.AudiothekLiveSection;
import de.danoeh.antennapod.ui.screen.home.sections.AudiothekSection;
import de.danoeh.antennapod.ui.screen.home.sections.AudiothekHeuteWichtigSection;
import de.danoeh.antennapod.ui.screen.home.sections.AudiothekStageSection;
import de.danoeh.antennapod.ui.screen.home.sections.DownloadsSection;
import de.danoeh.antennapod.ui.screen.home.sections.EchoSection;
import de.danoeh.antennapod.ui.screen.home.sections.EpisodesSurpriseSection;
import de.danoeh.antennapod.ui.screen.home.sections.InboxSection;
import de.danoeh.antennapod.ui.screen.home.sections.QueueSection;
import de.danoeh.antennapod.ui.screen.home.sections.SrfPlayPopularSection;
import de.danoeh.antennapod.ui.screen.home.sections.OrfSoundPodcastsSection;
import de.danoeh.antennapod.ui.screen.home.sections.DlfPodcastsSection;
import de.danoeh.antennapod.ui.screen.home.sections.RtvePodcastsSection;
import de.danoeh.antennapod.ui.screen.home.sections.SubscriptionsSection;
import de.danoeh.antennapod.ui.screen.home.settingsdialog.HomePreferences;
import de.danoeh.antennapod.ui.screen.home.settingsdialog.HomeSectionsSettingsDialog;
import de.danoeh.antennapod.ui.common.LiftOnScrollListener;
import io.reactivex.rxjava3.disposables.Disposable;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;

/**
 * Shows unread or recently published episodes
 */
public class HomeFragment extends Fragment implements Toolbar.OnMenuItemClickListener {

    public static final String TAG = "HomeFragment";
    public static final String PREF_NAME = "PrefHomeFragment";
    public static final String PREF_HIDE_ECHO = "HideEcho";

    private static final String KEY_UP_ARROW = "up_arrow";
    private boolean displayUpArrow;
    private HomeFragmentBinding viewBinding;
    private Disposable disposable;

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        viewBinding = HomeFragmentBinding.inflate(inflater);

        viewBinding.welcomeContainer.setVisibility(View.GONE);
        viewBinding.homeContainer.setVisibility(View.VISIBLE);
        viewBinding.swipeRefresh.setVisibility(View.VISIBLE);

        viewBinding.toolbar.inflateMenu(R.menu.home);
        viewBinding.toolbar.setOnMenuItemClickListener(this);
        if (savedInstanceState != null) {
            displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW);
        }
        viewBinding.homeScrollView.setOnScrollChangeListener(new LiftOnScrollListener(viewBinding.appbar));
        ((MainActivity) requireActivity()).setupToolbarToggle(viewBinding.toolbar, displayUpArrow);
        populateSectionList();
        updateWelcomeScreenVisibility();

        viewBinding.swipeRefresh.setDistanceToTriggerSync(getResources().getInteger(R.integer.swipe_refresh_distance));
        viewBinding.swipeRefresh.setOnRefreshListener(() ->
                FeedUpdateManager.getInstance().runOnceOrAsk(requireContext()));

        return viewBinding.getRoot();
    }

    private void populateSectionList() {
        viewBinding.homeContainer.removeAllViews();

        SharedPreferences prefs = getContext().getSharedPreferences(HomeFragment.PREF_NAME, Context.MODE_PRIVATE);
        if (EchoConfig.isCurrentlyVisible() && prefs.getInt(PREF_HIDE_ECHO, 0) != EchoConfig.RELEASE_YEAR) {
            addSection(new EchoSection(), R.id.home_section_echo);
        }

        List<String> sectionTags = HomePreferences.getSortedSectionTags(getContext());
        for (String sectionTag : sectionTags) {
            addSection(getSection(sectionTag), getSectionContainerId(sectionTag));
        }
    }

    private void addSection(Fragment section, int id) {
        if (section == null) { // Can happen when stored settings reference a section that no longer exists
            return;
        }
        FragmentContainerView containerView = new FragmentContainerView(getContext());
        containerView.setId(id);
        viewBinding.homeContainer.addView(containerView);
        getChildFragmentManager().beginTransaction().replace(containerView.getId(), section).commit();
    }

    private int getSectionContainerId(String sectionTag) {
        return switch (sectionTag) {
            case QueueSection.TAG -> R.id.home_section_queue;
            case InboxSection.TAG -> R.id.home_section_inbox;
            case EpisodesSurpriseSection.TAG -> R.id.home_section_surprise;
            case SubscriptionsSection.TAG -> R.id.home_section_subscriptions;
            case DownloadsSection.TAG -> R.id.home_section_downloads;
            case AudiothekFeaturedSection.TAG -> R.id.home_section_audiothek_featured;
            case AudiothekChartsSection.TAG -> R.id.home_section_audiothek_charts;
            case AudiothekHotSection.TAG -> R.id.home_section_audiothek_hot;
            case AudiothekLiveSection.TAG -> R.id.home_section_audiothek_live;
            case AudiothekStageSection.TAG -> R.id.home_section_audiothek_stage;
            case AudiothekSection.TAG -> R.id.home_section_audiothek;
            case AudiothekHeuteWichtigSection.TAG -> R.id.home_section_audiothek_heute_wichtig;
            case BbcSoundsFeaturedSection.TAG -> R.id.home_section_bbc_featured;
            case BbcAudioRecommendedTodaySection.TAG -> R.id.home_section_bbc_recommended;
            case SrfPlayPopularSection.TAG -> R.id.home_section_srf_popular;
            case OrfSoundPodcastsSection.TAG -> R.id.home_section_orf_podcasts;
            case DlfPodcastsSection.TAG -> R.id.home_section_dlf_podcasts;
            case RtvePodcastsSection.TAG -> R.id.home_section_rtve_podcasts;
            default -> throw new IllegalArgumentException("Unknown section tag: " + sectionTag);
        };
    }

    private Fragment getSection(String tag) {
        return switch (tag) {
            case QueueSection.TAG -> new QueueSection();
            case InboxSection.TAG -> new InboxSection();
            case EpisodesSurpriseSection.TAG -> new EpisodesSurpriseSection();
            case SubscriptionsSection.TAG -> new SubscriptionsSection();
            case DownloadsSection.TAG -> new DownloadsSection();
            case AudiothekFeaturedSection.TAG -> new AudiothekFeaturedSection();
            case AudiothekChartsSection.TAG -> new AudiothekChartsSection();
            case AudiothekHotSection.TAG -> new AudiothekHotSection();
            case AudiothekLiveSection.TAG -> new AudiothekLiveSection();
            case AudiothekStageSection.TAG -> new AudiothekStageSection();
            case AudiothekSection.TAG -> new AudiothekSection();
            case AudiothekHeuteWichtigSection.TAG -> new AudiothekHeuteWichtigSection();
            case BbcSoundsFeaturedSection.TAG -> new BbcSoundsFeaturedSection();
            case BbcAudioRecommendedTodaySection.TAG -> new BbcAudioRecommendedTodaySection();
            case SrfPlayPopularSection.TAG -> new SrfPlayPopularSection();
            case OrfSoundPodcastsSection.TAG -> new OrfSoundPodcastsSection();
            case DlfPodcastsSection.TAG -> new DlfPodcastsSection();
            case RtvePodcastsSection.TAG -> new RtvePodcastsSection();
            default -> null;
        };
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEventMainThread(FeedUpdateRunningEvent event) {
        viewBinding.swipeRefresh.setRefreshing(event.isFeedUpdateRunning);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() == R.id.homesettings_items) {
            new HomeSectionsSettingsDialog(getContext(), this::populateSectionList).show();
            return true;
        } else if (item.getItemId() == R.id.refresh_item) {
            FeedUpdateManager.getInstance().runOnceOrAsk(requireContext());
            return true;
        } else if (item.getItemId() == R.id.action_search) {
            ((MainActivity) getActivity()).loadChildFragment(SearchFragment.newInstance());
            return true;
        }
        return false;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (disposable != null) {
            disposable.dispose();
        }
        viewBinding = null;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onFeedListChanged(FeedListUpdateEvent event) {
        updateWelcomeScreenVisibility();
    }

    private void updateWelcomeScreenVisibility() {
        if (disposable != null) {
            disposable.dispose();
            disposable = null;
        }

        viewBinding.welcomeContainer.setVisibility(View.GONE);
        viewBinding.homeContainer.setVisibility(View.VISIBLE);
        viewBinding.swipeRefresh.setVisibility(View.VISIBLE);
        boolean bottomNav = UserPreferences.isBottomNavigationEnabled();
        viewBinding.arrowBottomIcon.setVisibility(bottomNav ? View.VISIBLE : View.GONE);
        viewBinding.arrowSidebarIcon.setVisibility(bottomNav ? View.GONE : View.VISIBLE);
    }

}
