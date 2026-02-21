package de.danoeh.antennapod.ui.screen.home.sections;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

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

public class AudiothekHeuteWichtigSection extends HomeSection {
    public static final String TAG = "AudiothekHeuteWichtigSection";

    private static final String AUDIOTHEK_POLITIK_URL = "https://api.ardaudiothek.de/graphql";
    private static final String API_BASE_URL = "https://api.ardaudiothek.de";
    private static final String GRAPHQL_QUERY = "{\"query\":\"query { editorialCategory(id: \\\"51850530\\\") { sections { title nodes { __typename id title image { url url1X1 } ... on ItemInterface { programSet { id title } } } } } }\"}";
    private static final String PROGRAM_SET_URL_TEMPLATE = API_BASE_URL + "/programsets/%s";
    private static final int NUM_ITEMS = 8;

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
        return getString(R.string.home_audiothek_heute_wichtig_title);
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
                    Request.Builder requestBuilder = new Request.Builder()
                            .url(AUDIOTHEK_POLITIK_URL)
                            .addHeader("Content-Type", "application/json")
                            .post(okhttp3.RequestBody.create(GRAPHQL_QUERY, okhttp3.MediaType.parse("application/json")));
                    
                    try (Response response = AntennapodHttpClient.getHttpClient().newCall(requestBuilder.build()).execute()) {
                        if (!response.isSuccessful()) {
                            throw new IOException("Unexpected response: " + response);
                        }
                        String body = response.body() != null ? response.body().string() : "";
                        return parseHeuteWichtigGraphQL(body);
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

    private static List<AudiothekItem> parseHeuteWichtigGraphQL(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONObject data = root.optJSONObject("data");
        if (data == null) {
            return new ArrayList<>();
        }
        
        JSONObject editorialCategory = data.optJSONObject("editorialCategory");
        if (editorialCategory == null) {
            return new ArrayList<>();
        }
        
        JSONArray sections = editorialCategory.optJSONArray("sections");
        if (sections == null) {
            return new ArrayList<>();
        }

        // Find the "Heute wichtig" section
        for (int i = 0; i < sections.length(); i++) {
            JSONObject section = sections.optJSONObject(i);
            if (section == null) {
                continue;
            }
            
            String title = section.optString("title");
            if ("Heute wichtig".equals(title)) {
                return parseGraphQLSectionItems(section);
            }
        }
        
        return new ArrayList<>();
    }

    private static List<AudiothekItem> parseGraphQLSectionItems(JSONObject section) throws JSONException {
        List<AudiothekItem> items = new ArrayList<>();
        JSONArray nodes = section.optJSONArray("nodes");
        if (nodes == null) {
            return items;
        }

        for (int i = 0; i < nodes.length() && i < NUM_ITEMS; i++) {
            JSONObject node = nodes.optJSONObject(i);
            if (node == null) {
                continue;
            }

            String title = node.optString("title", "");
            
            // Get programSet data like Hot section does
            JSONObject programSet = node.optJSONObject("programSet");
            if (programSet == null) {
                continue;
            }
            String programSetId = programSet.optString("id", null);
            if (programSetId == null || programSetId.isEmpty()) {
                continue;
            }

            // Get image directly from node (same as other working sections)
            JSONObject image = node.optJSONObject("image");
            String imageUrl = image != null ? image.optString("url1X1", null) : null;
            if (imageUrl == null && image != null) {
                imageUrl = image.optString("url", null);
            }
            if (imageUrl != null) {
                imageUrl = imageUrl.replace("{width}", "400");
            }
            
            // Build feedUrl from programSetId like Hot section does
            String feedUrl = String.format(PROGRAM_SET_URL_TEMPLATE, programSetId);
            items.add(new AudiothekItem(title, imageUrl, feedUrl));
        }

        return items;
    }

    private static List<AudiothekItem> parseGraphQLItem(JSONObject item) throws JSONException {
        List<AudiothekItem> items = new ArrayList<>();
        
        String title = item.optString("title", "");
        String id = item.optString("id", "");
        String feedUrl = API_BASE_URL + "/items/" + id;
        
        // Try to extract image from the item if available
        String imageUrl = null;
        JSONObject links = item.optJSONObject("_links");
        if (links != null) {
            JSONObject image = links.optJSONObject("mt:squareImage");
            if (image == null) {
                image = links.optJSONObject("mt:image");
            }
            if (image != null) {
                imageUrl = image.optString("href", null);
            }
        }
        
        items.add(new AudiothekItem(title, imageUrl, feedUrl));
        return items;
    }

    private static List<AudiothekItem> parseGraphQLProgramSet(JSONObject programSet) throws JSONException {
        List<AudiothekItem> items = new ArrayList<>();
        
        String title = programSet.optString("title", "");
        String id = programSet.optString("id", "");
        String feedUrl = API_BASE_URL + "/items/" + id;
        
        // Try to extract image from program set
        String imageUrl = null;
        JSONObject links = programSet.optJSONObject("_links");
        if (links != null) {
            JSONObject image = links.optJSONObject("mt:squareImage");
            if (image == null) {
                image = links.optJSONObject("mt:image");
            }
            if (image != null) {
                imageUrl = image.optString("href", null);
            }
        }
        
        items.add(new AudiothekItem(title, imageUrl, feedUrl));
        return items;
    }

    private static List<AudiothekItem> parseProgramSetsArray(JSONArray programSetsArray) {
        List<AudiothekItem> items = new ArrayList<>();
        for (int i = 0; i < programSetsArray.length(); i++) {
            JSONObject programSet = programSetsArray.optJSONObject(i);
            if (programSet == null) {
                continue;
            }
            JSONObject links = programSet.optJSONObject("_links");
            JSONObject self = links != null ? links.optJSONObject("self") : null;
            String href = self != null ? self.optString("href", null) : null;
            if (href == null) {
                continue;
            }
            href = href.replace("{?order,offset,limit}", "");
            String feedUrl = normalizeFeedUrl(href.startsWith("http") ? href : API_BASE_URL + href);

            JSONObject image = links != null ? links.optJSONObject("mt:squareImage") : null;
            if (image == null) {
                image = links != null ? links.optJSONObject("mt:image") : null;
            }
            String imageUrl = image != null ? image.optString("href", null) : null;
            if (imageUrl != null) {
                imageUrl = imageUrl.replace("{width}", "400");
                imageUrl = imageUrl.replace("{ratio}", "1x1");
            }

            String title = programSet.optString("title", "");
            items.add(new AudiothekItem(title, imageUrl, feedUrl));
        }
        return items;
    }

    private static List<AudiothekItem> parseItemsArray(JSONArray itemsArray) {
        List<AudiothekItem> items = new ArrayList<>();
        for (int i = 0; i < itemsArray.length(); i++) {
            JSONObject item = itemsArray.optJSONObject(i);
            if (item == null) {
                continue;
            }

            JSONObject embeddedProgramSet = null;
            JSONObject embedded = item.optJSONObject("_embedded");
            if (embedded != null) {
                embeddedProgramSet = embedded.optJSONObject("mt:programSet");
            }
            if (embeddedProgramSet == null) {
                continue;
            }

            JSONObject links = embeddedProgramSet.optJSONObject("_links");
            JSONObject self = links != null ? links.optJSONObject("self") : null;
            String href = self != null ? self.optString("href", null) : null;
            if (href == null) {
                continue;
            }
            href = href.replace("{?order,offset,limit}", "");
            String feedUrl = normalizeFeedUrl(href.startsWith("http") ? href : API_BASE_URL + href);

            JSONObject image = links != null ? links.optJSONObject("mt:squareImage") : null;
            if (image == null) {
                image = links != null ? links.optJSONObject("mt:image") : null;
            }
            String imageUrl = image != null ? image.optString("href", null) : null;
            if (imageUrl != null) {
                imageUrl = imageUrl.replace("{width}", "400");
                imageUrl = imageUrl.replace("{ratio}", "1x1");
            }

            String title = embeddedProgramSet.optString("title", "");
            items.add(new AudiothekItem(title, imageUrl, feedUrl));
        }
        return items;
    }

    private static String normalizeFeedUrl(String url) {
        if (url == null) {
            return null;
        }
        return url.replace("://api.ardaudiothek.de./", "://api.ardaudiothek.de/");
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
                return;
            }
            holder.cardView.setVisibility(View.VISIBLE);
            holder.actionButton.setVisibility(View.GONE);
            if (position >= items.size()) {
                holder.itemView.setAlpha(0.1f);
                Glide.with(activity).clear(holder.imageView);
                holder.imageView.setImageResource(R.color.medium_gray);
                holder.imageView.setOnClickListener(null);
                return;
            }

            holder.itemView.setAlpha(1.0f);
            AudiothekItem item = items.get(position);
            holder.imageView.setContentDescription(item.title);
            holder.titleLabel.setText(item.title);
            holder.titleLabel.setVisibility(View.VISIBLE);
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
            android.widget.TextView titleLabel;

            Holder(@NonNull View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.discovery_cover);
                imageView.setDirection(SquareImageView.DIRECTION_HEIGHT);
                actionButton = itemView.findViewById(R.id.actionButton);
                cardView = itemView.findViewById(R.id.cardView);
                titleLabel = itemView.findViewById(R.id.titleLabel);
            }
        }
    }
}
