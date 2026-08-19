package de.danoeh.antennapod.ui.screen.home.sections;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.event.FeedListUpdateEvent;
import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import de.danoeh.antennapod.net.discovery.DLFPodcastSearcher;
import de.danoeh.antennapod.ui.appstartintent.OnlineFeedviewActivityStarter;
import de.danoeh.antennapod.ui.common.SquareImageView;
import de.danoeh.antennapod.ui.screen.home.HomeSection;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DlfPodcastsSection extends HomeSection {
    public static final String TAG = "DlfPodcastsSection";

    private static final String DLF_PODCASTS_PAGE =
            "https://www.deutschlandfunk.de/podcasts";
    private static final String SELECTED_URL =
            "https://dlf-audiothek-appapi.deutschlandradio.de/selected-podcasts";
    private static final String BROADCASTS_URL =
            "https://dlf-audiothek-appapi.deutschlandradio.de/broadcasts";
    private static final int NUM_ITEMS = 8;

    private static final Pattern DATA_JSON_PATTERN =
            Pattern.compile("data-json=\"([^\"]+)\"");

    private Disposable disposable;
    private DlfHorizontalAdapter listAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View view = super.onCreateView(inflater, container, savedInstanceState);

        listAdapter = new DlfHorizontalAdapter((MainActivity) requireActivity());
        listAdapter.setDummyViews(NUM_ITEMS);
        viewBinding.recyclerView.setLayoutManager(
                new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false));
        viewBinding.recyclerView.setAdapter(listAdapter);
        int paddingHorizontal = (int) (12 * getResources().getDisplayMetrics().density);
        viewBinding.recyclerView.setPadding(paddingHorizontal, 0, paddingHorizontal, 0);
        viewBinding.emptyLabel.setText(R.string.home_new_empty_text);

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
        return getString(R.string.home_dlf_podcasts_title);
    }

    @Override
    protected String getMoreLinkTitle() {
        return "";
    }

    private void loadItems() {
        if (disposable != null) {
            disposable.dispose();
        }
        listAdapter.setDummyViews(NUM_ITEMS);

        disposable = Observable.fromCallable(() -> {
                    OkHttpClient client = AntennapodHttpClient.getHttpClient();

                    // Build ext_id → feedUrl lookup from DLF website
                    Map<String, String> extIdToFeed = new HashMap<>();
                    Request pageReq = new Request.Builder().url(DLF_PODCASTS_PAGE).build();
                    try (Response resp = client.newCall(pageReq).execute()) {
                        if (resp.isSuccessful() && resp.body() != null) {
                            String html = resp.body().string();
                            Matcher m = DATA_JSON_PATTERN.matcher(html);
                            while (m.find()) {
                                String decoded = m.group(1)
                                        .replace("&quot;", "\"")
                                        .replace("&amp;", "&")
                                        .replace("&lt;", "<")
                                        .replace("&gt;", ">");
                                try {
                                    JSONObject data = new JSONObject(decoded);
                                    JSONObject val = data.optJSONObject("value");
                                    if (val == null) {
                                        continue;
                                    }
                                    String feed = val.optString("pathPodcast", "");
                                    String extId = val.optString("sophoraExternalId", "");
                                    if (!feed.isEmpty() && feed.endsWith(".xml") && !extId.isEmpty()) {
                                        extIdToFeed.put(extId, feed);
                                    }
                                } catch (JSONException ignored) {
                                }
                            }
                        }
                    }

                    // Build ext_id → (stationId, sophoraId, imageUrl) from API for Kultur fallback
                    Map<String, BroadcastInfo> apiLookup = new HashMap<>();
                    Map<String, String> titleToImage = new HashMap<>();
                    Request apiReq = new Request.Builder().url(BROADCASTS_URL).build();
                    try (Response resp = client.newCall(apiReq).execute()) {
                        if (resp.isSuccessful() && resp.body() != null) {
                            JSONArray broadcasts = new JSONArray(resp.body().string());
                            for (int i = 0; i < broadcasts.length(); i++) {
                                JSONObject b = broadcasts.getJSONObject(i);
                                String extId = b.optString("broadcast_external_id", "");
                                if (!extId.isEmpty()) {
                                    apiLookup.put(extId, new BroadcastInfo(
                                            b.optString("station_id"),
                                            b.optString("sophora_id")));
                                }
                                String title = b.optString("broadcast_title", "");
                                String img = b.optString("broadcast_image_logo", null);
                                if (!title.isEmpty() && img != null && !img.isEmpty()) {
                                    titleToImage.put(title, img);
                                }
                            }
                        }
                    }

                    // Fetch selected (curated) podcasts and resolve feed URLs
                    Request selectedReq = new Request.Builder().url(SELECTED_URL).build();
                    try (Response resp = client.newCall(selectedReq).execute()) {
                        if (!resp.isSuccessful() || resp.body() == null) {
                            throw new IOException("Unexpected response: " + resp);
                        }
                        return parseSelectedPodcasts(resp.body().string(),
                                extIdToFeed, apiLookup, titleToImage);
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(items -> {
                    listAdapter.setDummyViews(0);
                    listAdapter.updateData(items);
                    boolean isEmpty = items.isEmpty();
                    viewBinding.recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
                    viewBinding.emptyLabel.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                }, error -> {
                    Log.e(TAG, Log.getStackTraceString(error));
                    listAdapter.setDummyViews(0);
                    listAdapter.updateData(new ArrayList<>());
                    viewBinding.recyclerView.setVisibility(View.GONE);
                    viewBinding.emptyLabel.setVisibility(View.VISIBLE);
                });
    }

    private static List<DlfItem> parseSelectedPodcasts(
            String json,
            Map<String, String> extIdToFeed,
            Map<String, BroadcastInfo> apiLookup,
            Map<String, String> titleToImage) throws JSONException {

        JSONObject root = new JSONObject(json);
        JSONArray podcasts = root.optJSONArray("podcasts");
        if (podcasts == null) {
            return new ArrayList<>();
        }

        List<DlfItem> items = new ArrayList<>();
        for (int i = 0; i < podcasts.length() && items.size() < NUM_ITEMS; i++) {
            JSONObject podcast = podcasts.getJSONObject(i);
            String extId = podcast.optString("broadcast_external_id", "");
            String title = podcast.optString("title", "");

            // Try website lookup first (DLF)
            String feedUrl = extIdToFeed.get(extId);

            // Fall back to API for Kultur podcasts
            if (feedUrl == null) {
                BroadcastInfo info = apiLookup.get(extId);
                if (info != null) {
                    feedUrl = DLFPodcastSearcher.feedUrlForBroadcast(info.stationId, info.sophoraId);
                }
            }

            if (feedUrl == null) {
                continue;
            }

            String imageUrl = titleToImage.get(title);
            if (imageUrl == null) {
                JSONObject image = podcast.optJSONObject("image");
                if (image != null) {
                    imageUrl = image.optString("logo", null);
                    if (imageUrl == null || imageUrl.isEmpty()) {
                        imageUrl = image.optString("small", null);
                    }
                }
            }

            items.add(new DlfItem(title, imageUrl, feedUrl));
        }
        return items;
    }

    private static class BroadcastInfo {
        final String stationId;
        final String sophoraId;

        BroadcastInfo(String stationId, String sophoraId) {
            this.stationId = stationId;
            this.sophoraId = sophoraId;
        }
    }

    private static class DlfItem {
        final String title;
        final String imageUrl;
        final String feedUrl;

        DlfItem(String title, String imageUrl, String feedUrl) {
            this.title = title;
            this.imageUrl = imageUrl;
            this.feedUrl = feedUrl;
        }
    }

    private static class DlfHorizontalAdapter extends RecyclerView.Adapter<DlfHorizontalAdapter.Holder> {
        private final WeakReference<MainActivity> mainActivityRef;
        private final List<DlfItem> items = new ArrayList<>();
        private int dummyViews = 0;

        DlfHorizontalAdapter(MainActivity activity) {
            this.mainActivityRef = new WeakReference<>(activity);
        }

        void setDummyViews(int dummyViews) {
            this.dummyViews = dummyViews;
            notifyDataSetChanged();
        }

        void updateData(List<DlfItem> newItems) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            MainActivity activity = mainActivityRef.get();
            View convertView;
            if (activity != null) {
                convertView = View.inflate(activity, R.layout.horizontal_feed_item, null);
            } else {
                convertView = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.horizontal_feed_item, parent, false);
            }
            return new Holder(convertView);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            MainActivity activity = mainActivityRef.get();
            if (activity == null) {
                holder.itemView.setAlpha(0.1f);
                holder.imageView.setOnClickListener(null);
                holder.titleView.setVisibility(View.GONE);
                return;
            }
            holder.cardView.setVisibility(View.VISIBLE);
            holder.actionButton.setVisibility(View.GONE);
            if (position >= items.size()) {
                holder.itemView.setAlpha(0.1f);
                Glide.with(activity).clear(holder.imageView);
                holder.imageView.setImageResource(R.color.medium_gray);
                holder.imageView.setOnClickListener(null);
                holder.titleView.setVisibility(View.GONE);
                return;
            }

            holder.itemView.setAlpha(1.0f);
            DlfItem item = items.get(position);
            holder.imageView.setContentDescription(item.title);
            holder.titleView.setText(item.title);
            holder.titleView.setVisibility(View.VISIBLE);
            holder.imageView.setOnClickListener(v ->
                    activity.startActivity(
                            new OnlineFeedviewActivityStarter(activity, item.feedUrl).getIntent()));

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
            SquareImageView imageView;
            androidx.cardview.widget.CardView cardView;
            android.widget.Button actionButton;
            TextView titleView;

            Holder(@NonNull View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.discovery_cover);
                imageView.setDirection(SquareImageView.DIRECTION_HEIGHT);
                actionButton = itemView.findViewById(R.id.actionButton);
                cardView = itemView.findViewById(R.id.cardView);
                titleView = itemView.findViewById(R.id.titleLabel);
            }
        }
    }
}
