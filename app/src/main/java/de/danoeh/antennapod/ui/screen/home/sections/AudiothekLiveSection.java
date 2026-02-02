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

public class AudiothekLiveSection extends HomeSection {
    public static final String TAG = "AudiothekLiveSection";

    private static final String AUDIOTHEK_HOME_URL = "https://api.ardaudiothek.de/homescreen";
    private static final int NUM_ITEMS = 8;
    private static final String API_BASE_URL = "https://api.ardaudiothek.de";

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
        return getString(R.string.home_audiothek_live_title);
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
                    Request request = new Request.Builder().url(AUDIOTHEK_HOME_URL).build();
                    try (Response response = AntennapodHttpClient.getHttpClient().newCall(request).execute()) {
                        if (!response.isSuccessful()) {
                            throw new IOException("Unexpected response: " + response);
                        }
                        String body = response.body() != null ? response.body().string() : "";
                        return parseLive(body);
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

    private static List<AudiothekItem> parseLive(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONObject embedded = root.optJSONObject("_embedded");
        if (embedded == null) {
            return new ArrayList<>();
        }
        JSONObject itemsModule = embedded.optJSONObject("mt:items");
        if (itemsModule == null) {
            return new ArrayList<>();
        }
        JSONObject moduleEmbedded = itemsModule.optJSONObject("_embedded");
        if (moduleEmbedded == null) {
            return new ArrayList<>();
        }

        Object itemsObj = moduleEmbedded.opt("mt:items");
        List<AudiothekItem> items = new ArrayList<>();
        if (itemsObj instanceof JSONObject) {
            items.addAll(parseMostPlayedItems(new JSONArray().put(itemsObj)));
        } else if (itemsObj instanceof JSONArray) {
            items.addAll(parseMostPlayedItems((JSONArray) itemsObj));
        }

        JSONArray programSets = moduleEmbedded.optJSONArray("mt:programSets");
        if (programSets != null) {
            items.addAll(parseProgramSetsArray(programSets));
        }

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

    private static List<AudiothekItem> parseMostPlayedItems(JSONArray itemsArray) {
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

            Holder(@NonNull View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.discovery_cover);
                imageView.setDirection(SquareImageView.DIRECTION_HEIGHT);
                actionButton = itemView.findViewById(R.id.actionButton);
                cardView = itemView.findViewById(R.id.cardView);
            }
        }
    }
}
