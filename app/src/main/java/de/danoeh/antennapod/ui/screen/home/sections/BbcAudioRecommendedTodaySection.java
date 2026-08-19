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
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.event.FeedListUpdateEvent;
import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import de.danoeh.antennapod.ui.appstartintent.OnlineFeedviewActivityStarter;
import de.danoeh.antennapod.ui.common.SquareImageView;
import de.danoeh.antennapod.ui.screen.home.HomeSection;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.Request;
import okhttp3.Response;

public class BbcAudioRecommendedTodaySection extends HomeSection {
    public static final String TAG = "BbcAudioRecommendedTodaySection";

    // Public (no auth) bbc.com/audio content-collection endpoint for "Recommended Today".
    // The UUID is the CMS collection ID baked into the bbc.com/audio page (SSR __NEXT_DATA__).
    private static final String BBC_AUDIO_RECOMMENDED_URL =
            "https://web-cdn.api.bbci.co.uk/xd/content-collection"
            + "/a5356eda-60e5-4b31-b19f-897ece850eaf?page=0&size=8";
    private static final String BBC_FEED_URL_TEMPLATE = "https://podcasts.files.bbci.co.uk/%s.rss";
    private static final int NUM_ITEMS = 8;

    private Disposable disposable;
    private BbcAudioHorizontalAdapter listAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View view = super.onCreateView(inflater, container, savedInstanceState);

        listAdapter = new BbcAudioHorizontalAdapter((MainActivity) requireActivity());
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
        return getString(R.string.home_bbc_audio_recommended_today_title);
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
            Request request = new Request.Builder().url(BBC_AUDIO_RECOMMENDED_URL).build();
            try (Response response = AntennapodHttpClient.getHttpClient().newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Unexpected response: " + response);
                }
                String body = response.body() != null ? response.body().string() : "";
                return parseRecommended(body);
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

    private static List<BbcAudioItem> parseRecommended(String json) throws JSONException {
        // The bbc.com/audio content-collection API returns a flat "data" array of episode objects.
        // Each episode has a "brand" (or "series" as fallback) whose id is used for the RSS feed.
        // Image comes from indexImage.model.blocks.src with a {recipe} placeholder.
        JSONObject root = new JSONObject(json);
        JSONArray data = root.optJSONArray("data");
        if (data == null) {
            return new ArrayList<>();
        }

        List<BbcAudioItem> items = new ArrayList<>();
        for (int i = 0; i < data.length() && items.size() < NUM_ITEMS; i++) {
            JSONObject item = data.optJSONObject(i);
            if (item == null) {
                continue;
            }

            // Prefer brand id, fall back to series id.
            // Note: optString converts JSON null to the string "null", so check for that too.
            String pid = null;
            JSONObject brand = item.optJSONObject("brand");
            if (brand != null && !brand.isNull("id")) {
                pid = brand.optString("id", null);
            }
            if (pid == null || pid.isEmpty()) {
                JSONObject series = item.optJSONObject("series");
                if (series != null && !series.isNull("id")) {
                    pid = series.optString("id", null);
                }
            }
            if (pid == null || pid.isEmpty()) {
                continue;
            }
            String displayTitle = item.optString("title", "");

            String imageUrl = null;
            JSONObject indexImage = item.optJSONObject("indexImage");
            if (indexImage != null) {
                JSONObject model = indexImage.optJSONObject("model");
                if (model != null) {
                    JSONObject blocks = model.optJSONObject("blocks");
                    if (blocks != null) {
                        imageUrl = blocks.optString("src", null);
                    }
                }
            }
            if (imageUrl != null) {
                imageUrl = imageUrl.replace("{recipe}", "512x512");
            }

            String feedUrl = String.format(BBC_FEED_URL_TEMPLATE, pid);
            items.add(new BbcAudioItem(displayTitle, imageUrl, feedUrl));
        }
        return items;
    }

    private static class BbcAudioItem {
        public final String title;
        public final String imageUrl;
        public final String feedUrl;

        private BbcAudioItem(String title, String imageUrl, String feedUrl) {
            this.title = title;
            this.imageUrl = imageUrl;
            this.feedUrl = feedUrl;
        }
    }

    private static class BbcAudioHorizontalAdapter extends RecyclerView.Adapter<BbcAudioHorizontalAdapter.Holder> {
        private final WeakReference<MainActivity> mainActivityRef;
        private final List<BbcAudioItem> items = new ArrayList<>();
        private int dummyViews = 0;

        BbcAudioHorizontalAdapter(MainActivity activity) {
            this.mainActivityRef = new WeakReference<>(activity);
        }

        void setDummyViews(int dummyViews) {
            this.dummyViews = dummyViews;
            notifyDataSetChanged();
        }

        void updateData(List<BbcAudioItem> newItems) {
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
            BbcAudioItem item = items.get(position);
            holder.imageView.setContentDescription(item.title);
            holder.titleView.setText(item.title);
            holder.titleView.setVisibility(View.VISIBLE);
            holder.imageView.setOnClickListener(v ->
                    activity.startActivity(new OnlineFeedviewActivityStarter(activity, item.feedUrl).getIntent()));

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
