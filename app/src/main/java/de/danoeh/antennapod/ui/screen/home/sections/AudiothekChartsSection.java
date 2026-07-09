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
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AudiothekChartsSection extends HomeSection {
    public static final String TAG = "AudiothekChartsSection";

    private static final int NUM_ITEMS = 8;
    private static final String API_BASE_URL = "https://api.ardaudiothek.de";
    private static final String GRAPHQL_URL = API_BASE_URL + "/graphql";
    private static final String PROGRAM_SET_URL_TEMPLATE = API_BASE_URL + "/programsets/%s";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");
    // The "Podcast Charts" module of the ARD Sounds home board (source: Playout).
    private static final String CHARTS_WIDGET_TYPE = "rankedSwiperTeaserWidget";
    private static final String CHARTS_QUERY = "query Charts($source: SourceSystem) {"
            + " homescreen(source: $source) {"
            + "  sections {"
            + "   type title"
            + "   teasers { title contentId image { url1X1 } }"
            + "  }"
            + " }"
            + "}";

    private Disposable disposable;
    private AudiothekHorizontalAdapter listAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View view = super.onCreateView(inflater, container, savedInstanceState);

        listAdapter = new AudiothekHorizontalAdapter((MainActivity) requireActivity());
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
        // The charts are global ARD data, independent of the user's subscriptions.
        // Reloading here would only flash the placeholder tiles on every feed update.
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
        return getString(R.string.home_audiothek_charts_title);
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
                    JSONObject variables = new JSONObject().put("source", "Playout");
                    JSONObject requestJson = new JSONObject()
                            .put("query", CHARTS_QUERY)
                            .put("variables", variables);
                    RequestBody requestBody = RequestBody.create(requestJson.toString(), JSON_MEDIA_TYPE);
                    Request request = new Request.Builder()
                            .url(GRAPHQL_URL)
                            .addHeader("Accept", "application/json")
                            .post(requestBody)
                            .build();
                    try (Response response = AntennapodHttpClient.getHttpClient().newCall(request).execute()) {
                        if (!response.isSuccessful()) {
                            throw new IOException("Unexpected response: " + response);
                        }
                        String body = response.body() != null ? response.body().string() : "";
                        return parseCharts(body);
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

    private static List<AudiothekItem> parseCharts(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONObject data = root.optJSONObject("data");
        JSONObject homescreen = data != null ? data.optJSONObject("homescreen") : null;
        JSONArray sections = homescreen != null ? homescreen.optJSONArray("sections") : null;
        if (sections == null) {
            return new ArrayList<>();
        }

        JSONObject chartsSection = null;
        for (int i = 0; i < sections.length(); i++) {
            JSONObject section = sections.optJSONObject(i);
            if (section == null) {
                continue;
            }
            if (CHARTS_WIDGET_TYPE.equals(section.optString("type", null))) {
                chartsSection = section;
                break;
            }
            // Fallback in case ARD renames the widget type: match the charts module by title.
            if (chartsSection == null && section.optString("title", "").contains("Charts")) {
                chartsSection = section;
            }
        }
        JSONArray teasers = chartsSection != null ? chartsSection.optJSONArray("teasers") : null;
        if (teasers == null) {
            return new ArrayList<>();
        }

        List<AudiothekItem> items = new ArrayList<>();
        for (int i = 0; i < teasers.length(); i++) {
            JSONObject teaser = teasers.optJSONObject(i);
            if (teaser == null) {
                continue;
            }
            String contentId = teaser.optString("contentId", null);
            if (contentId == null) {
                continue;
            }
            String feedUrl = String.format(PROGRAM_SET_URL_TEMPLATE, contentId);

            JSONObject image = teaser.optJSONObject("image");
            String imageUrl = image != null ? image.optString("url1X1", null) : null;
            if (imageUrl != null) {
                imageUrl = imageUrl.replace("{width}", "400");
            }

            String title = teaser.optString("title", "");
            items.add(new AudiothekItem(title, imageUrl, feedUrl));
        }
        return items;
    }

    private static class AudiothekItem {
        public final String title;
        public final String imageUrl;
        public final String feedUrl;

        private AudiothekItem(String title, String imageUrl, String feedUrl) {
            this.title = title;
            this.imageUrl = imageUrl;
            this.feedUrl = feedUrl;
        }
    }

    private static class AudiothekHorizontalAdapter extends RecyclerView.Adapter<AudiothekHorizontalAdapter.Holder> {
        private final WeakReference<MainActivity> mainActivityRef;
        private final List<AudiothekItem> items = new ArrayList<>();
        private int dummyViews = 0;

        AudiothekHorizontalAdapter(MainActivity activity) {
            this.mainActivityRef = new WeakReference<>(activity);
        }

        void setDummyViews(int dummyViews) {
            this.dummyViews = dummyViews;
            notifyDataSetChanged();
        }

        void updateData(List<AudiothekItem> newItems) {
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
            AudiothekItem item = items.get(position);
            holder.imageView.setContentDescription(item.title);
            holder.titleView.setText(item.title);
            holder.titleView.setVisibility(View.VISIBLE);
            holder.imageView.setOnClickListener(v -> {
                activity.startActivity(new OnlineFeedviewActivityStarter(activity, item.feedUrl).getIntent());
            });

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
