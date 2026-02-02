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
import java.util.Collections;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.event.FeedListUpdateEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import de.danoeh.antennapod.ui.appstartintent.OnlineFeedviewActivityStarter;
import de.danoeh.antennapod.ui.common.SquareImageView;
import de.danoeh.antennapod.ui.screen.home.HomeSection;
import de.danoeh.antennapod.ui.screen.episode.ItemPagerFragment;
import de.danoeh.antennapod.storage.database.FeedDatabaseWriter;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AudiothekHotSection extends HomeSection {
    public static final String TAG = "AudiothekHotSection";

    private static final String API_BASE_URL = "https://api.ardaudiothek.de";
    private static final String GRAPHQL_URL = API_BASE_URL + "/graphql";
    private static final String PROGRAM_SET_URL_TEMPLATE = API_BASE_URL + "/programsets/%s";
    private static final int IMAGE_WIDTH = 600;
    private static final int NUM_ITEMS = 8;

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private static final String HOMESCREEN_STAGE_QUERY = "query HomescreenStage {"
            + " homescreen {"
            + "  sections {"
            + "   __typename "
            + "   ... on Stage {"
            + "    nodes {"
            + "     __typename title "
            + "     image { url url1X1 } "
            + "     ... on Item { id title synopsis duration publicationStartDateAndTime audios { url downloadUrl } programSet { id title } }"
            + "     ... on EventLivestream { id title editorialDescription broadcastStart audios { url downloadUrl } programSet { id title } }"
            + "     ... on Extra { id title synopsis duration audios { url downloadUrl } programSet { id title } }"
            + "     ... on CoreSection { id title synopsis audios { url downloadUrl } programSet { id title } }"
            + "    }"
            + "   }"
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
        return getString(R.string.home_audiothek_hot_title);
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
                    RequestBody body = RequestBody.create(createGraphqlRequestBody(), JSON_MEDIA_TYPE);
                    Request request = new Request.Builder()
                            .url(GRAPHQL_URL)
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Accept", "application/json")
                            .addHeader("User-Agent", "AntennaPod")
                            .post(body)
                            .build();

                    try (Response response = AntennapodHttpClient.getHttpClient().newCall(request).execute()) {
                        if (!response.isSuccessful()) {
                            throw new IOException("Unexpected response: " + response);
                        }
                        String responseBody = response.body() != null ? response.body().string() : "";
                        return parseStage(responseBody);
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

    private static String createGraphqlRequestBody() throws JSONException {
        JSONObject requestJson = new JSONObject();
        requestJson.put("query", HOMESCREEN_STAGE_QUERY);
        requestJson.put("variables", new JSONObject());
        return requestJson.toString();
    }

    private static List<AudiothekItem> parseStage(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONArray errors = root.optJSONArray("errors");
        if (errors != null && errors.length() > 0) {
            throw new JSONException(errors.toString());
        }

        JSONObject data = root.optJSONObject("data");
        if (data == null) {
            return new ArrayList<>();
        }
        JSONObject homescreen = data.optJSONObject("homescreen");
        if (homescreen == null) {
            return new ArrayList<>();
        }
        JSONArray sections = homescreen.optJSONArray("sections");
        if (sections == null) {
            return new ArrayList<>();
        }

        JSONObject stage = null;
        for (int i = 0; i < sections.length(); i++) {
            JSONObject section = sections.optJSONObject(i);
            if (section == null) {
                continue;
            }
            if ("Stage".equals(section.optString("__typename", null))) {
                stage = section;
                break;
            }
        }
        if (stage == null) {
            return new ArrayList<>();
        }

        JSONArray nodes = stage.optJSONArray("nodes");
        if (nodes == null) {
            return new ArrayList<>();
        }

        List<AudiothekItem> items = new ArrayList<>();
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject node = nodes.optJSONObject(i);
            if (node == null) {
                continue;
            }

            JSONObject programSet = node.optJSONObject("programSet");
            if (programSet == null) {
                continue;
            }
            String programSetId = programSet.optString("id", null);
            if (programSetId == null || programSetId.isEmpty()) {
                continue;
            }

            String title = node.optString("title", "");
            if (title.isEmpty()) {
                title = programSet.optString("title", "");
            }

            String audioUrl = null;
            JSONArray audios = node.optJSONArray("audios");
            if (audios != null && audios.length() > 0) {
                JSONObject audio = audios.optJSONObject(0);
                if (audio != null) {
                    audioUrl = audio.optString("downloadUrl", null);
                    if (audioUrl == null || audioUrl.isEmpty()) {
                        audioUrl = audio.optString("url", null);
                    }
                }
            }

            JSONObject image = node.optJSONObject("image");
            if (image == null) {
                image = programSet.optJSONObject("image");
            }
            String imageUrl = image != null ? image.optString("url1X1", null) : null;
            if (imageUrl == null && image != null) {
                imageUrl = image.optString("url", null);
            }
            if (imageUrl != null) {
                imageUrl = imageUrl.replace("{width}", String.valueOf(IMAGE_WIDTH));
            }

            String description = node.optString("synopsis", null);
            if (description == null || description.isEmpty()) {
                description = node.optString("editorialDescription", null);
            }

            int durationSeconds = node.optInt("duration", 0);
            String publishDate = node.optString("publicationStartDateAndTime", null);
            String itemId = node.optString("id", null);

            String programSetFeedUrl = String.format(PROGRAM_SET_URL_TEMPLATE, programSetId);

            items.add(new AudiothekItem(programSetId, programSetFeedUrl, title, description, imageUrl, audioUrl,
                    durationSeconds, publishDate, itemId));
        }
        return items;
    }

    private static class AudiothekItem {
        public final String programSetId;
        public final String programSetFeedUrl;
        public final String title;
        public final String description;
        public final String imageUrl;
        public final String audioUrl;
        public final int durationSeconds;
        public final String publishDate;
        public final String itemId;

        private AudiothekItem(String programSetId, String programSetFeedUrl, String title, String description,
                              String imageUrl, String audioUrl, int durationSeconds, String publishDate,
                              String itemId) {
            this.programSetId = programSetId;
            this.programSetFeedUrl = programSetFeedUrl;
            this.title = title;
            this.description = description;
            this.imageUrl = imageUrl;
            this.audioUrl = audioUrl;
            this.durationSeconds = durationSeconds;
            this.publishDate = publishDate;
            this.itemId = itemId;
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
                if (item.audioUrl != null && !item.audioUrl.isEmpty()) {
                    openEpisode(activity, item);
                } else {
                    activity.startActivity(new OnlineFeedviewActivityStarter(activity, item.programSetFeedUrl).getIntent());
                }
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

        private static void openEpisode(MainActivity activity, AudiothekItem item) {
            Observable.fromCallable(() -> {
                        Feed feed = new Feed("audiothek:hot:" + item.programSetId, null, "ARD Audiothek");
                        feed.setType(Feed.TYPE_RSS2);
                        feed.setTitle("ARD Audiothek");
                        feed.setState(Feed.STATE_NOT_SUBSCRIBED);
                        feed.setImageUrl(item.imageUrl);

                        FeedItem feedItem = new FeedItem();
                        feedItem.setFeed(feed);
                        feedItem.setTitle(item.title);
                        feedItem.setDescriptionIfLonger(item.description);
                        feedItem.setImageUrl(item.imageUrl);
                        if (item.itemId != null && !item.itemId.isEmpty()) {
                            feedItem.setItemIdentifier(item.itemId);
                        }
                        if (item.publishDate != null && !item.publishDate.isEmpty()) {
                            try {
                                // Very rough parsing; ok if it fails
                                feedItem.setPubDate(new Date(Date.parse(item.publishDate)));
                            } catch (Exception ignored) {
                            }
                        }

                        FeedMedia media = new FeedMedia(feedItem, item.audioUrl, 0, "audio/*");
                        if (item.durationSeconds > 0) {
                            long durationMs = item.durationSeconds * 1000L;
                            if (durationMs <= Integer.MAX_VALUE) {
                                media.setDuration((int) durationMs);
                            }
                        }
                        feedItem.setMedia(media);

                        feed.setItems(Collections.singletonList(feedItem));

                        Feed storedFeed = FeedDatabaseWriter.updateFeed(activity, feed, false);
                        if (storedFeed == null || storedFeed.getItems() == null || storedFeed.getItems().isEmpty()) {
                            return null;
                        }
                        return storedFeed.getItems().get(0);
                    })
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(createdItem -> {
                        if (createdItem == null) {
                            return;
                        }
                        activity.loadChildFragment(ItemPagerFragment.newInstance(
                                Collections.singletonList(createdItem), createdItem));
                    }, error -> Log.e(TAG, Log.getStackTraceString(error)));
        }
    }
}
