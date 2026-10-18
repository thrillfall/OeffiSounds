package de.danoeh.antennapod.ui.screen.home.sections;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.event.FeedListUpdateEvent;
import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import de.danoeh.antennapod.ui.appstartintent.OnlineFeedviewActivityStarter;
import de.danoeh.antennapod.ui.screen.home.HomeSection;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.Request;
import okhttp3.Response;

public class AudiothekPanoramaSection extends HomeSection {
    public static final String TAG = "AudiothekPanoramaSection";

    private static final String PLAYOUT_API_URL = "https://api.ard.de/playout-api/v1/pages?canonicalWebURL=/";
    private static final String PROGRAM_SET_URL_PREFIX = "https://api.ardaudiothek.de/programsets/";
    private static final String PANORAMA_WIDGET_TYPE = "panoramaTeaserWidget";
    private static final int NUM_DUMMIES = 2;

    private Disposable disposable;
    private PanoramaAdapter listAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View view = super.onCreateView(inflater, container, savedInstanceState);

        listAdapter = new PanoramaAdapter((MainActivity) requireActivity());
        listAdapter.setDummyViews(NUM_DUMMIES);
        viewBinding.recyclerView.setLayoutManager(
                new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false));
        viewBinding.recyclerView.setAdapter(listAdapter);
        int paddingHorizontal = (int) (12 * getResources().getDisplayMetrics().density);
        viewBinding.recyclerView.setPadding(paddingHorizontal, 0, paddingHorizontal, 0);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        loadItems();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onFeedListChanged(FeedListUpdateEvent event) {
        loadItems();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (disposable != null) {
            disposable.dispose();
        }
    }

    @Override
    protected void handleMoreClick() {
    }

    @Override
    protected String getSectionTitle() {
        return getString(R.string.home_audiothek_panorama_title);
    }

    @Override
    protected String getMoreLinkTitle() {
        return "";
    }

    private void loadItems() {
        if (disposable != null) {
            disposable.dispose();
        }
        listAdapter.setDummyViews(NUM_DUMMIES);

        disposable = Observable.fromCallable(() -> {
            Request request = new Request.Builder()
                    .url(PLAYOUT_API_URL)
                    .build();
            try (Response response = AntennapodHttpClient.getHttpClient().newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Unexpected response: " + response);
                }
                String body = response.body() != null ? response.body().string() : "";
                return parsePanoramas(body);
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(items -> {
                    listAdapter.setDummyViews(0);
                    listAdapter.updateData(items);
                    viewBinding.getRoot().setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
                }, error -> {
                    Log.e(TAG, Log.getStackTraceString(error));
                    listAdapter.setDummyViews(0);
                    listAdapter.updateData(new ArrayList<>());
                    viewBinding.getRoot().setVisibility(View.GONE);
                });
    }

    static List<PanoramaItem> parsePanoramas(String json) throws JSONException {
        List<PanoramaItem> items = new ArrayList<>();
        JSONArray widgets = new JSONObject(json).optJSONArray("widgets");
        if (widgets == null) {
            return items;
        }
        for (int i = 0; i < widgets.length(); i++) {
            JSONObject widget = widgets.optJSONObject(i);
            if (widget == null || !PANORAMA_WIDGET_TYPE.equals(widget.optString("widgetType"))) {
                continue;
            }
            JSONArray teasers = widget.optJSONArray("teasers");
            if (teasers == null) {
                continue;
            }
            for (int j = 0; j < teasers.length(); j++) {
                JSONObject teaser = teasers.optJSONObject(j);
                if (teaser == null) {
                    continue;
                }
                String showId = teaser.optString("showId", "");
                if (!showId.startsWith("urn:ard:show:")) {
                    String assetId = teaser.optString("assetId", "");
                    showId = assetId.startsWith("urn:ard:show:") ? assetId : "";
                }
                if (showId.isEmpty()) {
                    continue;
                }
                JSONObject image = teaser.optJSONObject("image");
                String imageUrl = image != null ? image.optString("templateURL", null) : null;
                if (imageUrl != null) {
                    imageUrl = imageUrl.replace("{width}", "640");
                }
                items.add(new PanoramaItem(widget.optString("title", ""), teaser.optString("title", ""),
                        teaser.optString("description", ""), imageUrl, PROGRAM_SET_URL_PREFIX + showId));
            }
        }
        return items;
    }

    static class PanoramaItem {
        final String widgetTitle;
        final String title;
        final String description;
        final String imageUrl;
        final String feedUrl;

        PanoramaItem(String widgetTitle, String title, String description, String imageUrl, String feedUrl) {
            this.widgetTitle = widgetTitle;
            this.title = title;
            this.description = description;
            this.imageUrl = imageUrl;
            this.feedUrl = feedUrl;
        }
    }

    private static class PanoramaAdapter extends RecyclerView.Adapter<PanoramaAdapter.Holder> {
        private final WeakReference<MainActivity> mainActivityRef;
        private final List<PanoramaItem> items = new ArrayList<>();
        private int dummyViews = 0;

        PanoramaAdapter(MainActivity activity) {
            this.mainActivityRef = new WeakReference<>(activity);
        }

        void setDummyViews(int dummyViews) {
            this.dummyViews = dummyViews;
            notifyDataSetChanged();
        }

        void updateData(List<PanoramaItem> newItems) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.audiothek_panorama_item, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            MainActivity activity = mainActivityRef.get();
            if (activity == null || position >= items.size()) {
                holder.itemView.setAlpha(0.1f);
                holder.widgetTitleLabel.setText("");
                holder.titleLabel.setText("");
                holder.descriptionLabel.setText("");
                Glide.with(holder.imageView).clear(holder.imageView);
                holder.imageView.setImageResource(R.color.medium_gray);
                holder.imageView.setOnClickListener(null);
                holder.itemView.setOnClickListener(null);
                return;
            }

            holder.itemView.setAlpha(1.0f);
            PanoramaItem item = items.get(position);
            holder.widgetTitleLabel.setText(item.widgetTitle);
            holder.titleLabel.setText(item.title);
            holder.descriptionLabel.setText(item.description);
            holder.descriptionLabel.setVisibility(item.description.isEmpty() ? View.GONE : View.VISIBLE);
            View.OnClickListener openFeed = v -> activity.startActivity(
                    new OnlineFeedviewActivityStarter(activity, item.feedUrl).getIntent());
            holder.imageView.setOnClickListener(openFeed);
            holder.itemView.setOnClickListener(openFeed);

            Glide.with(activity)
                    .load(item.imageUrl)
                    .apply(new RequestOptions()
                            .placeholder(R.color.light_gray)
                            .centerCrop()
                            .dontAnimate())
                    .into(holder.imageView);
        }

        @Override
        public int getItemCount() {
            return items.size() + dummyViews;
        }

        static class Holder extends RecyclerView.ViewHolder {
            final TextView widgetTitleLabel;
            final ImageView imageView;
            final TextView titleLabel;
            final TextView descriptionLabel;

            Holder(@NonNull View itemView) {
                super(itemView);
                widgetTitleLabel = itemView.findViewById(R.id.widgetTitleLabel);
                imageView = itemView.findViewById(R.id.imageView);
                titleLabel = itemView.findViewById(R.id.titleLabel);
                descriptionLabel = itemView.findViewById(R.id.descriptionLabel);
            }
        }
    }
}
