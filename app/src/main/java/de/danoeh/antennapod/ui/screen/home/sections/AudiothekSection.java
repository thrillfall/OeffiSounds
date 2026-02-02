package de.danoeh.antennapod.ui.screen.home.sections;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.event.FeedListUpdateEvent;
import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import de.danoeh.antennapod.ui.appstartintent.OnlineFeedviewActivityStarter;
import de.danoeh.antennapod.ui.screen.home.HomeSection;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.Request;
import okhttp3.Response;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import de.danoeh.antennapod.ui.common.SquareImageView;

import java.lang.ref.WeakReference;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class AudiothekSection extends HomeSection {
    public static final String TAG = "AudiothekSection";

    private static final String AUDIOTHEK_HOME_URL = "https://api.ardaudiothek.de/homescreen";
    private static final int NUM_ITEMS = 8;
    private static final String API_BASE_URL = "https://api.ardaudiothek.de";

    private Disposable disposable;
    private AudiothekModulesAdapter listAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View view = super.onCreateView(inflater, container, savedInstanceState);

        listAdapter = new AudiothekModulesAdapter((MainActivity) requireActivity());
        listAdapter.setDummyModules(2, NUM_ITEMS);
        viewBinding.recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        viewBinding.recyclerView.setAdapter(listAdapter);
        viewBinding.recyclerView.setNestedScrollingEnabled(false);
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

    private static class AudiothekModule {
        public final String title;
        public final List<AudiothekItem> items;

        private AudiothekModule(String title, List<AudiothekItem> items) {
            this.title = title;
            this.items = items;
        }
    }

    private static class AudiothekModulesAdapter extends RecyclerView.Adapter<AudiothekModulesAdapter.ModuleHolder> {
        private final WeakReference<MainActivity> mainActivityRef;
        private final List<AudiothekModule> modules = new ArrayList<>();
        private int dummyModules = 0;
        private int dummyModuleItems = 0;
        private final RecyclerView.RecycledViewPool recycledViewPool = new RecyclerView.RecycledViewPool();

        AudiothekModulesAdapter(MainActivity activity) {
            this.mainActivityRef = new WeakReference<>(activity);
        }

        void setDummyModules(int dummyModules, int dummyModuleItems) {
            this.dummyModules = dummyModules;
            this.dummyModuleItems = dummyModuleItems;
            notifyDataSetChanged();
        }

        void updateData(List<AudiothekModule> newModules) {
            modules.clear();
            modules.addAll(newModules);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ModuleHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.audiothek_module_row, parent, false);
            return new ModuleHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ModuleHolder holder, int position) {
            MainActivity activity = mainActivityRef.get();
            if (activity == null) {
                return;
            }
            holder.recyclerView.setRecycledViewPool(recycledViewPool);

            final AudiothekHorizontalAdapter adapter;
            if (position >= modules.size()) {
                holder.titleView.setText("");
                adapter = new AudiothekHorizontalAdapter(activity);
                adapter.setDummyViews(dummyModuleItems);
                holder.recyclerView.setAdapter(adapter);
                return;
            }

            AudiothekModule module = modules.get(position);
            holder.titleView.setText(module.title);
            adapter = new AudiothekHorizontalAdapter(activity);
            adapter.setDummyViews(0);
            adapter.updateData(module.items);
            holder.recyclerView.setAdapter(adapter);
        }

        @Override
        public int getItemCount() {
            return modules.size() + dummyModules;
        }

        static class ModuleHolder extends RecyclerView.ViewHolder {
            final TextView titleView;
            final RecyclerView recyclerView;

            ModuleHolder(@NonNull View itemView) {
                super(itemView);
                titleView = itemView.findViewById(R.id.moduleTitle);
                recyclerView = itemView.findViewById(R.id.recyclerView);
                recyclerView.setLayoutManager(
                        new LinearLayoutManager(itemView.getContext(), RecyclerView.HORIZONTAL, false));
                int paddingHorizontal = (int) (12 * itemView.getResources().getDisplayMetrics().density);
                recyclerView.setPadding(paddingHorizontal, 0, paddingHorizontal, 0);
                recyclerView.setNestedScrollingEnabled(false);
                recyclerView.setItemAnimator(null);
            }
        }
    }

    @Override
    protected void handleMoreClick() {
    }

    @Override
    protected String getSectionTitle() {
        return getString(R.string.home_audiothek_title);
    }

    @Override
    protected String getMoreLinkTitle() {
        return "";
    }

    private void loadItems() {
        if (disposable != null) {
            disposable.dispose();
        }
        listAdapter.setDummyModules(2, NUM_ITEMS);

        disposable = Observable.fromCallable(() -> {
                    Request request = new Request.Builder().url(AUDIOTHEK_HOME_URL).build();
                    try (Response response = AntennapodHttpClient.getHttpClient().newCall(request).execute()) {
                        if (!response.isSuccessful()) {
                            throw new IOException("Unexpected response: " + response);
                        }
                        String body = response.body() != null ? response.body().string() : "";
                        return parseHomescreen(body);
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(modules -> {
                    listAdapter.setDummyModules(0, 0);
                    listAdapter.updateData(modules);
                    boolean isEmpty = modules.isEmpty();
                    viewBinding.recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
                    viewBinding.emptyLabel.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                }, error -> {
                    Log.e(TAG, Log.getStackTraceString(error));
                    listAdapter.setDummyModules(0, 0);
                    listAdapter.updateData(new ArrayList<>());
                    viewBinding.recyclerView.setVisibility(View.GONE);
                    viewBinding.emptyLabel.setVisibility(View.VISIBLE);
                });
    }

    private static List<AudiothekModule> parseHomescreen(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONObject embedded = root.optJSONObject("_embedded");
        if (embedded == null) {
            return new ArrayList<>();
        }

        List<AudiothekModule> modules = new ArrayList<>();
        AudiothekModule featured = parseFeaturedProgramSets(embedded);
        if (featured != null && !featured.items.isEmpty()) {
            modules.add(featured);
        }
        AudiothekModule mostPlayed = parseMostPlayed(embedded);
        if (mostPlayed != null && !mostPlayed.items.isEmpty()) {
            modules.add(mostPlayed);
        }
        return modules;
    }

    @Nullable
    private static AudiothekModule parseFeaturedProgramSets(JSONObject homescreenEmbedded) {
        JSONObject featured = homescreenEmbedded.optJSONObject("mt:featuredProgramSets");
        if (featured == null) {
            return null;
        }
        JSONObject featuredEmbedded = featured.optJSONObject("_embedded");
        if (featuredEmbedded == null) {
            return null;
        }
        JSONArray programSets = featuredEmbedded.optJSONArray("mt:programSets");
        if (programSets == null) {
            return null;
        }
        List<AudiothekItem> items = parseProgramSetsArray(programSets);
        return new AudiothekModule(getModuleTitle(featured), items);
    }

    @Nullable
    private static AudiothekModule parseMostPlayed(JSONObject homescreenEmbedded) {
        JSONObject mostPlayed = homescreenEmbedded.optJSONObject("mt:mostPlayed");
        if (mostPlayed == null) {
            return null;
        }
        JSONObject mostPlayedEmbedded = mostPlayed.optJSONObject("_embedded");
        if (mostPlayedEmbedded == null) {
            return null;
        }
        Object itemsObj = mostPlayedEmbedded.opt("mt:items");

        List<AudiothekItem> items = new ArrayList<>();
        if (itemsObj instanceof JSONObject) {
            items.addAll(parseItemsArray(new JSONArray().put(itemsObj)));
        } else if (itemsObj instanceof JSONArray) {
            items.addAll(parseItemsArray((JSONArray) itemsObj));
        }
        return new AudiothekModule(getModuleTitle(mostPlayed), items);
    }

    private static String getModuleTitle(JSONObject module) {
        JSONObject widget = module.optJSONObject("widget");
        if (widget != null) {
            String widgetTitle = widget.optString("widget_title", "");
            if (!widgetTitle.isEmpty()) {
                return widgetTitle;
            }
        }
        return module.optString("title", "");
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
        url = url.replace("://api.ardaudiothek.de./", "://api.ardaudiothek.de/");
        url = url.replace("{?order,offset,limit}", "");
        url = url.replace("{?offset,limit}", "?offset=0&limit=50");
        return url;
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
            CardView cardView;
            Button actionButton;

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
