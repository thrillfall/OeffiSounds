package de.danoeh.antennapod.ui.discovery;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import androidx.fragment.app.Fragment;
import de.danoeh.antennapod.net.discovery.AudiothekPodcastSearcher;
import de.danoeh.antennapod.net.discovery.PodcastSearchResult;
import de.danoeh.antennapod.ui.appstartintent.OnlineFeedviewActivityStarter;
import de.danoeh.antennapod.ui.discovery.databinding.QuickFeedDiscoveryBinding;
import io.reactivex.rxjava3.disposables.Disposable;

import java.util.ArrayList;
import java.util.List;

public class QuickFeedDiscoveryFragment extends Fragment implements AdapterView.OnItemClickListener {
    private static final String TAG = "FeedDiscoveryFragment";
    private static final int NUM_SUGGESTIONS = 12;

    private Disposable disposable;
    private FeedDiscoverAdapter adapter;
    private QuickFeedDiscoveryBinding viewBinding;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        viewBinding = QuickFeedDiscoveryBinding.inflate(inflater);
        viewBinding.discoverMore.setVisibility(View.GONE);

        adapter = new FeedDiscoverAdapter(getActivity());
        viewBinding.discoverGrid.setAdapter(adapter);
        viewBinding.discoverGrid.setOnItemClickListener(this);

        DisplayMetrics displayMetrics = getContext().getResources().getDisplayMetrics();
        float screenWidthDp = displayMetrics.widthPixels / displayMetrics.density;
        if (screenWidthDp > 600) {
            viewBinding.discoverGrid.setNumColumns(6);
        } else {
            viewBinding.discoverGrid.setNumColumns(4);
        }

        // Fill with dummy elements to have a fixed height and
        // prevent the UI elements below from jumping on slow connections
        List<PodcastSearchResult> dummies = new ArrayList<>();
        for (int i = 0; i < NUM_SUGGESTIONS; i++) {
            dummies.add(PodcastSearchResult.dummy());
        }

        adapter.updateData(dummies);
        loadToplist();

        return viewBinding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        viewBinding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (disposable != null) {
            disposable.dispose();
        }
    }

    private void loadToplist() {
        viewBinding.errorContainer.setVisibility(View.GONE);
        viewBinding.poweredByLabel.setVisibility(View.VISIBLE);

        disposable = new AudiothekPodcastSearcher().getSuggestions()
                .subscribe(podcasts -> {
                    viewBinding.errorContainer.setVisibility(View.GONE);
                    if (podcasts.isEmpty()) {
                        viewBinding.errorLabel.setText(getResources().getText(R.string.search_status_no_results));
                        viewBinding.errorContainer.setVisibility(View.VISIBLE);
                        viewBinding.discoverGrid.setVisibility(View.INVISIBLE);
                    } else {
                        viewBinding.discoverGrid.setVisibility(View.VISIBLE);
                        adapter.updateData(podcasts);
                    }
                }, error -> {
                    Log.e(TAG, Log.getStackTraceString(error));
                    viewBinding.errorLabel.setText(error.getLocalizedMessage());
                    viewBinding.errorContainer.setVisibility(View.VISIBLE);
                    viewBinding.discoverGrid.setVisibility(View.INVISIBLE);
                    viewBinding.errorRetryButton.setVisibility(View.VISIBLE);
                    viewBinding.errorRetryButton.setOnClickListener(v -> loadToplist());
                });
    }

    @Override
    public void onItemClick(AdapterView<?> parent, final View view, int position, long id) {
        PodcastSearchResult podcast = adapter.getItem(position);
        if (TextUtils.isEmpty(podcast.feedUrl)) {
            return;
        }
        startActivity(new OnlineFeedviewActivityStarter(getContext(), podcast.feedUrl).getIntent());
    }
}
